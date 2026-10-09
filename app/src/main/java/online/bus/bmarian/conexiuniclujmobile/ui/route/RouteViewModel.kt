package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.data.VehicleStream
import online.bus.bmarian.conexiuniclujmobile.data.tripIdFor
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.DirectionShape
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedStop
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedVehicle
import online.bus.bmarian.conexiuniclujmobile.logic.MarkedArrival
import online.bus.bmarian.conexiuniclujmobile.logic.SCHEDULE_HORIZON_MIN
import online.bus.bmarian.conexiuniclujmobile.logic.daySchedule
import online.bus.bmarian.conexiuniclujmobile.logic.departureMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.directionShape
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.logic.indexVehicles
import online.bus.bmarian.conexiuniclujmobile.logic.mergeArrivals
import online.bus.bmarian.conexiuniclujmobile.logic.minutesOfDay
import online.bus.bmarian.conexiuniclujmobile.logic.routeStopArrivals
import online.bus.bmarian.conexiuniclujmobile.logic.scheduledArrivals
import online.bus.bmarian.conexiuniclujmobile.logic.stopsForDirection
import java.time.Instant
import java.time.LocalDateTime

object RouteHandoff {
    var shapeInfo: ShapeInfo? = null
        private set
    var tripId: String? = null
        private set
    var fromStopId: Int? = null
        private set
    var fromStopName: String? = null
        private set

    fun set(info: ShapeInfo, tripId: String, stopId: Int?, stopName: String?) {
        shapeInfo = info
        this.tripId = tripId
        fromStopId = stopId
        fromStopName = stopName
    }
}

class RouteUi(
    val stops: List<IndexedStop>,
    val arrivals: List<List<MarkedArrival>>,
    val headerTimes: List<Int>,
    val shape: DirectionShape?,
    val vehicles: List<IndexedVehicle>,
    val nearestStopIdx: Int,
    val now: LocalDateTime,
)

/** Port of useRouteArrivals. */
class RouteViewModel(val routeId: Int, initialDirection: String, val fromStopId: Int?, changeId: Long?) : ViewModel() {
    private val repository = Services.repository

    val shapeInfo = MutableStateFlow(RouteHandoff.shapeInfo?.takeIf { it.routeId == routeId })
    val fromStopName = MutableStateFlow(
        if (fromStopId == null) null
        else RouteHandoff.fromStopName?.takeIf { RouteHandoff.fromStopId == fromStopId }
            ?: repository.stops.value.find { it.stopId == fromStopId }?.name,
    )
    val failed = MutableStateFlow(false)
    val initialLoading = MutableStateFlow(shapeInfo.value == null)
    private val wantsAuto = initialDirection == "auto"
    val direction = MutableStateFlow(resolveDirection(initialDirection))

