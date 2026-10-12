package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.max

// Port of RoutePlanningView.vue. Times are epoch ms; day minutes and day types in Cluj time.

const val PLAN_MAX_MINUTES = 60
const val WALK_SPEED = 80.0 // m/min
const val VEHICLE_LOOKAHEAD_STOPS = 3

@Serializable
data class PlanStop(
    @SerialName("stop_id") val stopId: Int,
    @SerialName("stop_name") val name: String = "",
    @SerialName("stop_lat") val lat: Double = 0.0,
    @SerialName("stop_lon") val lon: Double = 0.0,
)

@Serializable
data class PlanWalkSeg(
    val geometry: String = "",
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_sec") val durationSec: Double = 0.0,
)

@Serializable
data class ApiLeg(
    @SerialName("route_id") val routeId: Int,
    @SerialName("trip_id") val tripId: String,
    @SerialName("start_stop_id") val startStopId: Int,
    @SerialName("dest_stop_id") val destStopId: Int,
    @SerialName("ride_seconds") val rideSeconds: Double = 0.0,
    @SerialName("intermediate_stop_ids") val intermediateStopIds: List<Int>? = null,
)

@Serializable
data class ApiPlan(
    val legs: List<ApiLeg> = emptyList(),
    @SerialName("is_direct") val isDirect: Boolean = false,
    @SerialName("walk_start_meters") val walkStartMeters: Double = 0.0,
    @SerialName("walk_end_meters") val walkEndMeters: Double = 0.0,
    @SerialName("walk_transfer_meters") val walkTransferMeters: Double = 0.0,
    @SerialName("transit_duration_sec") val transitDurationSec: Double = 0.0,
    @SerialName("total_distance") val totalDistance: Double = 0.0,
    @SerialName("generalized_cost") val generalizedCost: Double? = null,
    @SerialName("number_of_transfers") val numberOfTransfers: Int? = null,
    @SerialName("start_time_ms") val startTimeMs: Long? = null,
    @SerialName("end_time_ms") val endTimeMs: Long? = null,
    @SerialName("walk_segments") val walkSegments: List<PlanWalkSeg>? = null,
)

@Serializable
data class PlanResponse(
    val plans: List<ApiPlan> = emptyList(),
    val stops: Map<String, PlanStop> = emptyMap(),
    val shapes: Map<String, ShapeInfo> = emptyMap(),
)

data class PlannedTimeEntry(
    val minutes: Double,
    val isLive: Boolean,
    val routeId: Int? = null,
    val tripId: String? = null,
    val transferRouteId: Int? = null,
    val transferTripId: String? = null,
)

class StoredLiveEta(val entry: PlannedTimeEntry, val ts: Long)

data class RichLeg(
    val routeIds: List<Int>,
    val tripIds: List<String>,
    val startStopId: Int,
    val destStopId: Int,
    val rideSeconds: Double,
    val intermediateStopIds: List<Int>,
)

data class GroupedPlan(
    val legs: List<RichLeg>,
    val isDirect: Boolean,
    val walkStartMeters: Double,
    val walkEndMeters: Double,
    val walkTransferMeters: Double,
    val walkSegments: List<PlanWalkSeg>,
    val generalizedCost: Double,
    val numberOfTransfers: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

data class RichPlan(
    val plan: GroupedPlan,
    val nextTimes: List<PlannedTimeEntry>,
    val isLive: Boolean,
    val key: String,
) {
    val legs get() = plan.legs
    val isDirect get() = plan.isDirect
    val walkStartMeters get() = plan.walkStartMeters
    val walkEndMeters get() = plan.walkEndMeters
    val walkTransferMeters get() = plan.walkTransferMeters
    val walkSegments get() = plan.walkSegments
    val endTimeMs get() = plan.endTimeMs
}

enum class TimeMode(val key: String) { Now("now"), Leave("leave"), Arrive("arrive"), Last("last") }

// JS new Date(x) truncates to whole milliseconds.
private fun jsDate(ms: Double): Long = ms.toLong()

private fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), CLUJ_ZONE)

