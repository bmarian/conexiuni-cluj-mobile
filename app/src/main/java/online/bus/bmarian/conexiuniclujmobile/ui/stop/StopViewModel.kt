package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.routeIdFromTripId
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.NaturalOrder
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.hasTimetableEntries
import online.bus.bmarian.conexiuniclujmobile.logic.stationDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.trackableTripIds
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import java.time.Instant
import java.time.LocalDateTime

class StopUi(val departures: List<StopDeparture>, val now: LocalDateTime)

class StopMapLayer(val shapes: List<MapShape>, val vehicles: List<MapVehicle>)

class StopViewModel(val stopId: Int) : ViewModel() {
    private val repository = Services.repository

    val name = MutableStateFlow(repository.stops.value.find { it.stopId == stopId }?.name)
    val failed = MutableStateFlow(false)
    val loading = MutableStateFlow(true)

    private val _info = MutableStateFlow<StopInfo?>(null)
    val info: StateFlow<StopInfo?> = _info
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())
    private val live = MutableStateFlow<LiveState?>(null)

    /** The stop and the other platforms of its station. */
    val infos: StateFlow<List<StopInfo>> = combine(_info, repository.network.index) { info, index ->
        if (info == null) return@combine emptyList()
        val nowMs = System.currentTimeMillis()
        listOf(info) + index?.platforms(stopId)?.drop(1)?.mapNotNull { index.stopInfo(it.stopId, nowMs) }.orEmpty()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ui: StateFlow<StopUi?> = combine(infos, live, Services.clock) { infos, live, _ ->
        if (infos.isEmpty()) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        StopUi(stationDepartures(infos, live, now, nowMs), now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mapLayer: StateFlow<StopMapLayer> = combine(ui, shapes, live, Services.favorites.routes) { ui, shapes, live, _ ->
        if (ui == null || live == null) return@combine StopMapLayer(emptyList(), emptyList())
        val lines = mutableListOf<MapShape>()
        val buses = mutableListOf<MapVehicle>()
        for (departure in ui.departures.filter(::isDepartureFavorite)) {
            val shape = shapes[departure.tripId].orEmpty()
            val vehicles = live.vehicles(departure.tripId)
            if (shape.isEmpty() || vehicles.isEmpty()) continue
            lines += MapShape(departure.tripId, departure.shortName, departure.color, shape.map { LatLon(it.lat, it.lon) })
            vehicles.mapTo(buses) { MapVehicle("${it.id}", it, departure.shortName, departure.color, it.heading) }
        }
        StopMapLayer(lines, buses)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, StopMapLayer(emptyList(), emptyList()))

    val routesHere: StateFlow<List<ShapeInfo>> = combine(infos, Services.favorites.routes) { infos, _ ->
        infos.flatMap { it.shapesInfo }.distinctBy { it.routeId }.filter { hasTimetableEntries(it.timetable) }.sortedWith(
            compareBy<ShapeInfo> { if (Services.favorites.isRouteFavorite(it.routeId)) 0 else 1 }
                .thenComparator { a, b -> NaturalOrder.compare(a.shortName, b.shortName) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Buses are drawn for favourite lines only, so only their trips are subscribed.
    private val favoriteTripIds = combine(infos, Services.favorites.routes) { infos, _ ->
        infos.flatMap(::trackableTripIds).distinct().filter { tripId -> routeIdFromTripId(tripId)?.let(Services.favorites::isRouteFavorite) == true }
    }.distinctUntilChanged()

    init {
        load()
    }

    fun load() {
        failed.value = false
        viewModelScope.launch {
            if (_info.value == null) repository.cachedStopInfo(stopId)?.let { show(it) }
            try {
                show(repository.stopInfo(stopId))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (_info.value == null) failed.value = true
            } finally {
                loading.value = false
            }
        }
    }

    private fun show(loaded: StopInfo) {
        name.value = loaded.name
        _info.value = loaded
        loading.value = false
    }

    suspend fun live() = coroutineScope {
        launch {
            favoriteTripIds.collectLatest { ids ->
                shapes.value = runCatching { repository.shapes(ids) }.getOrDefault(emptyMap())
            }
        }
        combine(infos.map { list -> list.map { it.stopId }.ifEmpty { listOf(stopId) } }.distinctUntilChanged(), favoriteTripIds) { a, b -> a to b }
            .collectLatest { (stopIds, ids) -> LiveStream.live(stopIds, ids).collect { live.value = it } }
    }

    fun isDepartureFavorite(departure: StopDeparture) =
        Services.favorites.isRouteFavorite(departure.routeId)

    /** The platform where a line calls, the stop itself first. */
    fun platformOf(routeId: Int): StopInfo? = infos.value.firstOrNull { info -> info.shapesInfo.any { it.routeId == routeId } }

    fun platform(stopId: Int): StopInfo? = infos.value.firstOrNull { it.stopId == stopId }
}
