package online.bus.bmarian.conexiuniclujmobile.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.Places
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.Reminder
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.PlannedRun
import online.bus.bmarian.conexiuniclujmobile.journey.simplifyPath
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.PlanOption
import online.bus.bmarian.conexiuniclujmobile.logic.PlanResponse
import online.bus.bmarian.conexiuniclujmobile.logic.TimeMode
import online.bus.bmarian.conexiuniclujmobile.logic.clampCoord4
import online.bus.bmarian.conexiuniclujmobile.logic.dayTypeOf
import online.bus.bmarian.conexiuniclujmobile.logic.decodePolyline
import online.bus.bmarian.conexiuniclujmobile.logic.formatLocalDateTime
import online.bus.bmarian.conexiuniclujmobile.logic.planQuery
import online.bus.bmarian.conexiuniclujmobile.logic.serviceSlot
import online.bus.bmarian.conexiuniclujmobile.logic.toFixed4
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.theme.manyLines
import java.util.UUID

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
    val plans = MutableStateFlow<List<PlanOption>>(emptyList())
    val selectedIndex = MutableStateFlow(0)
    val mapLayer = MutableStateFlow(PlanMapLayer())
    val mapVehicles = MutableStateFlow<List<MapVehicle>>(emptyList())
    val locatingTimedOut = MutableStateFlow(false)

    val activeSearch = MutableStateFlow(if (query.lat == null || query.lon == null) SearchField.Destination else null)
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<Place>>(emptyList())
    val isSearching = MutableStateFlow(false)

    private var currentQueryKey: String? = null
    private var selectedKey: String? = null
    private val live = MutableStateFlow<LiveState?>(null)
    private var calculation: Job? = null
    private var refresh: Job? = null
    private var dragGeneration = 0

    val destLat: Double get() = destLatText.value?.toDoubleOrNull() ?: Double.NaN
    val destLon: Double get() = destLonText.value?.toDoubleOrNull() ?: Double.NaN
    val hasValidCoords: Boolean get() = !destLat.isNaN() && !destLon.isNaN()

    /** Port of getStop. */
    fun stop(id: Int): Pair<String, Pair<Double, Double>>? {
        planData.value?.stops?.get("$id")?.let { return it.name to (it.lat to it.lon) }
        Services.repository.stops.value.find { it.stopId == id }?.let { return it.name to (it.lat to it.lon) }
        return null
    }

    fun stopName(id: Int): String = stop(id)?.first ?: if (id != 0) "Stop $id" else ""

    fun line(id: Int?) = id?.let { planData.value?.lines?.get("$it") }
    fun linesApart(plan: PlanOption) = manyLines(plan.rides.map { line(it.lines.firstOrNull())?.shortName.orEmpty() })

    fun journeyFor(plan: PlanOption): Journey? {
        val rides = plan.rides
        if (rides.isEmpty() || !hasValidCoords) return null
        fun at(id: Int): JourneyStop? = stop(id)?.let { (name, pos) -> JourneyStop(id, name, pos.first, pos.second) }
        fun points(path: String) = decodePolyline(path).map { LatLon(it.first, it.second) }
        val legs = mutableListOf<JourneyLeg>()
        var walkSeconds = 0L
        for (leg in plan.legs) {
            if (!leg.isRide) {
                walkSeconds += leg.seconds
                continue
            }
            val stops = (listOf(leg.from) + leg.passed + leg.to).mapNotNull(::at)
            if (stops.size < 2) return null
            val trips = plan.legTrips(legs.size)
            legs += JourneyLeg(
                routeIds = leg.lines,
                tripIds = leg.lines.map { trips[it].orEmpty() },
                lines = leg.lines.map { line(it)?.shortName ?: "$it" },
                colors = leg.lines.map { line(it)?.color ?: "#64748b" },
                stops = stops,
                walkMinutes = walkSeconds / 60.0,
                rideMinutes = leg.seconds / 60.0,
                path = simplifyPath(points(leg.path)),
            )
            walkSeconds = 0
        }
        val name = destName.value.ifEmpty { stopName(rides.last().to) }
        return Journey(
            id = JourneyManager.newId(),
            destination = JourneyStop(-1, name, destLat, destLon),
            legs = legs,
            planPath = "/plan?" + currentQuery().encode(),
            walkEndMinutes = walkSeconds / 60.0,
            walks = plan.legs.filter { !it.isRide }.map { simplifyPath(points(it.path), 8.0) },
            runs = plan.runs.map { run -> PlannedRun(run.arrive, run.rides.map { it.depart }) },
            city = Cities.current.value,
        )
    }

    /** A one-off reminder timed on the plan's first bus; the whole trip is guided from there. */
    fun reminderFor(plan: PlanOption): Reminder? {
        val journey = journeyFor(plan) ?: return null
        val leg = plan.rides.firstOrNull() ?: return null
        val ride = plan.firstRide ?: return null
        val (stopName, at) = stop(leg.from) ?: return null
        val line = line(ride.routeId) ?: return null
        val (date, minute) = serviceSlot(ride.depart * 1000, CLUJ_ZONE)
        return Reminder(
            id = UUID.randomUUID().toString(),
            followed = Followed(leg.from, stopName, ride.routeId, ride.tripId, line.shortName, line.color, journey.destination.name, Cities.current.value),
            lat = at.first,
            lon = at.second,
            time = minute,
            date = date.toString(),
            slots = mapOf(dayTypeOf(date) to minute),
            journey = journey,
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
        viewModelScope.launch {
            combine(live, selectedIndex, plans) { l, _, _ -> l }.collect { updateMapVehicles(it) }
        }
        viewModelScope.launch {
            combine(selectedIndex, plans) { _, _ -> }.collect { updateMapLayer() }
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
        return planQuery(origin, destLat to destLon, timeMode.value, timeValue.value, Api::encode)
    }

    private fun clearPlanSelection() {
        planParam = null
    }

    private fun resetResults() {
        refresh?.cancel()
        plans.value = emptyList()
        planData.value = null
        currentQueryKey = null
    }

    /** A silent call only refreshes the timings of what is on screen. */
    fun calculateRoutes(force: Boolean = false, silent: Boolean = false) {
        if (originPoint() == null || !hasValidCoords) return
        val qk = queryKey() ?: return
        if (!force && !silent && qk == currentQueryKey && planData.value?.options?.isNotEmpty() == true) return
        calculation?.cancel()
        if (!silent) resetResults()
        val lat = destLat
        val lon = destLon
        calculation = viewModelScope.launch {
            if (!silent) isCalculating.value = true
            try {
                val data = Api.get<PlanResponse>(qk)
                setPlanData(data)
                currentQueryKey = qk
                if (!silent && data.options.isNotEmpty()) {
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
                if (!silent) planData.value = null
            } finally {
                if (!silent) isCalculating.value = false
            }
        }
    }

    val fitRequests = MutableStateFlow(0)

    private fun setPlanData(data: PlanResponse) {
        planData.value = data
        val kept = selectedKey?.let { key -> data.options.indexOfFirst { it.key == key } } ?: -1
        val target = if (kept >= 0) kept else planParam?.takeIf { it >= 0 && it < data.options.size } ?: 0
        selectedIndex.value = target
        selectedKey = data.options.getOrNull(target)?.key
        plans.value = data.options
        refresh?.cancel()
        if (data.refreshIn > 0 && timeMode.value == TimeMode.Now) {
            refresh = viewModelScope.launch {
                delay(data.refreshIn * 1000)
                calculateRoutes(silent = true)
            }
        }
    }

    fun refreshRoutes() {
        clearPlanSelection()
        selectedIndex.value = 0
        selectedKey = null
        resetResults()
        calculateRoutes(force = true)
    }

    fun selectPlanAt(idx: Int) {
        selectedIndex.value = idx
        selectedKey = plans.value.getOrNull(idx)?.key
        planParam = idx.takeIf { it > 0 }
        fitRequests.value++
    }

    fun setTimeMode(mode: TimeMode) {
        if (timeMode.value == mode) return
        timeMode.value = mode
        if (mode != TimeMode.Now) mapVehicles.value = emptyList()
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
            searchResults.value = Places.search(q, 5)
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
        runCatching { Places.reverse(lat, lon)?.label }.getOrNull()
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

    /** Looks up the names a swap needs; the returned swap is applied by the caller. */
    suspend fun prepareSwap(): (() -> Unit)? {
        if (!hasValidCoords) return null
        val origin = customOrigin.value
        val newDestLat = origin?.lat ?: Services.location.value?.lat ?: return null
        val newDestLon = origin?.lon ?: Services.location.value?.lon ?: return null
        val oldLat = destLat
        val oldLon = destLon
        val oldName = destName.value
        val newDestName = origin?.name ?: reverseGeocode(newDestLat, newDestLon)
        val newOriginName = if (oldName == string(R.string.w_plan_origin_current_location)) reverseGeocode(oldLat, oldLon) else oldName
        return {
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

    /** Only the buses the selected option catches. */
    private fun caughtTrips(): List<String> {
        if (timeMode.value != TimeMode.Now) return emptyList()
        val rides = plans.value.getOrNull(selectedIndex.value)?.runs?.firstOrNull()?.rides.orEmpty()
        return rides.filter { it.vehicleId != 0 }.map { it.tripId }.distinct()
    }

    suspend fun live() {
        combine(plans, selectedIndex, timeMode) { _, _, _ -> caughtTrips() }
            .distinctUntilChanged()
            .collectLatest { trips ->
                if (trips.isEmpty()) {
                    live.value = null
                    return@collectLatest
                }
                LiveStream.live(emptyList(), trips).collect { live.value = it }
            }
    }

    private fun updateMapVehicles(state: LiveState?) {
        val plan = plans.value.getOrNull(selectedIndex.value)
        if (plan == null || state == null || timeMode.value != TimeMode.Now) {
            mapVehicles.value = emptyList()
            return
        }
        mapVehicles.value = plan.runs.firstOrNull()?.rides.orEmpty().mapNotNull { ride ->
            val vehicle = state.vehicles(ride.tripId).find { it.id == ride.vehicleId && ride.vehicleId != 0 } ?: return@mapNotNull null
            val line = line(ride.routeId) ?: return@mapNotNull null
            MapVehicle("${vehicle.id}", vehicle, line.shortName, line.color, vehicle.heading)
        }
    }

    private fun updateMapLayer() {
        val plan = plans.value.getOrNull(selectedIndex.value)
        if (plan == null) {
            mapLayer.value = PlanMapLayer()
            return
        }
        fun points(path: String) = decodePolyline(path).map { LatLon(it.first, it.second) }
        val rides = plan.rides
        val shapes = rides.mapIndexed { i, leg ->
            val id = leg.lines.firstOrNull()
            val line = line(id)
            MapShape(id?.let { plan.legTrips(i)[it] }.orEmpty(), line?.shortName.orEmpty(), line?.color ?: "#64748b", points(leg.path))
        }
        val walks = plan.legs.filter { !it.isRide }.map { points(it.path) }
        val highlights = LinkedHashMap<Int, StopHighlight>()
        rides.forEachIndexed { idx, leg ->
            highlights[leg.from] = if (idx == 0) StopHighlight.Green else StopHighlight.Purple
            leg.passed.forEach { highlights[it] = StopHighlight.Gray }
            if (idx == rides.lastIndex) highlights[leg.to] = StopHighlight.Red
            else if (leg.to != rides[idx + 1].from) highlights[leg.to] = StopHighlight.Amber
        }
        mapLayer.value = PlanMapLayer(shapes, walks, highlights)
    }
}