private fun minutesOfDayMs(ms: Long): Int = minutesOfDay(local(ms))

private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun formatLocalDateTime(ms: Long): String = local(ms).format(LOCAL_FORMAT)

fun lastTransitCutoffString(nowMs: Long): String {
    val now = local(nowMs)
    var cutoff = now.withHour(3).withMinute(0).withSecond(0).withNano(0)
    if (!cutoff.isAfter(now)) cutoff = cutoff.plusDays(1)
    return cutoff.format(LOCAL_FORMAT)
}

private val PLANNER_DATE_TIME = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$""")

fun parsePlannerDateTime(value: String?): Long? {
    if (value.isNullOrEmpty()) return null
    val m = PLANNER_DATE_TIME.matchEntire(value) ?: return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    val (y, mo, d, hh, mm) = m.destructured
    val ss = m.groupValues[6].ifEmpty { "0" }
    return runCatching {
        LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), hh.toInt(), mm.toInt(), ss.toInt()).atZone(CLUJ_ZONE).toInstant().toEpochMilli()
    }.getOrNull()
}

/** JS toFixed(4). */
fun toFixed4(v: Double): String = BigDecimal(v).setScale(4, RoundingMode.HALF_UP).toPlainString()

fun clampCoord4(v: Double): Double = toFixed4(v).toDouble()

fun getScheduleDiffs(arrivalAtStopMinutes: Double, referenceMinutes: Int, maxMinutes: Int, arriveBy: Boolean): List<Double> {
    val offsets = if (arriveBy) listOf(-1440, 0) else listOf(0, 1440)
    return offsets.map { arrivalAtStopMinutes + it - referenceMinutes }
        .filter { diff -> if (arriveBy) diff <= 0 && diff >= -maxMinutes else diff >= 0 && diff <= maxMinutes }
}

private val COMBINING = Regex("[\\u0300-\\u036f]")
private val NON_ALNUM = Regex("[^a-z0-9]+")

fun normalizeStopLabelForMatch(label: String): String =
    Normalizer.normalize(label, Normalizer.Form.NFD).replace(COMBINING, "").lowercase().replace(NON_ALNUM, " ").trim()

fun stopLabelsMatch(a: String, b: String): Boolean {
    val na = normalizeStopLabelForMatch(a)
    val nb = normalizeStopLabelForMatch(b)
    if (na.isEmpty() || nb.isEmpty()) return false
    return na == nb || na.contains(nb) || nb.contains(na)
}

interface StopLookup {
    fun name(id: Int): String?
    fun position(id: Int): Pair<Double, Double>?
}

fun tripUsesDepartureIn(shape: ShapeInfo, tripId: String, stops: StopLookup): Boolean {
    val timetable = shape.timetable
    val firstStopId = shape.stopTimes.firstOrNull { it.tripId == tripId }?.stopId
    val firstStopName = firstStopId?.let(stops::name)
    if (firstStopName.isNullOrEmpty()) return tripId.endsWith("_0")
    if (stopLabelsMatch(firstStopName, timetable.inStopName)) return true
    if (stopLabelsMatch(firstStopName, timetable.outStopName)) return false
    return tripId.endsWith("_0")
}

fun getRideMinutesBetweenStops(stopTimes: List<StopTime>, tripId: String, fromStopId: Int, toStopId: Int): Int {
    var started = false
    var totalSec = 0.0
    for (st in stopTimes) {
        if (st.tripId != tripId) continue
        if (!started) {
            if (st.stopId == fromStopId) started = true
            continue
        }
        totalSec += st.offsetArrivalTime
        if (st.stopId == toStopId) return ceil(totalSec / 60).toInt()
    }
    return 0
}

class Planner(private val shapes: Map<String, ShapeInfo>, private val stops: StopLookup) {

    fun getTripDeparturesAtStop(
        shape: ShapeInfo,
        tripId: String,
        routeId: Int,
        boardingStopId: Int,
        nowMs: Long,
        limit: Int = 3,
        maxMinutes: Int = PLAN_MAX_MINUTES,
        arriveBy: Boolean = false,
    ): List<PlannedTimeEntry> {
        val timetable = shape.timetable
        val isOutgoing = tripUsesDepartureIn(shape, tripId, stops)
        val offsetMin = timeOffsetToStop(shape.stopTimes, tripId, boardingStopId) / 60
        val daySchedule = daySchedule(timetable, local(nowMs))
        val nowMins = minutesOfDayMs(nowMs)
        val results = mutableListOf<PlannedTimeEntry>()
        for (entry in daySchedule.entries) {
            val terminusMinutes = timeStringToMinutes(if (isOutgoing) entry.departureIn else entry.departureOut) ?: continue
            val arrivalAtBoardingMin = terminusMinutes + offsetMin
            for (diff in getScheduleDiffs(arrivalAtBoardingMin, nowMins, maxMinutes, arriveBy)) {
                // Shown to the minute, like the website's planner.
                results += PlannedTimeEntry(diff.roundToInt().toDouble(), false, routeId, tripId)
            }
        }
        if (arriveBy) {
            return results.sortedByDescending { it.minutes }.take(limit).sortedBy { it.minutes }
        }
        return results.sortedBy { it.minutes }.take(limit)
    }

    fun getLegDepartures(
        leg: RichLeg,
        nowMs: Long,
        limit: Int = 3,
        maxMinutes: Int = PLAN_MAX_MINUTES,
        arriveBy: Boolean = false,
    ): List<PlannedTimeEntry> {
        val times = mutableListOf<PlannedTimeEntry>()
        for (i in leg.routeIds.indices) {
            val routeId = leg.routeIds[i]
            val tripId = leg.tripIds.getOrNull(i)
            if (tripId.isNullOrEmpty()) continue
            val shape = shapes["$routeId"] ?: continue
            times += getTripDeparturesAtStop(shape, tripId, routeId, leg.startStopId, nowMs, limit, maxMinutes, arriveBy)
        }
        val sorted = if (arriveBy) times.sortedByDescending { it.minutes } else times.sortedBy { it.minutes }
        return sorted.take(limit)
    }

    fun getRideMinutes(leg: RichLeg, tripId: String? = null): Int {
        val selectedTripId = tripId ?: leg.tripIds.firstOrNull() ?: return ceil(leg.rideSeconds / 60).toInt()
        val routeId = leg.routeIds.getOrNull(leg.tripIds.indexOf(selectedTripId)) ?: leg.routeIds.firstOrNull()
        val shape = routeId?.let { shapes["$it"] }
        val fromStopTimes = shape?.stopTimes.orEmpty()
        val fromStopTimesRide = if (fromStopTimes.isNotEmpty()) {
            getRideMinutesBetweenStops(fromStopTimes, selectedTripId, leg.startStopId, leg.destStopId)
        } else 0
        return if (fromStopTimesRide != 0) fromStopTimesRide else ceil(leg.rideSeconds / 60).toInt()
    }

    fun hasScheduledConnection(plan: GroupedPlan, firstDeparture: PlannedTimeEntry, nowMs: Long): Boolean {
        if (plan.isDirect || plan.legs.size < 2) return true
        val leg1 = plan.legs[0]
        val leg2 = plan.legs[1]
        val transferWalkMin = plan.walkTransferMeters / WALK_SPEED
        val leg1RideMin = getRideMinutes(leg1, firstDeparture.tripId)
        val readyForLeg2 = jsDate(nowMs + (firstDeparture.minutes + leg1RideMin + transferWalkMin) * 60_000)
        return getLegDepartures(leg2, readyForLeg2, 1, PLAN_MAX_MINUTES, false).isNotEmpty()
    }

    fun computeNextTimesForPlan(plan: GroupedPlan, nowMs: Long, arriveBy: Boolean = false): List<PlannedTimeEntry> {
        if (plan.legs.isEmpty()) {
            val walkTotalMin = getWalkMinutes(plan)
            return listOf(PlannedTimeEntry(if (arriveBy) -walkTotalMin else 0.0, false))
        }
        val walkStartMin = plan.walkStartMeters / WALK_SPEED
        val walkEndMin = plan.walkEndMeters / WALK_SPEED
        if (plan.isDirect) {
            val leg = plan.legs[0]
            val rideMin = getRideMinutes(leg)
            val refTime = if (arriveBy) jsDate(nowMs - (rideMin + walkEndMin) * 60_000) else nowMs
            val offset = (refTime - nowMs) / 60_000.0
            return getLegDepartures(leg, refTime, 6, PLAN_MAX_MINUTES, arriveBy)
                .map { it.copy(minutes = it.minutes + offset) }
                .filter { arriveBy || it.minutes >= walkStartMin }
                .sortedBy { it.minutes }
                .take(3)
        }
        val leg1 = plan.legs[0]
        val leg2 = plan.legs[1]
        if (arriveBy) {
            val refTime2Base = jsDate(nowMs - walkEndMin * 60_000 - getRideMinutes(leg2) * 60_000.0)
            val leg2Deps = getLegDepartures(leg2, refTime2Base, 8, PLAN_MAX_MINUTES, true)
            val valid = mutableListOf<PlannedTimeEntry>()
            for (t2 in leg2Deps) {
                val leg2BoardTime = jsDate(refTime2Base + t2.minutes * 60_000)
                val refTime1Base = jsDate(leg2BoardTime - (getRideMinutes(leg1) + plan.walkTransferMeters / WALK_SPEED) * 60_000)
                val bestT1 = getLegDepartures(leg1, refTime1Base, 1, PLAN_MAX_MINUTES, true).firstOrNull() ?: continue
                valid += bestT1.copy(
                    minutes = bestT1.minutes + (refTime1Base - nowMs) / 60_000.0,
                    isLive = false,
                    transferRouteId = t2.routeId,
                    transferTripId = t2.tripId,
                )
            }
            return valid.sortedByDescending { it.minutes }.take(3).sortedBy { it.minutes }
        }
        val leg1Deps = getLegDepartures(leg1, nowMs, 12, PLAN_MAX_MINUTES, false).filter { it.minutes >= walkStartMin }
        return leg1Deps.filter { hasScheduledConnection(plan, it, nowMs) }.sortedBy { it.minutes }.take(3)
    }

    private fun stopSequenceIndex(shape: ShapeInfo, tripId: String, stopId: Int): Int =
        shape.stopTimes.firstOrNull { it.tripId == tripId && it.stopId == stopId }?.stopSequence ?: -1

    fun resolveTripIdForLeg(routeId: Int, fallbackTripId: String, startStopId: Int, destStopId: Int, shape: ShapeInfo?): String {
        if (shape == null) return fallbackTripId
        for (tripId in listOf("${routeId}_0", "${routeId}_1")) {
            val startSeq = stopSequenceIndex(shape, tripId, startStopId)
            val destSeq = stopSequenceIndex(shape, tripId, destStopId)
            if (startSeq >= 0 && destSeq > startSeq) return tripId
        }
        return fallbackTripId
    }

    fun normalizeLegDirections(grouped: List<GroupedPlan>): List<GroupedPlan> = grouped.map { plan ->
        plan.copy(
            legs = plan.legs.map { leg ->
                val routeIds = mutableListOf<Int>()
                val tripIds = mutableListOf<String>()
                for (i in leg.routeIds.indices) {
                    val routeId = leg.routeIds[i]
                    val tripId = leg.tripIds.getOrNull(i)
                    if (tripId.isNullOrEmpty()) continue
                    val normalized = resolveTripIdForLeg(routeId, tripId, leg.startStopId, leg.destStopId, shapes["$routeId"])
                    if (routeId in routeIds) continue
                    routeIds += routeId
                    tripIds += normalized
                }
                leg.copy(routeIds = routeIds, tripIds = tripIds)
            },
        )
    }

    fun enrichWithAlternativesFromShapes(grouped: List<GroupedPlan>, nowMs: Long): List<GroupedPlan> {
        // Object.entries visits integer keys in ascending order.
        val ordered = shapes.entries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.sortedBy { it.first }
        return grouped.map { plan ->
            plan.copy(
                legs = plan.legs.map { leg ->
                    val dir = if (leg.tripIds.firstOrNull()?.endsWith("_0") == true) "0" else "1"
                    val mergedRouteIds = leg.routeIds.toMutableList()
                    val mergedTripIds = leg.tripIds.toMutableList()
                    val required = listOf(leg.startStopId) + leg.intermediateStopIds + leg.destStopId
                    for ((routeId, shape) in ordered) {
                        if (routeId in mergedRouteIds) continue
                        val tripId = resolveTripIdForLeg(routeId, "${routeId}_$dir", leg.startStopId, leg.destStopId, shape)
                        val stopTimes = shape.stopTimes.filter { it.tripId == tripId }
                        if (stopTimes.isEmpty()) continue
                        var reqIdx = 0
                        for (st in stopTimes) {
                            if (st.stopId == required[reqIdx]) {
                                reqIdx++
                                if (reqIdx == required.size) break
                            }
                        }
                        if (reqIdx < required.size) continue
                        if (getTripDeparturesAtStop(shape, tripId, routeId, leg.startStopId, nowMs, 1, 30).isEmpty()) continue
                        mergedRouteIds += routeId
                        mergedTripIds += tripId
                    }
                    if (mergedRouteIds.size == leg.routeIds.size) leg else leg.copy(routeIds = mergedRouteIds, tripIds = mergedTripIds)
                },
            )
        }
    }

    fun buildRichPlan(plan: GroupedPlan, nowMs: Long, arriveBy: Boolean): RichPlan? {
        var nextTimes = computeNextTimesForPlan(plan, nowMs, arriveBy)
        // OTP can find a run the timetable misses (03:00 cutoff): use its start time.
        if (nextTimes.isEmpty() && plan.startTimeMs > 0) {
            val walkStartMin = plan.walkStartMeters / WALK_SPEED
            val boardingMs = if (plan.legs.isNotEmpty()) plan.startTimeMs + walkStartMin * 60_000 else plan.startTimeMs.toDouble()
            nextTimes = listOf(PlannedTimeEntry((boardingMs - nowMs) / 60_000.0, false))
        }
        if (nextTimes.isEmpty()) return null
        return RichPlan(plan, nextTimes, false, planKey(plan))
    }

    fun getDecayedLiveEntries(entries: List<StoredLiveEta>?, nowMs: Long): List<PlannedTimeEntry> {
        if (entries.isNullOrEmpty()) return emptyList()
        return entries.map { it.entry.copy(minutes = it.entry.minutes - (nowMs - it.ts) / 60_000.0) }
            .filter { it.minutes > 0 && it.minutes <= PLAN_MAX_MINUTES }
            .sortedBy { it.minutes }
    }

    fun mergeLiveAndScheduled(plan: RichPlan, liveEntries: List<PlannedTimeEntry>, scheduledEntries: List<PlannedTimeEntry>): List<PlannedTimeEntry> {
        val walkStartMin = plan.walkStartMeters / WALK_SPEED
        val catchableLive = liveEntries.filter { it.minutes >= walkStartMin }
        val liveTripKeys = catchableLive.map { "${it.routeId ?: ""}:${it.tripId ?: ""}" }.toSet()
        val scheduledWithoutLive = scheduledEntries.filter { "${it.routeId ?: ""}:${it.tripId ?: ""}" !in liveTripKeys }
        val entries = if (catchableLive.isNotEmpty()) {
            catchableLive.sortedBy { it.minutes } + scheduledWithoutLive.sortedBy { it.minutes }
        } else scheduledEntries.sortedBy { it.minutes }
        val merged = mutableListOf<PlannedTimeEntry>()
        val seen = HashSet<String>()
        for (entry in entries) {
            val key = "${if (entry.isLive) 'L' else 'S'}:${entry.routeId ?: ""}:${entry.tripId ?: ""}:${Math.round(entry.minutes)}"
            if (!seen.add(key)) continue
            merged += entry
            if (merged.size >= 3) break
        }
        return merged.ifEmpty { scheduledEntries.sortedBy { it.minutes }.take(3) }
    }

    fun applyTiming(
        plans: List<RichPlan>,
        nowMs: Long,
        mode: TimeMode,
        timeValue: String,
        liveEtaByKey: Map<String, List<StoredLiveEta>>,
    ): List<RichPlan> {
        val arriveBy = mode == TimeMode.Arrive || mode == TimeMode.Last
        val requested = requestedTime(nowMs, mode, timeValue)
        val offsetMin = (requested - nowMs) / 60_000.0
        return plans.map { plan ->
            val scheduled = computeNextTimesForPlan(plan.plan, requested, arriveBy).map { it.copy(minutes = it.minutes + offsetMin) }
            val liveEntries = if (mode == TimeMode.Now) {
                getDecayedLiveEntries(liveEtaByKey[plan.key], nowMs).filter { hasScheduledConnection(plan.plan, it, nowMs) }
            } else emptyList()
            val walkStartMin = plan.walkStartMeters / WALK_SPEED
            val isLive = liveEntries.any { it.minutes >= walkStartMin }
            val nextTimes = if (scheduled.isNotEmpty() || liveEntries.isNotEmpty()) {
                mergeLiveAndScheduled(plan, liveEntries, scheduled)
            } else plan.nextTimes.filter { !it.isLive }
            plan.copy(nextTimes = nextTimes, isLive = isLive)
        }
    }

    fun buildPlans(data: PlanResponse, nowMs: Long, mode: TimeMode, timeValue: String): List<RichPlan> {
        if (data.plans.isEmpty()) return emptyList()
        val arriveBy = mode == TimeMode.Arrive || mode == TimeMode.Last
        val requested = requestedTime(nowMs, mode, timeValue)
        val grouped = enrichWithAlternativesFromShapes(normalizeLegDirections(groupPlans(data.plans)), requested)
        val offsetMin = (requested - nowMs) / 60_000.0
        val results = grouped.mapNotNull { plan ->
            buildRichPlan(plan, requested, arriveBy)?.let { rich ->
                rich.copy(nextTimes = rich.nextTimes.map { it.copy(minutes = it.minutes + offsetMin) })
            }
        }
        val sorted = results.sortedWith { a, b ->
            val walkOnlyA = a.legs.isEmpty()
            val walkOnlyB = b.legs.isEmpty()
            when {
                walkOnlyA != walkOnlyB -> if (walkOnlyA) 1 else -1
                mode == TimeMode.Last -> b.endTimeMs.compareTo(a.endTimeMs)
                else -> computeTotalMinutes(a).compareTo(computeTotalMinutes(b))
            }
        }
        return sorted.filter { it.legs.isNotEmpty() }.take(6) + sorted.filter { it.legs.isEmpty() }
    }

    fun getTransferWalkMeters(plan: RichPlan, legIdx: Int): Double {
        val a = plan.legs.getOrNull(legIdx) ?: return 0.0
        val b = plan.legs.getOrNull(legIdx + 1) ?: return 0.0
        if (a.destStopId == b.startStopId) return 0.0
        val aStop = stops.position(a.destStopId) ?: return 0.0
        val bStop = stops.position(b.startStopId) ?: return 0.0
        return haversineMeters(aStop.first, aStop.second, bStop.first, bStop.second)
    }

    companion object {
        fun requestedTime(nowMs: Long, mode: TimeMode, timeValue: String): Long = when {
            mode == TimeMode.Last -> parsePlannerDateTime(lastTransitCutoffString(nowMs)) ?: nowMs
            mode != TimeMode.Now && timeValue.isNotEmpty() -> parsePlannerDateTime(timeValue) ?: nowMs
            else -> nowMs
        }

        fun groupPlans(rawPlans: List<ApiPlan>): List<GroupedPlan> {
            val groups = LinkedHashMap<String, GroupedPlan>()
            for (p in rawPlans) {
                val key = p.legs.joinToString("|") { "${it.startStopId}>${it.destStopId}@${if (it.tripId.endsWith("_0")) '0' else '1'}" }
                val existing = groups[key]
                if (existing != null) {
                    val legs = existing.legs.mapIndexed { i, rich ->
                        val api = p.legs.getOrNull(i) ?: return@mapIndexed rich
                        if (api.routeId in rich.routeIds) rich else rich.copy(routeIds = rich.routeIds + api.routeId, tripIds = rich.tripIds + api.tripId)
                    }
                    val cost = p.generalizedCost ?: Double.POSITIVE_INFINITY
                    groups[key] = existing.copy(legs = legs, generalizedCost = minOf(existing.generalizedCost, cost))
                } else {
                    groups[key] = GroupedPlan(
                        legs = p.legs.map {
                            RichLeg(listOf(it.routeId), listOf(it.tripId), it.startStopId, it.destStopId, it.rideSeconds, it.intermediateStopIds.orEmpty())
                        },
                        isDirect = p.isDirect,
                        walkStartMeters = p.walkStartMeters,
                        walkEndMeters = p.walkEndMeters,
                        walkTransferMeters = p.walkTransferMeters,
                        walkSegments = p.walkSegments.orEmpty(),
                        generalizedCost = p.generalizedCost ?: Double.POSITIVE_INFINITY,
                        numberOfTransfers = p.numberOfTransfers ?: max(0, p.legs.size - 1),
                        startTimeMs = p.startTimeMs ?: 0,
                        endTimeMs = p.endTimeMs ?: 0,
                    )
                }
            }
            return groups.values.toList()
        }

        fun planKey(p: GroupedPlan): String {
            if (p.legs.isEmpty()) return "W:${Math.round(p.walkStartMeters + p.walkTransferMeters + p.walkEndMeters)}"
            return (if (p.isDirect) "D" else "C${p.legs.size}") + ":" + p.legs.joinToString("|") { l ->
                "${l.routeIds.firstOrNull() ?: "x"}/${if (l.tripIds.firstOrNull()?.endsWith("_0") == true) '0' else '1'}@${l.startStopId}>${l.destStopId}"
            }
        }

        fun getWalkMinutes(plan: GroupedPlan): Double {
            val fromSegments = plan.walkSegments.sumOf { it.durationSec } / 60
            if (fromSegments > 0) return fromSegments
            return (plan.walkStartMeters + plan.walkTransferMeters + plan.walkEndMeters) / WALK_SPEED
        }

        fun computeTotalMinutes(plan: RichPlan): Double {
            if (plan.nextTimes.isEmpty()) return Double.POSITIVE_INFINITY
            val first = plan.nextTimes.firstOrNull()?.minutes ?: 0.0
            if (plan.legs.isEmpty()) return Math.round(first + getWalkMinutes(plan.plan)).toDouble()
            val rideMin = plan.legs.sumOf { it.rideSeconds / 60 }
            val transferPenalty = (plan.legs.size - 1) * 5
            val walkEndMin = plan.walkEndMeters / WALK_SPEED
            return Math.round(first + rideMin + transferPenalty + walkEndMin).toDouble()
        }

        fun getJourneyDuration(plan: RichPlan): Double {
            val walkTime = getWalkMinutes(plan.plan)
            val rideTime = plan.legs.sumOf { it.rideSeconds / 60 }
            val transferWait = if (plan.isDirect) 0 else (plan.legs.size - 1) * 2
            return walkTime + rideTime + transferWait
        }

        fun formatDuration(m: Double): String {
            val rounded = Math.round(m).toInt()
            if (rounded < 60) return "$rounded min"
            val h = Math.floorDiv(rounded, 60)
            val rem = rounded % 60
            return if (rem == 0) "$h h" else "$h h $rem min"
        }

        fun decodePolyline(encoded: String, precision: Int = 5): List<Pair<Double, Double>> {
            val factor = Math.pow(10.0, precision.toDouble())
            val coords = mutableListOf<Pair<Double, Double>>()
            var index = 0
            var lat = 0
            var lng = 0
            while (index < encoded.length) {
                var shift = 0
                var result = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20 && index < encoded.length)
                lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
                shift = 0
                result = 0
                if (index >= encoded.length) break
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20 && index < encoded.length)
                lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
                coords += lat / factor to lng / factor
            }
            return coords
        }
    }
}
