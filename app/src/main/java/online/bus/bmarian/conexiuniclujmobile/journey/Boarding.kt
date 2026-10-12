package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.max

internal const val AT_STOP_M = 40.0
internal const val LEFT_STOP_M = 150.0
private const val IN_A_ROW = 2

internal fun distanceTo(fix: Fix, stop: JourneyStop) = haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon)

// The ride's line starts at the stop, so being on it away from the stop is being down the line.
internal fun leftOnBus(fix: Fix, leg: JourneyLeg): Boolean =
    distanceTo(fix, leg.boarding) > LEFT_STOP_M && !offLine(fix, leg)

internal fun rideRoute(leg: JourneyLeg): RouteLine =
    RouteLine(leg.path.takeIf { it.size >= 2 } ?: leg.stops.map { JourneyPoint(it.lat, it.lon) })

/** Each stop's metres along the ride, taken in order so a line passing near itself keeps them apart. */
internal fun stopsAlong(leg: JourneyLeg, route: RouteLine): List<Double> {
    var from = 0.0
    return leg.stops.map { stop -> (route.locate(stop.lat, stop.lon, from)?.along ?: from).also { from = it } }
}

/** The walk that ends nearest `lat`/`lon`, the way to it. */
internal fun walkRoute(journey: Journey, lat: Double, lon: Double): RouteLine? =
    journey.walks.filter { it.size >= 2 }.minByOrNull { haversineMeters(it.last().lat, it.last().lon, lat, lon) }?.let(::RouteLine)

/** m/s halfway between a walk and the leg's planned ride: faster is riding, slower is walking. */
internal fun ridePace(leg: JourneyLeg): Double? {
    if (leg.rideMinutes <= 0) return null
    return (WALK_SPEED / 60 + rideRoute(leg).length / (leg.rideMinutes * 60)) / 2
}

/** Further from the ride's line than standing at one of its stops. */
internal fun offLine(fix: Fix, leg: JourneyLeg): Boolean {
    val off = rideRoute(leg).locate(fix.lat, fix.lon)?.off ?: return false
    return off > max(AT_STOP_M, fix.accuracyMeters.toDouble())
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
