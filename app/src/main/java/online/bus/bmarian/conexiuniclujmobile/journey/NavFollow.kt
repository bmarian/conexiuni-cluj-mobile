package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.bearingDegrees
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.max

/** The line someone is following on a step, and how far along it they are. */
internal class Progress(val stepType: String?, val leg: Int, val line: RouteLine?, val along: Double)

/**
 * Where to draw the traveller: on the step's line while near it, heading along it and never sliding back.
 * Off it, the heading only turns once they moved further than standing at a stop, and while waiting at
 * the stop they stay put, facing the way the bus will go.
 */
internal class NavFollow(private val journey: Journey) {
    private var step: Pair<String?, Int>? = null
    private var line: RouteLine? = null
    private var along = 0.0
    private var shown: Fix? = null
    private var turnedAt: Fix? = null
    private var bearing: Float? = null
    private var onLineAt: Long? = null

    fun update(fix: Fix, stepType: String?, leg: Int, nowMs: Long = System.currentTimeMillis()): Pair<Fix, Progress> {
        if (step != stepType to leg) {
            step = stepType to leg
            line = lineFor(stepType, leg)
            along = 0.0
            turnedAt = null
            onLineAt = null
            if (stepType == StepType.WAIT) {
                journey.legs.getOrNull(leg)?.let { rideRoute(it).locate(it.boarding.lat, it.boarding.lon) }?.let { bearing = it.bearing.toFloat() }
            }
        }
        val error = fix.accuracyMeters.toDouble()
        val near = max(AT_STOP_M, error)
        // After the first fix on the line, only as far ahead as a bus could have gone since.
        val upTo = onLineAt?.let { along + reachSince(it, nowMs, error) } ?: Double.POSITIVE_INFINITY
        val spot = line?.locate(fix.lat, fix.lon, along - error, upTo)?.takeIf { it.off <= near }
        val last = shown
        val out = when {
            spot != null -> {
                val ahead = if (spot.along >= along) spot else line!!.locate(fix.lat, fix.lon, along, upTo) ?: spot
                along = max(along, ahead.along)
                onLineAt = nowMs
                bearing = ahead.bearing.toFloat()
                Fix(ahead.lat, ahead.lon, fix.accuracyMeters, bearing, fix.speed)
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
        return out to Progress(stepType, leg, line, along)
    }

    private fun lineFor(stepType: String?, leg: Int): RouteLine? = when (stepType) {
        StepType.WALK -> journey.legs.getOrNull(leg)?.boarding?.let { walkRoute(journey, it.lat, it.lon) }
        StepType.RIDE -> journey.legs.getOrNull(leg)?.let(::rideRoute)
        StepType.WALK_END -> walkRoute(journey, journey.destination.lat, journey.destination.lon)
        else -> null
    }
}
