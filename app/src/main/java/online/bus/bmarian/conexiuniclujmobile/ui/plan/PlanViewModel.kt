package online.bus.bmarian.conexiuniclujmobile.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.Photon
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyPoint
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.simplifyPath
import online.bus.bmarian.conexiuniclujmobile.logic.PLAN_MAX_MINUTES
import online.bus.bmarian.conexiuniclujmobile.logic.PlanResponse
import online.bus.bmarian.conexiuniclujmobile.logic.PlannedTimeEntry
import online.bus.bmarian.conexiuniclujmobile.logic.Planner
import online.bus.bmarian.conexiuniclujmobile.logic.RichPlan
import online.bus.bmarian.conexiuniclujmobile.logic.StopLookup
import online.bus.bmarian.conexiuniclujmobile.logic.StoredLiveEta
import online.bus.bmarian.conexiuniclujmobile.logic.TimeMode
import online.bus.bmarian.conexiuniclujmobile.logic.VEHICLE_LOOKAHEAD_STOPS
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.clampCoord4
import online.bus.bmarian.conexiuniclujmobile.logic.findClosestShapeIdx
import online.bus.bmarian.conexiuniclujmobile.logic.formatLocalDateTime
import online.bus.bmarian.conexiuniclujmobile.logic.lastTransitCutoffString
import online.bus.bmarian.conexiuniclujmobile.logic.toFixed4
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight

class Origin(val name: String, val lat: Double, val lon: Double)

enum class SearchField { Origin, Destination }

class PlanMapLayer(
    val shapes: List<MapShape> = emptyList(),
    val walks: List<List<LatLon>> = emptyList(),
    val highlights: Map<Int, StopHighlight> = emptyMap(),
)

private const val LOCATING_TIMEOUT_MS = 15_000L

/** Port of RoutePlanningView's state and watchers. */
class PlanViewModel(app: Application, query: PlanQuery) : AndroidViewModel(app) {
    private fun string(id: Int) = getApplication<Application>().getString(id)

    val destLatText = MutableStateFlow(query.lat)
    val destLonText = MutableStateFlow(query.lon)
    val destName = MutableStateFlow(query.name.orEmpty())
    val customOrigin = MutableStateFlow(originFrom(query))
    val timeMode = MutableStateFlow(
        when {
            query.last == "true" -> TimeMode.Last
            query.time != null -> if (query.arriveBy == "true") TimeMode.Arrive else TimeMode.Leave
            else -> TimeMode.Now
        },
    )
    val timeValue = MutableStateFlow(query.time.orEmpty())
    private var planParam: Int? = query.plan?.toIntOrNull()

    val isCalculating = MutableStateFlow(false)
    val planData = MutableStateFlow<PlanResponse?>(null)
    val plans = MutableStateFlow<List<RichPlan>>(emptyList())
    val selectedIndex = MutableStateFlow(0)
    val selectedLiveEtaMin = MutableStateFlow<Double?>(null)
    val mapLayer = MutableStateFlow(PlanMapLayer())
    val mapVehicles = MutableStateFlow<List<MapVehicle>>(emptyList())
    val locatingTimedOut = MutableStateFlow(false)

    val activeSearch = MutableStateFlow(if (query.lat == null || query.lon == null) SearchField.Destination else null)
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<Place>>(emptyList())
    val isSearching = MutableStateFlow(false)

