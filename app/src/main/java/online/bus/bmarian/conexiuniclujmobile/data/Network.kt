package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.DayNote
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.byModeThenNumber
import online.bus.bmarian.conexiuniclujmobile.logic.mainMode
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleCalendar
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleDays
import online.bus.bmarian.conexiuniclujmobile.logic.scheduleDayAt
import java.io.File
import java.time.Instant

@Serializable
class NetworkTrip(
    @SerialName("trip_id") val tripId: String,
    @SerialName("stop_ids") val stopIds: IntArray = IntArray(0),
    @SerialName("stop_sequences") val stopSequences: IntArray = IntArray(0),
    @SerialName("shape_hash") val shapeHash: String? = null,
)

@Serializable
class NetworkRoute(
    @SerialName("route_id") val routeId: Int,
    @SerialName("route_short_name") val shortName: String,
    @SerialName("route_long_name") val longName: String = "",
    @SerialName("route_type") val type: Int = 3,
    @SerialName("route_color") val color: String = "#64748b",
    @SerialName("route_desc") val desc: String = "",
    val timetable: Timetable? = null,
    val trips: List<NetworkTrip> = emptyList(),
    val offsets: Map<String, List<IntArray>> = emptyMap(),
) {
    fun toRoute() = Route(routeId, shortName, longName, type, color, desc)
}

@Serializable
class NetworkBundle(
    val version: String = "",
    @SerialName("static_version") val staticVersion: String = "",
    @SerialName("generated_at") val generatedAt: String = "",
    val routes: List<NetworkRoute> = emptyList(),
    val stops: List<Stop> = emptyList(),
    val rules: NetworkRules = NetworkRules(),
    @SerialName("schedule_days") val scheduleDays: Map<String, String> = emptyMap(),
    @SerialName("day_notes") val dayNotes: Map<String, DayNote> = emptyMap(),
)

/** What the learners found that an offline timetable list still needs. */
@Serializable
class NetworkRules(@SerialName("late_grace_min") val lateGraceMin: Double = 1.0)

@Serializable
data class NetworkVersion(
    val version: String = "",
    @SerialName("static_version") val staticVersion: String = "",
)

fun clujClock(nowMs: Long, calendar: ScheduleCalendar = ScheduleDays.current): Pair<String, Int> =
    scheduleDayAt(nowMs, calendar) to Instant.ofEpochMilli(nowMs).atZone(CLUJ_ZONE).hour

private fun hasEntries(t: Timetable) = t.weekdays.entries.isNotEmpty() || t.saturday.entries.isNotEmpty() || t.sunday.entries.isNotEmpty()

class StationLook(val mode: Mode, val color: String, val routes: List<NetworkRoute>)

private fun modeColors(routes: List<NetworkRoute>): Map<Mode, String> =
    routes.sortedWith(byModeThenNumber({ it.type }, { it.shortName })).groupBy { modeOf(it.type) }.mapValues { (_, lines) ->
        val counts = LinkedHashMap<String, Int>()
        for (line in lines) counts[line.color] = (counts[line.color] ?: 0) + 1
        counts.maxByOrNull { it.value }!!.key
    }

class NetworkIndex(val bundle: NetworkBundle) {
    private val stopById = bundle.stops.associateBy { it.stopId }
    private val routeById = bundle.routes.associateBy { it.routeId }
    private val routeByShortName = bundle.routes.associateBy { it.shortName }
    private val sortedRoutes = bundle.routes.sortedBy { it.routeId }
    val routes: List<Route> = bundle.routes.map { it.toRoute() }
    val stops: List<Stop> get() = bundle.stops
    val lateGraceMin: Double get() = maxOf(0.5, bundle.rules.lateGraceMin)
    val calendar = ScheduleCalendar(bundle.scheduleDays, bundle.dayNotes)

    private val platformsByStation: Map<Int, List<Stop>> by lazy { bundle.stops.groupBy { it.station } }

    private val colorOf: Map<Mode, String> by lazy { modeColors(bundle.routes) }

    fun modeColor(mode: Mode): String? = colorOf[mode]

    private val looks: Map<Int, StationLook> by lazy {
        val linesAt = HashMap<Int, MutableList<NetworkRoute>>()
        for (route in bundle.routes.sortedWith(byModeThenNumber({ it.type }, { it.shortName }))) {
            // Only the lines a stop page lists.
            if (route.timetable?.let(::hasEntries) != true) continue
            route.trips.flatMap { trip -> trip.stopIds.asList().mapNotNull { stopById[it]?.station } }.toSet()
                .forEach { linesAt.getOrPut(it) { mutableListOf() } += route }
        }
        linesAt.mapValues { (_, lines) ->
            val mode = mainMode(lines.map { it.type })
            val colors = lines.filter { modeOf(it.type) == mode }.map { it.color }.toSet()
            StationLook(mode, colors.singleOrNull() ?: colorOf.getValue(mode), lines)
        }
    }

    fun look(stopId: Int): StationLook? = stopById[stopId]?.let { looks[it.station] }

    fun platforms(stopId: Int): List<Stop> {
        val stop = stopById[stopId] ?: return emptyList()
        return listOf(stop) + platformsByStation[stop.station].orEmpty().filter { it.stopId != stopId }
    }

    fun oneStopPerStation(stops: List<Stop>): List<Stop> {
        val seen = HashSet<Int>()
        return stops.filter { seen.add((stopById[it.stopId] ?: it).station) }
    }

    fun route(routeId: Int): NetworkRoute? = routeById[routeId]

    fun route(shortName: String): NetworkRoute? = routeByShortName[shortName]

    fun shapeHash(tripId: String): String? =
        routeIdFromTripId(tripId)?.let { routeById[it] }?.trips?.firstOrNull { it.tripId == tripId }?.shapeHash

