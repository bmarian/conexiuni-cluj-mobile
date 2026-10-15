package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.max

internal const val AT_STOP_M = 40.0
internal const val LEFT_STOP_M = 150.0
private const val ON_LINE_M = 75.0
// 72 km/h, faster than a bus goes in town.
private const val MAX_BUS_MPS = 20.0
private const val IN_A_ROW = 2

internal fun distanceTo(fix: Fix, stop: JourneyStop) = haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon)

// The ride's line starts at the stop, so being on it away from the stop is being down the line.
internal fun leftOnBus(fix: Fix, leg: JourneyLeg): Boolean =
    distanceTo(fix, leg.boarding) > LEFT_STOP_M && !offLine(fix, leg)

internal fun rideRoute(leg: JourneyLeg): RouteLine =
    RouteLine(leg.path.takeIf { it.size >= 2 } ?: leg.stops.map { JourneyPoint(it.lat, it.lon) })

/** Each stop's metres along the ride, taken in order so a line passing near itself keeps them apart. */
internal fun stopsAlong(leg: JourneyLeg, route: RouteLine): List<Double> = alongEach(route, leg.stops.map { JourneyPoint(it.lat, it.lon) })

private fun alongEach(route: RouteLine, points: List<JourneyPoint>): List<Double> {
    var from = 0.0
    return points.map { p -> (route.locate(p.lat, p.lon, from)?.along ?: from).also { from = it } }
}

/** The part of a line's shape between two of its stops, given all its stops in order. */
internal fun ridePath(shape: List<JourneyPoint>, stops: List<JourneyPoint>, boarding: Int, alighting: Int): List<JourneyPoint> {
    if (shape.size < 2 || boarding !in stops.indices || alighting !in stops.indices) return emptyList()
    val route = RouteLine(shape)
    val along = alongEach(route, stops.take(alighting + 1))
    return route.between(along[boarding], along[alighting])
}

/** A fix counts only near the line and within reach since the last, so a stray fix can't skip stops. */
internal class RideTracker(leg: JourneyLeg, startMs: Long) {
    private val route = rideRoute(leg)
    private val stops = stopsAlong(leg, route)
    private var fixAt = startMs
    private var seen: Fix? = null
    var along = stops.first()
        private set
    var stop = 0
        private set
    val there get() = along >= stops.last() - AT_STOP_M

    fun update(fix: Fix, nowMs: Long) {
        if (fix == seen) return
        seen = fix
        // Only what the fix's own error cannot take back counts as ridden.
        val error = fix.accuracyMeters.toDouble()
        val spot = route.locate(fix.lat, fix.lon, along - error, along + reachSince(fixAt, nowMs, error)) ?: return
        if (spot.off > max(ON_LINE_M, 2 * error)) return
        along = max(along, spot.along - error)
        fixAt = nowMs
        stop = max(stop, stops.indexOfLast { it <= along })
    }
}

/** Metres a bus could have gone since `sinceMs`, give or take a fix's error. */
internal fun reachSince(sinceMs: Long, nowMs: Long, error: Double) =
    MAX_BUS_MPS * (nowMs - sinceMs).coerceAtLeast(0) / 1000 + 2 * error + AT_STOP_M

/** Far enough from the ride's line that it is not the bus's GPS wandering. */
internal fun farOffLine(fix: Fix, leg: JourneyLeg): Boolean =
    offLine(fix, leg, max(ON_LINE_M, 2.0 * fix.accuracyMeters))

/** The walk that ends nearest `lat`/`lon`, the way to it. */
internal fun walkRoute(journey: Journey, lat: Double, lon: Double): RouteLine? =
    journey.walks.filter { it.size >= 2 }.minByOrNull { haversineMeters(it.last().lat, it.last().lon, lat, lon) }?.let(::RouteLine)

/** m/s halfway between a walk and the leg's planned ride: faster is riding, slower is walking. */
internal fun ridePace(leg: JourneyLeg): Double? {
    if (leg.rideMinutes <= 0) return null
    return (WALK_SPEED / 60 + rideRoute(leg).length / (leg.rideMinutes * 60)) / 2
}

/** Further from the ride's line than standing at one of its stops. */
internal fun offLine(fix: Fix, leg: JourneyLeg, near: Double = max(AT_STOP_M, fix.accuracyMeters.toDouble())): Boolean {
    val off = rideRoute(leg).locate(fix.lat, fix.lon)?.off ?: return false
    return off > near
}

/** How you move, judged only over stretches longer than the fixes' own error. */
internal class Motion {
    private var anchor: Fix? = null
    private var anchorAt = 0L
    private var streakFrom: Fix? = null
    private var streakAt = 0L
    private var fast = false
    private var stretches = 0

    fun update(fix: Fix, nowMs: Long, pace: Double?) {
        val a = anchor
        if (a == null) {
            anchor = fix
            anchorAt = nowMs
            return
        }
        val moved = haversineMeters(a.lat, a.lon, fix.lat, fix.lon)
        if (pace == null || nowMs <= anchorAt || moved <= a.accuracyMeters + fix.accuracyMeters) return
        val isFast = moved / ((nowMs - anchorAt) / 1000.0) > pace
        if (stretches == 0 || isFast != fast) {
            streakFrom = a
            streakAt = anchorAt
            fast = isFast
            stretches = 0
        }
        stretches++
        anchor = fix
        anchorAt = nowMs
    }

    // A GPS jump and back is two fast stretches that go nowhere.
    fun riding(fix: Fix, nowMs: Long, pace: Double?): Boolean {
        val from = streakFrom ?: return false
        if (pace == null || !fast || stretches < IN_A_ROW || nowMs <= streakAt) return false
        return haversineMeters(from.lat, from.lon, fix.lat, fix.lon) / ((nowMs - streakAt) / 1000.0) > pace
    }

    fun walking(): Boolean = !fast && stretches >= IN_A_ROW

    fun reset() {
        stretches = 0
    }
}
