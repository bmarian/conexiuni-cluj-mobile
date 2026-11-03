package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.liveRowKey
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.DEPARTURES_SHOWN
import online.bus.bmarian.conexiuniclujmobile.logic.Departure
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.Leg
import online.bus.bmarian.conexiuniclujmobile.logic.LegLine
import online.bus.bmarian.conexiuniclujmobile.logic.LegTimes
import online.bus.bmarian.conexiuniclujmobile.logic.SCHEDULE_HORIZON_MIN
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.logic.legsNear
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.scheduledAtStop
import online.bus.bmarian.conexiuniclujmobile.logic.stopsOf
import online.bus.bmarian.conexiuniclujmobile.logic.timeLegs
import online.bus.bmarian.conexiuniclujmobile.logic.tripOf
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight

class GroupUi(val times: List<LegTimes>, val now: LocalDateTime, val nowMs: Long)

class GroupMapLayer(val shapes: List<MapShape>, val vehicles: List<MapVehicle>, val stops: Map<Int, StopHighlight>)

private fun Leg.stopIds() = (lines + alight.orEmpty()).map { it.stopId }

class GroupViewModel(raw: String) : ViewModel() {
    private val repository = Services.repository
    val index: StateFlow<NetworkIndex?> = repository.network.index

    val lines: StateFlow<List<GroupLine>> = index.map { it?.readLines(raw).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val legs: StateFlow<List<Leg>> = combine(index, lines, Services.location) { index, lines, fix ->
        index?.legsNear(lines, fix?.lat, fix?.lon).orEmpty()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val live = MutableStateFlow<LiveState?>(null)
    val beat: StateFlow<Long?> = live.map { it?.asOfMs }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())

    private fun walkMs(index: NetworkIndex, from: Int, to: Int): Long {
        val a = index.stop(from) ?: return 0
        val b = index.stop(to) ?: return 0
        return if (from == to) 0 else (haversineMeters(a.lat, a.lon, b.lat, b.lon) / WALK_SPEED * 60_000).roundToLong()
    }

    val ui: StateFlow<GroupUi?> = combine(index, legs, live, Services.clock) { index, legs, live, _ ->
        if (index == null) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        val infos = HashMap<Int, StopInfo?>()
        fun busesAt(line: LegLine): List<Departure> {
            val tripId = tripOf(line.line)
            live?.rows?.get(liveRowKey(tripId, line.stopId))?.let { row ->
                return row.entries.map { Departure(line.routeId, it.at * 1000, it.live, it.vehicleId.takeIf { id -> id != 0 }) }
            }
            val info = infos.getOrPut(line.stopId) { index.stopInfo(line.stopId, nowMs) } ?: return emptyList()
            return scheduledAtStop(info, line.routeId, tripId, now).filter { it < SCHEDULE_HORIZON_MIN }
                .map { Departure(line.routeId, nowMs + (it * 60_000).roundToLong(), false) }
        }
        GroupUi(timeLegs(legs, ::busesAt, ::busesAt, { a, b -> walkMs(index, a, b) }, DEPARTURES_SHOWN), now, nowMs)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mapLayer: StateFlow<GroupMapLayer?> = combine(index, legs, shapes, live) { index, legs, shapes, live ->
        if (index == null) return@combine null
        val lines = lines.value
        val drawn = lines.mapNotNull { line ->
            val route = index.route(line.routeId) ?: return@mapNotNull null
            val points = shapes[tripOf(line)].orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            MapShape(tripOf(line), route.shortName, route.color, points.map { LatLon(it.lat, it.lon) })
        }
        val buses = lines.flatMap { line ->
            val route = index.route(line.routeId) ?: return@flatMap emptyList()
            live?.vehicles(tripOf(line)).orEmpty().map { MapVehicle("${it.id}", it, route.shortName, route.color, it.heading, faded = it.atStand) }
        }
        val marked = legs.flatMap { it.stopIds() }.toSet()
        val stops = LinkedHashMap<Int, StopHighlight>()
        for (line in lines) for (id in index.stopsOf(line)) stops.putIfAbsent(id, if (id in marked) StopHighlight.Green else StopHighlight.Gray)
        GroupMapLayer(drawn, buses, stops)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun live() = coroutineScope {
        val trips = lines.map { list -> list.map(::tripOf) }.distinctUntilChanged()
        launch { trips.collectLatest { ids -> if (ids.isNotEmpty()) shapes.value = runCatching { repository.shapes(ids) }.getOrDefault(emptyMap()) } }
        combine(legs.map { legs -> legs.flatMap { it.stopIds() }.distinct() }.distinctUntilChanged(), trips) { a, b -> a to b }
            .collectLatest { (stops, ids) -> if (stops.isNotEmpty()) LiveStream.live(stops, ids).collect { live.value = it } }
    }
}