    fun stopTimes(route: NetworkRoute, dayType: String, hour: Int): List<StopTime> {
        val offsets = route.offsets[dayType]?.getOrNull(hour)
        val out = ArrayList<StopTime>()
        var i = 0
        for (trip in route.trips) {
            trip.stopIds.forEachIndexed { k, stopId ->
                val stop = stopById[stopId]
                out += StopTime(
                    tripId = trip.tripId,
                    stopId = stopId,
                    offsetArrivalTime = (offsets?.getOrNull(i) ?: 0).toDouble(),
                    stopSequence = trip.stopSequences.getOrElse(k) { 0 },
                    headsign = stop?.name.orEmpty(),
                    routeShortName = route.shortName,
                    lat = stop?.lat ?: 0.0,
                    lon = stop?.lon ?: 0.0,
                )
                i++
            }
        }
        return out
    }

    fun stopInfo(stopId: Int, nowMs: Long): StopInfo? {
        val stop = stopById[stopId] ?: return null
        val (dayType, hour) = clujClock(nowMs, calendar)
        val trips = sortedSetOf<String>()
        val shapes = ArrayList<ShapeInfo>()
        for (route in sortedRoutes) {
            val here = route.trips.filter { stopId in it.stopIds }.map { it.tripId }
            if (here.isEmpty()) continue
            trips += here
            val timetable = route.timetable?.takeIf(::hasEntries) ?: continue
            shapes += ShapeInfo(
                shortName = route.shortName,
                longName = route.longName,
                routeId = route.routeId,
                type = route.type,
                color = route.color,
                stopTimesSingular = stopTimes(route, dayType, hour),
                timetable = timetable,
                lateGraceMin = lateGraceMin,
            )
        }
        return StopInfo(
            stopId = stop.stopId,
            name = stop.name,
            lat = stop.lat,
            lon = stop.lon,
            stopCode = stop.stopCode,
            outgoingTripIds = trips.filter { it.endsWith(OUTGOING_SUFFIX) },
            incomingTripIds = trips.filter { it.endsWith(INCOMING_SUFFIX) },
            shapesInfo = shapes,
            lateGraceMin = lateGraceMin,
        )
    }

    fun shapeInfo(route: Route, nowMs: Long): ShapeInfo? {
        val known = routeById[route.routeId] ?: return null
        val (dayType, hour) = clujClock(nowMs, calendar)
        return assembleShapeInfo(route, known.timetable, stopTimes(known, dayType, hour), lateGraceMin)
    }

    fun hourlyOffsets(shortName: String, dayType: String): Map<String, HourlyStopOffsets>? {
        val route = routeByShortName[shortName] ?: return null
        val stopIds = LinkedHashMap<String, List<Int>>()
        val hours = HashMap<String, MutableMap<String, List<Int>>>()
        for (hour in 0 until 24) {
            for ((tripId, trip) in stopTimes(route, dayType, hour).groupBy { it.tripId }) {
                val ordered = trip.sortedBy { it.stopSequence }
                stopIds.getOrPut(tripId) { ordered.map { it.stopId } }
                var cumulative = 0
                hours.getOrPut(tripId) { LinkedHashMap() }[hour.toString()] = ordered.map { st ->
                    cumulative += st.offsetArrivalTime.toInt()
                    cumulative
                }
            }
        }
        return stopIds.mapValues { (tripId, ids) -> HourlyStopOffsets(ids, hours[tripId].orEmpty()) }
    }
}

private const val MAX_AGE_MS = 12 * 60 * 60_000L
private const val CHECK_EVERY_MS = 15 * 60_000L

class NetworkStore(context: Context, val city: String) {
    private val file = File(context.filesDir, "network/$city.json")
    private val prefs = cityPrefs(context, "network", city)
    private val mutex = Mutex()
    private var lastCheck = 0L

    init {
        // Saved before cities, under one name.
        val old = File(context.filesDir, "network.json")
        if (city == Cities.DEFAULT && old.exists()) {
            file.parentFile?.mkdirs()
            if (file.exists()) old.delete() else old.renameTo(file)
        }
    }

    private val _index = MutableStateFlow<NetworkIndex?>(null)
    val index: StateFlow<NetworkIndex?> = _index

    suspend fun load() = mutex.withLock {
        if (_index.value != null || !file.exists()) return@withLock
        val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() } ?: return@withLock
        parse(text)?.let { _index.value = it }
    }

    private suspend fun parse(text: String): NetworkIndex? = withContext(Dispatchers.Default) {
        runCatching { NetworkIndex(Api.json.decodeFromString<NetworkBundle>(text)) }.getOrNull()
            ?.takeIf { it.bundle.routes.isNotEmpty() && it.bundle.stops.isNotEmpty() }
    }

    suspend fun refresh(now: Boolean = false): Boolean = mutex.withLock {
        val at = System.currentTimeMillis()
        if (!now && at - lastCheck < CHECK_EVERY_MS) return@withLock true
        lastCheck = at
        runCatching {
            val latest = Api.networkVersion(city)
            val have = _index.value
            val fresh = have != null && prefs.getString("static_version", null) == latest.staticVersion &&
                at - prefs.getLong("fetched_at", 0) < MAX_AGE_MS
            if (fresh) return@runCatching
            if (have != null && have.bundle.version == latest.version) {
                prefs.edit { putLong("fetched_at", at) }
                return@runCatching
            }
            val text = Api.networkText(city)
            val fetched = parse(text) ?: return@runCatching
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$city.json.tmp")
                tmp.writeText(text)
                tmp.renameTo(file)
            }
            _index.value = fetched
            prefs.edit {
                putString("static_version", fetched.bundle.staticVersion)
                putLong("fetched_at", at)
            }
        }.isSuccess
    }
}
