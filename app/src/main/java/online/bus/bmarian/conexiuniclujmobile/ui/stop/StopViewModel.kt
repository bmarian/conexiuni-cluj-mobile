package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.data.VehicleStream
import online.bus.bmarian.conexiuniclujmobile.data.directionFromTripId
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.NaturalOrder
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.buildShapeIndex
import online.bus.bmarian.conexiuniclujmobile.logic.computeStopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.hasTimetableEntries
import online.bus.bmarian.conexiuniclujmobile.logic.indexVehicles
import online.bus.bmarian.conexiuniclujmobile.logic.trackableTripIds
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import java.time.Instant
import java.time.LocalDateTime

class StopUi(val departures: List<StopDeparture>, val now: LocalDateTime)

class StopMapLayer(val shapes: List<MapShape>, val vehicles: List<MapVehicle>)

/** Port of useStopDepartures. */
class StopViewModel(val stopId: Int) : ViewModel() {
    private val repository = Services.repository

    val name = MutableStateFlow(repository.stops.value.find { it.stopId == stopId }?.name)
    val failed = MutableStateFlow(false)
    val loading = MutableStateFlow(true)

    private val _info = MutableStateFlow<StopInfo?>(null)
    val info: StateFlow<StopInfo?> = _info
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>?>(null)
    private val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())

    val ui: StateFlow<StopUi?> = combine(_info, shapes, vehicles, Services.clock) { info, shapes, vehicles, _ ->
        if (info == null || shapes == null) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        StopUi(computeStopDepartures(info, shapes, vehicles, now, nowMs), now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mapLayer: StateFlow<StopMapLayer> = combine(ui, shapes, vehicles, Services.favorites.routes) { ui, shapes, vehicles, _ ->
        if (ui == null) return@combine StopMapLayer(emptyList(), emptyList())
        val nowMs = System.currentTimeMillis()
        val lines = mutableListOf<MapShape>()
        val buses = mutableListOf<MapVehicle>()
        for (departure in ui.departures.filter(::isDepartureFavorite)) {
            val shape = shapes?.get(departure.tripId).orEmpty()
            if (shape.isEmpty()) continue
            val tracked = indexVehicles(departure.shortName, departure.color, buildShapeIndex(shape), nowMs, vehicles[departure.tripId].orEmpty())
            if (tracked.isEmpty()) continue
            lines += MapShape(departure.tripId, departure.shortName, departure.color, shape.map { LatLon(it.lat, it.lon) })
            tracked.mapTo(buses) { MapVehicle("${it.vehicle.id}", it.vehicle, departure.shortName, departure.color, it.heading) }
        }
        StopMapLayer(lines, buses)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, StopMapLayer(emptyList(), emptyList()))

    val routesHere: StateFlow<List<ShapeInfo>> = combine(_info, Services.favorites.routes) { info, _ ->
        info?.shapesInfo.orEmpty().filter { hasTimetableEntries(it.timetable) }.sortedWith(
            compareBy<ShapeInfo> { if (Services.favorites.isRouteFavorite(it.routeId)) 0 else 1 }
                .thenComparator { a, b -> NaturalOrder.compare(a.shortName, b.shortName) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    private suspend fun show(loaded: StopInfo) {
        name.value = loaded.name
        val loadedShapes = runCatching { repository.shapes(trackableTripIds(loaded)) }.getOrDefault(shapes.value ?: emptyMap())
        _info.value = loaded
        shapes.value = loadedShapes
        loading.value = false
    }

    suspend fun live() {
        _info.filterNotNull().map { trackableTripIds(it) }.distinctUntilChanged().collectLatest { ids ->
            VehicleStream.vehicles(ids).collect { vehicles.value = it }
        }
    }

    fun isDepartureFavorite(departure: StopDeparture) =
        Services.favorites.isRouteFavorite(departure.routeId, directionFromTripId(departure.tripId))
}