    private var currentQueryKey: String? = null
    private var liveEtaByKey: Map<String, List<StoredLiveEta>> = emptyMap()
    private val shapePoints = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())
    private val live = MutableStateFlow<LiveState?>(null)
    private var calculation: Job? = null
    private var dragGeneration = 0

    val destLat: Double get() = destLatText.value?.toDoubleOrNull() ?: Double.NaN
    val destLon: Double get() = destLonText.value?.toDoubleOrNull() ?: Double.NaN
    val hasValidCoords: Boolean get() = !destLat.isNaN() && !destLon.isNaN()

    private val stops = object : StopLookup {
        override fun name(id: Int) = stop(id)?.first
        override fun position(id: Int) = stop(id)?.second
    }

    /** Port of getStop. */
    fun stop(id: Int): Pair<String, Pair<Double, Double>>? {
        Services.repository.stops.value.find { it.stopId == id }?.let { return it.name to (it.lat to it.lon) }
        planData.value?.stops?.get("$id")?.let { return it.name to (it.lat to it.lon) }
        return null
    }

    fun stopName(id: Int): String = stop(id)?.first ?: if (id != 0) "Stop $id" else ""

    fun planner(): Planner = Planner(planData.value?.shapes.orEmpty(), stops)

    fun journeyFor(plan: RichPlan): Journey? {
        if (plan.legs.isEmpty() || !hasValidCoords) return null
        val routesById = Services.repository.routes.value.associateBy { it.routeId }
        fun at(id: Int): JourneyStop? = stop(id)?.let { (name, pos) -> JourneyStop(id, name, pos.first, pos.second) }
        val transfers = (plan.legs.size - 1).coerceAtLeast(1)
        val points = shapePoints.value
        fun pathOf(tripId: String?, startStopId: Int, destStopId: Int): List<JourneyPoint> {
            val pts = tripId?.let { points[it] } ?: return emptyList()
            val start = stop(startStopId)?.second ?: return emptyList()
            val dest = stop(destStopId)?.second ?: return emptyList()
            val a = findClosestShapeIdx(start.first, start.second, pts)
            val b = findClosestShapeIdx(dest.first, dest.second, pts)
            return simplifyPath(pts.subList(minOf(a, b), maxOf(a, b) + 1).map { LatLon(it.lat, it.lon) })
        }
        val legs = plan.legs.mapIndexed { i, leg ->
            val stops = (listOf(leg.startStopId) + leg.intermediateStopIds + leg.destStopId).mapNotNull(::at)
            if (stops.size < 2) return null
            JourneyLeg(
                routeIds = leg.routeIds,
                tripIds = leg.tripIds,
                lines = leg.routeIds.map { routesById[it]?.shortName ?: "$it" },
                colors = leg.routeIds.map { routesById[it]?.color ?: "#64748b" },
                stops = stops,
                walkMinutes = if (i == 0) plan.walkStartMeters / WALK_SPEED else plan.walkTransferMeters / WALK_SPEED / transfers,
                rideMinutes = leg.rideSeconds / 60,
                path = pathOf(leg.tripIds.firstOrNull(), leg.startStopId, leg.destStopId),
            )
        }
        val name = destName.value.ifEmpty { stopName(plan.legs.last().destStopId) }
        return Journey(
            id = JourneyManager.newId(),
            destination = JourneyStop(-1, name, destLat, destLon),
            legs = legs,
            planPath = "/plan?" + currentQuery().encode(),
            walkEndMinutes = plan.walkEndMeters / WALK_SPEED,
            walks = plan.walkSegments.map { seg -> simplifyPath(Planner.decodePolyline(seg.geometry).map { LatLon(it.first, it.second) }, 8.0) },
        )
    }

    init {
        if (hasValidCoords && Services.location.value == null && customOrigin.value == null) activeSearch.value = SearchField.Origin
        if (!hasValidCoords) activeSearch.value = SearchField.Destination
        calculateRoutes()
        viewModelScope.launch {
            var had = Services.location.value != null
            Services.location.collect { fix ->
                if (fix != null && !had && customOrigin.value == null) {
                    if (activeSearch.value == SearchField.Origin) closeSearch()
                    calculateRoutes()
                }
                had = had || fix != null
            }
        }
        viewModelScope.launch {
            delay(LOCATING_TIMEOUT_MS)
            locatingTimedOut.value = true
        }
        viewModelScope.launch { Services.clock.collect { applyTiming() } }
        viewModelScope.launch {
            plans.map { list -> list.flatMap { p -> p.legs.flatMap { l -> l.routeIds.zip(l.tripIds) } }.toSet() }
                .distinctUntilChanged()
                .collectLatest { loadShapes(it) }
        }
        viewModelScope.launch {
            combine(live, shapePoints) { l, p -> l to p }.collect { (l, p) -> onLive(l, p) }
        }
        viewModelScope.launch {
            combine(selectedIndex, plans.map { list -> list.map { it.key } }.distinctUntilChanged(), shapePoints) { _, _, _ -> }
                .collect { updateMapLayer() }
        }
        viewModelScope.launch {
            searchQuery.collect { q -> runSearch(q, 500) }
        }
    }

    private fun originFrom(query: PlanQuery): Origin? {
        val lat = query.originLat?.toDoubleOrNull() ?: return null
        val lon = query.originLon?.toDoubleOrNull() ?: return null
        val name = query.originName?.takeIf { it.isNotEmpty() } ?: return null
        return Origin(name, lat, lon)
    }

    fun currentQuery(): PlanQuery {
        val origin = customOrigin.value
        val mode = timeMode.value
        return PlanQuery(
            lat = destLatText.value,
            lon = destLonText.value,
            name = destName.value,
            originLat = origin?.let { toFixed4(it.lat) },
            originLon = origin?.let { toFixed4(it.lon) },
            originName = origin?.name,
            time = timeValue.value.takeIf { mode == TimeMode.Leave || mode == TimeMode.Arrive }?.takeIf { it.isNotEmpty() },
            arriveBy = when (mode) {
                TimeMode.Leave -> "false"
                TimeMode.Arrive -> "true"
                else -> null
            }.takeIf { timeValue.value.isNotEmpty() },
            last = if (mode == TimeMode.Last) "true" else null,
            plan = selectedIndex.value.takeIf { it > 0 }?.toString(),
        )
    }

    private fun originPoint(): Pair<Double, Double>? =
        customOrigin.value?.let { it.lat to it.lon } ?: Services.location.value?.let { it.lat to it.lon }

    private fun queryKey(): String? {
        val origin = originPoint() ?: return null
        if (!hasValidCoords) return null
        val tk = when (timeMode.value) {
            TimeMode.Now -> "now"
            TimeMode.Last -> "last:${lastTransitCutoffString(System.currentTimeMillis())}"
            else -> "${timeMode.value.key}:${timeValue.value}"
        }
        return "plan_routes?from_lat=${origin.first}&from_lng=${origin.second}&to_lat=$destLat&to_lng=$destLon&t=$tk"
    }

    private fun clearPlanSelection() {
        planParam = null
    }

    private fun resetResults() {
        plans.value = emptyList()
        planData.value = null
        currentQueryKey = null
        liveEtaByKey = emptyMap()
    }

    fun calculateRoutes(force: Boolean = false) {
        val origin = originPoint() ?: return
        if (!hasValidCoords) return
        val qk = queryKey()
        if (!force && qk != null && qk == currentQueryKey && planData.value?.plans?.isNotEmpty() == true) return
        calculation?.cancel()
        resetResults()
        val lat = destLat
        val lon = destLon
        calculation = viewModelScope.launch {
            isCalculating.value = true
            try {
                val params = StringBuilder("plan_routes?from_lat=${toFixed4(origin.first)}&from_lng=${toFixed4(origin.second)}&to_lat=${toFixed4(lat)}&to_lng=${toFixed4(lon)}")
                when {
                    timeMode.value == TimeMode.Last -> params.append("&time=${Api.encode(lastTransitCutoffString(System.currentTimeMillis()))}&arrive_by=true")
                    timeMode.value != TimeMode.Now && timeValue.value.isNotEmpty() ->
                        params.append("&time=${Api.encode(timeValue.value)}&arrive_by=${timeMode.value == TimeMode.Arrive}")
                }
                val data = Api.get<PlanResponse>(params.toString())
                setPlanData(data)
                currentQueryKey = qk
                if (data.plans.isNotEmpty()) {
                    fitRequests.value++
                    if (Services.online.value) {
                        val o = customOrigin.value
                        Services.favorites.addRecentPlan(
                            FavoritePlan(
                                name = destName.value.ifEmpty { string(R.string.w_plan_title_generic) },
                                lat = lat, lon = lon,
                                originName = o?.name, originLat = o?.lat, originLon = o?.lon,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                planData.value = null
            } finally {
                isCalculating.value = false
            }
        }
    }

    val fitRequests = MutableStateFlow(0)

    private suspend fun setPlanData(data: PlanResponse) {
        val nowMs = Services.clock.value
        val mode = timeMode.value
        val value = timeValue.value
        val built = withContext(Dispatchers.Default) { Planner(data.shapes, stops).buildPlans(data, nowMs, mode, value) }
        planData.value = data
        liveEtaByKey = emptyMap()
        val target = planParam?.takeIf { it >= 0 && it < built.size } ?: 0
        selectedIndex.value = target
        plans.value = built
        updateSelectedLive()
    }

    fun refreshRoutes() {
        clearPlanSelection()
        selectedIndex.value = 0
        resetResults()
        calculateRoutes(force = true)
    }

    fun selectPlanAt(idx: Int) {
        selectedIndex.value = idx
        planParam = idx.takeIf { it > 0 }
        fitRequests.value++
        mapVehicles.value = emptyList()
        updateSelectedLive()
        viewModelScope.launch { updateMapVehicles(live.value, shapePoints.value) }
    }

    fun setTimeMode(mode: TimeMode) {
        if (timeMode.value == mode) return
        timeMode.value = mode
        if (mode != TimeMode.Now) {
            liveEtaByKey = emptyMap()
            selectedLiveEtaMin.value = null
            mapVehicles.value = emptyList()
        }
        if (mode != TimeMode.Now && mode != TimeMode.Last && timeValue.value.isEmpty()) {
            timeValue.value = formatLocalDateTime(System.currentTimeMillis())
        }
        if (mode == TimeMode.Now || mode == TimeMode.Last) refreshRoutes()
    }

    fun setTimeValue(value: String) {
        val min = formatLocalDateTime(Services.clock.value)
        timeValue.value = if (value.isNotEmpty() && value < min) min else value
    }

    fun openSearch(field: SearchField) {
        if (field == SearchField.Destination && !Services.online.value) return
        activeSearch.value = field
        searchResults.value = emptyList()
        searchQuery.value = ""
    }

    fun closeSearch() {
        activeSearch.value = null
        searchQuery.value = ""
        searchResults.value = emptyList()
    }

    private suspend fun performSearch(query: String) {
        if (!Services.online.value && activeSearch.value == SearchField.Destination) {
            searchResults.value = emptyList()
            isSearching.value = false
            return
        }
        val q = query.trim()
        if (q.length < 3) {
            searchResults.value = emptyList()
            return
        }
        isSearching.value = true
        try {
            searchResults.value = Photon.search(q, Services.settings.locale.value, 5)
        } finally {
            isSearching.value = false
        }
    }

    private var searchJob: Job? = null

    private fun runSearch(query: String, waitMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(waitMs)
            performSearch(query)
        }
    }

    fun searchNow() = runSearch(searchQuery.value, 0)

    private fun setOrigin(origin: Origin?) {
        // 4 decimals, as the website's links keep it.
        customOrigin.value = origin?.let { Origin(it.name, clampCoord4(it.lat), clampCoord4(it.lon)) }
        clearPlanSelection()
        closeSearch()
        calculateRoutes()
    }

    fun selectOrigin(place: Place) {
        val lat = place.latitude ?: return
        val lon = place.longitude ?: return
        setOrigin(Origin(place.label, lat, lon))
    }

    fun useCurrentLocation() = setOrigin(null)

    private fun setDestination(lat: String, lon: String, name: String) {
        val changed = lat.toDoubleOrNull() != destLat || lon.toDoubleOrNull() != destLon
        val hadValid = hasValidCoords
        destLatText.value = lat
        destLonText.value = lon
        destName.value = name
        closeSearch()
        if (changed) {
            if (hadValid) clearPlanSelection()
            resetResults()
        }
        calculateRoutes()
    }

    fun selectDestination(place: Place) {
        if (!Services.online.value) return
        setDestination(place.lat, place.lon, place.label)
    }

    fun dropPinAsDestination() {
        if (!Services.online.value) return
        setDestination("46.7712", "23.6236", string(R.string.w_dropped_pin))
    }

    fun useCurrentLocationAsDestination() {
        if (!Services.online.value) return
        val fix = Services.location.value ?: return
        setDestination(fix.lat.toString(), fix.lon.toString(), string(R.string.w_plan_origin_current_location))
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String =
        runCatching { Photon.reverse(lat, lon, Services.settings.locale.value)?.label }.getOrNull()
            ?: "${toFixed4(lat)}, ${toFixed4(lon)}"

    fun onDestinationDragged(at: LatLon) {
        val gen = ++dragGeneration
        viewModelScope.launch {
            val name = reverseGeocode(at.lat, at.lon)
            if (gen != dragGeneration) return@launch
            setDestination(toFixed4(at.lat), toFixed4(at.lon), name)
        }
    }

    fun onOriginDragged(at: LatLon) {
        val gen = ++dragGeneration
        viewModelScope.launch {
            val name = reverseGeocode(at.lat, at.lon)
            if (gen != dragGeneration) return@launch
            setOrigin(Origin(name, at.lat, at.lon))
        }
    }

    fun swap() {
        if (!hasValidCoords) return
        val origin = customOrigin.value
        val newDestLat = origin?.lat ?: Services.location.value?.lat ?: return
        val newDestLon = origin?.lon ?: Services.location.value?.lon ?: return
        val oldLat = destLat
        val oldLon = destLon
        val oldName = destName.value
        viewModelScope.launch {
            val newDestName = origin?.name ?: reverseGeocode(newDestLat, newDestLon)
            val newOriginName = if (oldName == string(R.string.w_plan_origin_current_location)) reverseGeocode(oldLat, oldLon) else oldName
            customOrigin.value = Origin(newOriginName, clampCoord4(oldLat), clampCoord4(oldLon))
            clearPlanSelection()
            setDestination(PlanQuery.jsNumber(newDestLat), PlanQuery.jsNumber(newDestLon), newDestName)
        }
    }

    fun isFavorite(plans: List<FavoritePlan>): Boolean =
        plans.any { it.sameAs(destLat, destLon, customOrigin.value?.lat, customOrigin.value?.lon) }

    fun favoriteLabel(plans: List<FavoritePlan>): String? =
        plans.find { it.sameAs(destLat, destLon, customOrigin.value?.lat, customOrigin.value?.lon) }?.label

    fun toggleFavorite() {
        if (!Services.online.value || !hasValidCoords) return
        val o = customOrigin.value
        Services.favorites.togglePlan(
            FavoritePlan(
                name = destName.value.ifEmpty { string(R.string.w_plan_title_generic) },
                lat = destLat, lon = destLon,
                originName = o?.name, originLat = o?.lat, originLon = o?.lon,
            ),
        )
    }

    fun rename(label: String) {
        if (!Services.online.value) return
        Services.favorites.renamePlan(destLat, destLon, customOrigin.value?.lat, customOrigin.value?.lon, label.trim().ifEmpty { null })
    }

    private fun applyTiming() {
        val data = planData.value ?: return
        if (plans.value.isEmpty()) return
        plans.value = Planner(data.shapes, stops).applyTiming(plans.value, Services.clock.value, timeMode.value, timeValue.value, liveEtaByKey)
        updateSelectedLive()
    }

    private fun updateSelectedLive() {
        val plan = plans.value.getOrNull(selectedIndex.value)
        if (plan == null) {
            selectedLiveEtaMin.value = null
            return
        }
        val walkStartMin = plan.walkStartMeters / WALK_SPEED
        val live = planner().getDecayedLiveEntries(liveEtaByKey[plan.key], Services.clock.value)
        selectedLiveEtaMin.value = live.firstOrNull { it.minutes >= walkStartMin }?.minutes
    }

    private suspend fun loadShapes(trips: Set<Pair<Int, String>>) {
        if (trips.isEmpty()) {
            shapePoints.value = emptyMap()
            return
        }
        val shapes = planData.value?.shapes.orEmpty()
        val ids = trips.filter { shapes.containsKey("${it.first}") }.map { it.second }.distinct()
        if (ids.isEmpty()) {
            shapePoints.value = emptyMap()
            return
        }
        val loaded = runCatching { Services.repository.shapes(ids) }.getOrNull() ?: return
        shapePoints.value = loaded.filterValues { it.isNotEmpty() }
    }

    private class Subscription(val stopIds: List<Int>, val tripIds: List<String>)

    /** Every plan's first boarding stop for its rows, every leg's trips for the buses. */
    private fun subscription(): Subscription {
        if (timeMode.value != TimeMode.Now || plans.value.isEmpty()) return Subscription(emptyList(), emptyList())
        val stopIds = plans.value.mapNotNull { it.legs.firstOrNull()?.startStopId }.distinct()
        return Subscription(stopIds, plans.value.flatMap { p -> p.legs.flatMap { it.tripIds } }.distinct())
    }

    suspend fun live() {
        combine(plans, timeMode) { _, _ -> subscription() }
            .distinctUntilChanged { a, b -> a.stopIds == b.stopIds && a.tripIds == b.tripIds }
            .collectLatest { sub ->
                if (sub.tripIds.isEmpty()) {
                    live.value = null
                    return@collectLatest
                }
                LiveStream.live(sub.stopIds, sub.tripIds).collect { live.value = it }
            }
    }

    private suspend fun onLive(state: LiveState?, points: Map<String, List<ShapePoint>>) {
        if (timeMode.value != TimeMode.Now) return
        val data = planData.value ?: return
        if (state == null) {
            mapVehicles.value = emptyList()
            liveEtaByKey = emptyMap()
            selectedLiveEtaMin.value = null
            return
        }
        val nowMs = Services.clock.value
        val planner = Planner(data.shapes, stops)
        val newLive = withContext(Dispatchers.Default) {
            val result = HashMap<String, List<StoredLiveEta>>()
            for (plan in plans.value) {
                val leg = plan.legs.firstOrNull() ?: continue
                val entries = mutableListOf<StoredLiveEta>()
                for (i in leg.tripIds.indices) {
                    val tid = leg.tripIds[i]
                    val routeId = leg.routeIds.getOrNull(i) ?: continue
                    val rows = state.entriesAt(tid, leg.startStopId, nowMs) ?: continue
                    for (row in rows) {
                        if (!row.live || row.minutes > PLAN_MAX_MINUTES) continue
                        val entry = PlannedTimeEntry(row.minutes.toDouble(), true, routeId, tid)
                        if (planner.hasScheduledConnection(plan.plan, entry, nowMs)) entries += StoredLiveEta(entry, System.currentTimeMillis())
                    }
                }
                if (entries.isNotEmpty()) result[plan.key] = entries.sortedBy { it.entry.minutes }.take(3)
            }
            result
        }
        liveEtaByKey = newLive
        applyTiming()
        updateMapVehicles(state, points)
    }

    private fun lookaheadShapeIdx(stopTimes: List<online.bus.bmarian.conexiuniclujmobile.data.StopTime>, tripId: String, boardingStopId: Int, boardingIdx: Int, pts: List<ShapePoint>): Int {
        val tripStops = stopTimes.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
        val boardingPos = tripStops.indexOfFirst { it.stopId == boardingStopId }
        if (boardingPos <= 0) return boardingIdx
        val target = tripStops[maxOf(0, boardingPos - VEHICLE_LOOKAHEAD_STOPS)]
        if (target.lat == 0.0 || target.lon == 0.0) return boardingIdx
        return findClosestShapeIdx(target.lat, target.lon, pts)
    }

    private suspend fun updateMapVehicles(state: LiveState?, points: Map<String, List<ShapePoint>>) {
        val plan = plans.value.getOrNull(selectedIndex.value)
        val data = planData.value
        if (plan == null || data == null || state == null || points.isEmpty()) {
            mapVehicles.value = emptyList()
            return
        }
        mapVehicles.value = withContext(Dispatchers.Default) {
            val all = mutableListOf<MapVehicle>()
            for (leg in plan.legs) {
                for (i in leg.tripIds.indices) {
                    val tid = leg.tripIds[i]
                    val routeId = leg.routeIds.getOrNull(i) ?: continue
                    val shape = data.shapes["$routeId"] ?: continue
                    val pts = points[tid] ?: continue
                    val v = state.vehicles(tid)
                    if (v.isEmpty()) continue
                    val start = stop(leg.startStopId)?.second ?: continue
                    val dest = stop(leg.destStopId)?.second ?: continue
                    val startIdx = findClosestShapeIdx(start.first, start.second, pts)
                    val destIdx = findClosestShapeIdx(dest.first, dest.second, pts)
                    val rStart = minOf(startIdx, destIdx)
                    val rEnd = maxOf(startIdx, destIdx)
                    val lookahead = lookaheadShapeIdx(shape.stopTimes, tid, leg.startStopId, startIdx, pts)
                    val lower = maxOf(0, minOf(rStart, lookahead))
                    v.filter { findClosestShapeIdx(it.latitude, it.longitude, pts) in lower..rEnd + 1 }.forEach {
                        all += MapVehicle("${it.id}", it, shape.shortName, shape.color, it.heading)
                    }
                }
            }
            all.distinctBy { it.id }
        }
    }

    private fun updateMapLayer() {
        val plan = plans.value.getOrNull(selectedIndex.value)
        val data = planData.value
        if (plan == null || data == null) {
            mapLayer.value = PlanMapLayer()
            return
        }
        val points = shapePoints.value
        val shapes = plan.legs.mapNotNull { leg ->
            val tid = leg.tripIds.firstOrNull() ?: return@mapNotNull null
            val routeId = leg.routeIds.firstOrNull() ?: return@mapNotNull null
            val s = data.shapes["$routeId"] ?: return@mapNotNull null
            val pts = points[tid] ?: return@mapNotNull null
            val start = stop(leg.startStopId)?.second ?: return@mapNotNull null
            val dest = stop(leg.destStopId)?.second ?: return@mapNotNull null
            val a = findClosestShapeIdx(start.first, start.second, pts)
            val b = findClosestShapeIdx(dest.first, dest.second, pts)
            MapShape(tid, s.shortName, s.color, pts.subList(minOf(a, b), maxOf(a, b) + 1).map { LatLon(it.lat, it.lon) })
        }
        val walks = plan.walkSegments.map { seg -> Planner.decodePolyline(seg.geometry).map { LatLon(it.first, it.second) } }
        val highlights = LinkedHashMap<Int, StopHighlight>()
        plan.legs.forEachIndexed { idx, leg ->
            highlights[leg.startStopId] = if (idx == 0) StopHighlight.Green else StopHighlight.Purple
            leg.intermediateStopIds.forEach { highlights[it] = StopHighlight.Gray }
            if (idx == plan.legs.lastIndex) highlights[leg.destStopId] = StopHighlight.Red
            else if (leg.destStopId != plan.legs[idx + 1].startStopId) highlights[leg.destStopId] = StopHighlight.Amber
        }
        mapLayer.value = PlanMapLayer(shapes, walks, highlights)
    }
}