    private val shapes = MutableStateFlow<Map<String, DirectionShape>>(emptyMap())
    private val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())

    val activeChange = MutableStateFlow<RouteChange?>(null)

    val hourlyOffsets = MutableStateFlow<Map<String, HourlyStopOffsets>>(emptyMap())

    /** 'auto' picks the direction with the stop nearest the user. */
    private fun resolveDirection(requested: String): String {
        if (requested == "1") return "1"
        if (requested != "auto") return "0"
        val loc = Services.location.value ?: return "0"
        val info = shapeInfo.value ?: return "0"
        val outId = tripIdFor(routeId, "0")
        val inId = tripIdFor(routeId, "1")
        var bestDir = "0"
        var bestDist = Double.POSITIVE_INFINITY
        for (st in info.stopTimes) {
            val dir = when (st.tripId) {
                outId -> "0"
                inId -> "1"
                else -> null
            }
            if (dir == null || st.lat == 0.0 || st.lon == 0.0) continue
            val d = haversineMeters(loc.lat, loc.lon, st.lat, st.lon)
            if (d < bestDist) {
                bestDist = d
                bestDir = dir
            }
        }
        return bestDir
    }

    val ui: StateFlow<RouteUi?> = combine(
        shapeInfo, direction, shapes, vehicles, Services.clock, Services.location,
    ) { values ->
        val info = values[0] as ShapeInfo? ?: return@combine null
        val dir = values[1] as String
        @Suppress("UNCHECKED_CAST") val shapes = values[2] as Map<String, DirectionShape>
        @Suppress("UNCHECKED_CAST") val vehicles = values[3] as Map<String, List<Vehicle>>
        val loc = values[5] as online.bus.bmarian.conexiuniclujmobile.data.Fix?
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        val tripId = tripIdFor(routeId, dir)
        val stops = stopsForDirection(info, tripId)
        val shape = shapes[dir]
        val indexed = if (shape == null) emptyList()
        else indexVehicles(info.shortName, info.color, shape.index, nowMs, vehicles[tripId].orEmpty())
        val departures = departureMinutes(daySchedule(info.timetable, now), dir == "0")
        // Terminus departures come from the timetable only.
        val firstOffset = stops.firstOrNull()?.offsetFromStart ?: 0
        val header = mergeArrivals(null, scheduledArrivals(departures, firstOffset, minutesOfDay(now), SCHEDULE_HORIZON_MIN))
        var nearest = -1
        if (loc != null) {
            var best = Double.POSITIVE_INFINITY
            stops.forEachIndexed { idx, stop ->
                if (stop.stop.lat == 0.0 || stop.stop.lon == 0.0) return@forEachIndexed
                val d = haversineMeters(loc.lat, loc.lon, stop.stop.lat, stop.stop.lon)
                if (d < best) {
                    best = d
                    nearest = idx
                }
            }
        }
        RouteUi(
            stops = stops,
            arrivals = routeStopArrivals(info, stops, dir == "0", shape, indexed, now, nowMs),
            headerTimes = header.map { it.minutes },
            shape = shape,
            vehicles = indexed,
            nearestStopIdx = nearest,
            now = now,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
        if (changeId != null) {
            viewModelScope.launch {
                val change = Services.routeUpdates.loadChange(changeId)
                if (change != null) {
                    activeChange.value = change
                    Services.routeUpdates.markSeen(listOf(change.id))
                }
            }
        }
    }

    fun load() {
        failed.value = false
        viewModelScope.launch {
            try {
                if (shapeInfo.value == null) {
                    initialLoading.value = true
                    val route = repository.route(routeId) ?: throw IllegalStateException("unknown route")
                    repository.cachedShapeInfo(route)?.let { shapeInfo.value = it }
                    val fresh = try {
                        repository.shapeInfo(route)
                    } catch (e: Exception) {
                        if (e is CancellationException || shapeInfo.value == null) throw e
                        null
                    }
                    if (fresh != null) shapeInfo.value = fresh
                    if (wantsAuto) direction.value = resolveDirection("auto")
                }
                initialLoading.value = false
                loadShapes(shapeInfo.value!!)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (shapeInfo.value == null) failed.value = true
                initialLoading.value = false
            }
        }
    }

    private suspend fun loadShapes(info: ShapeInfo) {
        val ids = listOf("0", "1").map { it to tripIdFor(routeId, it) }
        val loaded = runCatching { repository.shapes(ids.map { it.second }) }.getOrDefault(emptyMap())
        shapes.value = ids.mapNotNull { (dir, tripId) ->
            directionShape(info, tripId, loaded[tripId].orEmpty())?.let { dir to it }
        }.toMap()
    }

    fun hasDirection(dir: String): Boolean =
        shapeInfo.value?.stopTimes?.any { it.tripId == tripIdFor(routeId, dir) } == true

    fun terminal(dir: String): String {
        val info = shapeInfo.value ?: return ""
        val last = info.stopTimes.filter { it.tripId == tripIdFor(routeId, dir) }.maxByOrNull { it.stopSequence }
        return last?.headsign?.trim().orEmpty().ifEmpty {
            if (dir == "0") info.timetable.outStopName else info.timetable.inStopName
        }
    }

    private var hourlyJob: Job? = null
    private var hourlyKey: String? = null

    /** Ignore answers for a tab no longer shown. */
    fun loadHourly(shortName: String, dayType: String) {
        val key = "$shortName|$dayType"
        hourlyJob?.cancel()
        if (key != hourlyKey) {
            hourlyKey = key
            hourlyOffsets.value = emptyMap()
        }
        hourlyJob = viewModelScope.launch {
            hourlyOffsets.value = Services.repository.hourlyOffsets(shortName, dayType)
        }
    }

    suspend fun live() {
        shapes.collectLatest { loaded ->
            val ids = loaded.keys.map { tripIdFor(routeId, it) }
            if (ids.isEmpty()) return@collectLatest
            VehicleStream.vehicles(ids).collect { vehicles.value = it }
        }
    }
}
