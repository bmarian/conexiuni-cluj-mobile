package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDateTime
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
import online.bus.bmarian.conexiuniclujmobile.data.RecentGroup
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.boardingStop
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.stopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.tripOf
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle

/** [departure]: the line's next buses at [stopId], null once none is left today. */
class GroupRow(val line: GroupLine, val stopId: Int?, val departure: StopDeparture?)

class GroupUi(val rows: List<GroupRow>, val now: LocalDateTime)

class GroupMapLayer(val shapes: List<MapShape>, val vehicles: List<MapVehicle>)

private const val LINII = "linii"

class GroupViewModel(raw: String, private val state: SavedStateHandle) : ViewModel() {
    private val repository = Services.repository
    val index: StateFlow<NetworkIndex?> = repository.network.index

    /** Null until the network is in. */
    val lines: StateFlow<List<GroupLine>?> = combine(index, state.getStateFlow(LINII, raw)) { index, raw -> index?.readLines(raw) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _hidden = MutableStateFlow<Set<Int>>(emptySet())
    val hidden: StateFlow<Set<Int>> = _hidden
    private val shown = combine(lines, _hidden) { lines, hidden -> lines.orEmpty().filter { it.routeId !in hidden } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val boarding: StateFlow<List<Pair<GroupLine, Int?>>> = combine(index, lines, Services.location) { index, lines, fix ->
        if (index == null) emptyList() else lines.orEmpty().map { it to index.boardingStop(it, fix?.lat, fix?.lon) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val live = MutableStateFlow<LiveState?>(null)
    val beat: StateFlow<Long?> = live.map { it?.asOfMs }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())

    fun flip(routeId: Int) {
        val index = index.value ?: return
        val before = lines.value ?: return
        val after = before.map { if (it.routeId == routeId) it.copy(direction = if (it.direction == "0") "1" else "0") else it }
        Services.groups.replaceLines(before, after)
        Services.favorites.removeRecent(RecentGroup(before))
        state[LINII] = index.writeLines(after)
    }

    fun toggleHidden(routeId: Int) {
        _hidden.value = if (routeId in _hidden.value) _hidden.value - routeId else _hidden.value + routeId
    }

    val ui: StateFlow<GroupUi?> = combine(index, boarding, live, Services.clock) { index, boarding, live, _ ->
        if (index == null) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        val rows = boarding.map { (line, stopId) ->
            val info = stopId?.let { index.stopInfo(it, nowMs) }
            GroupRow(line, stopId, info?.let { stopDepartures(it, live, now, nowMs, tripOf(line)).firstOrNull() })
        }
        GroupUi(rows, now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mapLayer: StateFlow<GroupMapLayer?> = combine(index, shown, shapes, live) { index, shown, shapes, live ->
        if (index == null) return@combine null
        val drawn = shown.mapNotNull { line ->
            val route = index.route(line.routeId) ?: return@mapNotNull null
            val points = shapes[tripOf(line)].orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            MapShape(tripOf(line), route.shortName, route.color, points.map { LatLon(it.lat, it.lon) })
        }
        val buses = shown.flatMap { line ->
            val route = index.route(line.routeId) ?: return@flatMap emptyList()
            live?.vehicles(tripOf(line)).orEmpty().map { MapVehicle("${it.id}", it, route.shortName, route.color, it.heading, faded = it.atStand) }
        }
        GroupMapLayer(drawn, buses)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun live() = coroutineScope {
        // Every line streams, so hiding one doesn't restart the stream.
        val trips = lines.map { list -> list.orEmpty().map(::tripOf) }.distinctUntilChanged()
        launch {
            trips.collectLatest { ids -> if (ids.isNotEmpty()) shapes.value = runCatching { repository.shapes(ids) }.getOrDefault(emptyMap()) }
        }
        val stops = boarding.map { list -> list.mapNotNull { it.second }.distinct() }.distinctUntilChanged()
        combine(stops, trips, ::Pair).collectLatest { (stops, ids) ->
            if (stops.isNotEmpty() || ids.isNotEmpty()) LiveStream.live(stops, ids).collect { live.value = it }
        }
    }
}
