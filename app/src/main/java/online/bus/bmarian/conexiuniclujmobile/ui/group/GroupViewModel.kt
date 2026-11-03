package online.bus.bmarian.conexiuniclujmobile.ui.group

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle

/** [departures]: each line's next buses at its boarding stop, null once none is left today. */
class GroupUi(val departures: List<StopDeparture?>, val now: LocalDateTime)

class GroupMapLayer(val shapes: List<MapShape>, val vehicles: List<MapVehicle>)

class GroupViewModel(raw: String) : ViewModel() {
    private val repository = Services.repository
    val index: StateFlow<NetworkIndex?> = repository.network.index

    private val _lines = MutableStateFlow<List<GroupLine>>(emptyList())
    val lines: StateFlow<List<GroupLine>> = _lines
    private val _hidden = MutableStateFlow<Set<Int>>(emptySet())
    val hidden: StateFlow<Set<Int>> = _hidden
    private val shown = combine(_lines, _hidden) { lines, hidden -> lines.filter { it.routeId !in hidden } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val boards: StateFlow<List<Int?>> = combine(index, _lines, Services.location) { index, lines, fix ->
        if (index == null) emptyList() else lines.map { index.boardingStop(it, fix?.lat, fix?.lon) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val live = MutableStateFlow<LiveState?>(null)
    val beat: StateFlow<Long?> = live.map { it?.asOfMs }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())

    init {
        viewModelScope.launch { _lines.value = index.filterNotNull().first().readLines(raw) }
    }

    fun flip(routeId: Int) {
        val before = _lines.value
        val after = before.map { if (it.routeId == routeId) it.copy(direction = if (it.direction == "0") "1" else "0") else it }
        Services.groups.replaceLines(before, after)
        Services.favorites.removeRecent(RecentGroup(before))
        _lines.value = after
    }

    fun toggleHidden(routeId: Int) {
        _hidden.value = if (routeId in _hidden.value) _hidden.value - routeId else _hidden.value + routeId
    }

    val ui: StateFlow<GroupUi?> = combine(index, _lines, boards, live, Services.clock) { index, lines, boards, live, _ ->
        if (index == null) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        val departures = lines.mapIndexed { i, line ->
            val info = boards.getOrNull(i)?.let { index.stopInfo(it, nowMs) } ?: return@mapIndexed null
            stopDepartures(info, live, now, nowMs, tripOf(line)).firstOrNull()
        }
        GroupUi(departures, now)
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
        launch {
            _lines.map { list -> list.map(::tripOf) }.distinctUntilChanged().collectLatest { ids ->
                if (ids.isNotEmpty()) shapes.value = runCatching { repository.shapes(ids) }.getOrDefault(emptyMap())
            }
        }
        val stops = boards.map { it.filterNotNull().distinct() }.distinctUntilChanged()
        val trips = shown.map { list -> list.map(::tripOf) }.distinctUntilChanged()
        combine(stops, trips, ::Pair).collectLatest { (stops, ids) ->
            if (stops.isNotEmpty() || ids.isNotEmpty()) LiveStream.live(stops, ids).collect { live.value = it }
        }
    }
}
