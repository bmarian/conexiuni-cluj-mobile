package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.ACCEL
import online.bus.bmarian.conexiuniclujmobile.logic.bearingDegrees
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.max

/** The line someone is following on a step, and how far along it they are. */
internal class Progress(val stepType: String?, val leg: Int, val line: RouteLine?, val along: Double, val ahead: List<Fix> = emptyList(), val aheadMs: Long = 0)

/** Snapped to the step's line while near it, sliding back only on foot; waiting at the stop, facing the way the bus goes. */
internal class NavFollow(private val journey: Journey) {
    private var step: Pair<String?, Int>? = null
    private var line: RouteLine? = null
    private var along = 0.0
    private var shown: Fix? = null
    private var turnedAt: Fix? = null
    private var bearing: Float? = null
    private var onLineAt: Long? = null
    private var glide: NavGlide? = null
    private var shownAlong = 0.0

    // Timed by the fix: the first after a step change can be older than it.
    fun update(fix: Fix, stepType: String?, leg: Int): Pair<Fix, Progress> {
        if (step != stepType to leg) {
            step = stepType to leg
            line = lineFor(stepType, leg)
            along = 0.0
            turnedAt = null
            onLineAt = null
            glide = null
            shownAlong = 0.0
            if (stepType == StepType.WAIT) {
                journey.legs.getOrNull(leg)?.let { rideRoute(it).locate(it.boarding.lat, it.boarding.lon) }?.let { bearing = it.bearing.toFloat() }
            }
        }
        val error = fix.accuracyMeters.toDouble()
        val near = max(AT_STOP_M, error)
        // After the first fix on the line, only as far ahead as a bus could have gone since.
        val upTo = onLineAt?.let { along + reachSince(it, fix.atMs, error) } ?: Double.POSITIVE_INFINITY
        var spot = line?.locate(fix.lat, fix.lon, along - error, upTo)?.takeIf { it.off <= near }
        // On foot the way can be walked back.
        if (spot == null && stepType != StepType.RIDE) {
            spot = line?.locate(fix.lat, fix.lon, 0.0, along)?.takeIf { it.off <= near }
            if (spot != null) {
                along = spot.along
                glide = null
            }
        }
        val last = shown
        var aheadOn = emptyList<Fix>()
        var aheadMs = 0L
        val out = when {
            spot != null -> {
                val ahead = if (spot.along >= along) spot else line!!.locate(fix.lat, fix.lon, along, upTo) ?: spot
                along = max(along, ahead.along)
                onLineAt = fix.atMs
                val on = line!!
                val smooth = glide ?: NavGlide(on.length, if (stepType == StepType.RIDE) ACCEL else WALK_ACCEL).also { glide = it }
                val moving = smooth.update(spot.along, error, fix.atMs)
                shownAlong = moving.along
                bearing = on.headingAt(moving.along).toFloat()
                aheadOn = moving.keys.map { on.fixAt(it, fix) }
                aheadMs = moving.ms
                on.fixAt(moving.along, fix)
            }
            stepType == StepType.WAIT && last != null && haversineMeters(last.lat, last.lon, fix.lat, fix.lon) <= near ->
                Fix(last.lat, last.lon, fix.accuracyMeters, bearing, fix.speed)
            else -> {
                val from = turnedAt
                if (from == null) {
                    turnedAt = fix
                } else if (haversineMeters(from.lat, from.lon, fix.lat, fix.lon) > near) {
                    bearing = bearingDegrees(from.lat, from.lon, fix.lat, fix.lon).toFloat()
                    turnedAt = fix
                }
                Fix(fix.lat, fix.lon, fix.accuracyMeters, bearing, fix.speed)
            }
        }
        shown = out
        return out to Progress(stepType, leg, line, shownAlong, aheadOn, aheadMs)
    }

    private fun RouteLine.fixAt(along: Double, fix: Fix) =
        pointAt(along).let { Fix(it.lat, it.lon, fix.accuracyMeters, headingAt(along).toFloat(), fix.speed, fix.atMs) }

    private fun lineFor(stepType: String?, leg: Int): RouteLine? = when (stepType) {
        StepType.WALK -> journey.legs.getOrNull(leg)?.boarding?.let { walkRoute(journey, it.lat, it.lon) }
        StepType.RIDE -> journey.legs.getOrNull(leg)?.let(::rideRoute)
        StepType.WALK_END -> walkRoute(journey, journey.destination.lat, journey.destination.lon)
        else -> null
    }
}
