package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters

// Close enough to count as at the stop, and far enough from it to count as on the bus.
internal const val AT_STOP_M = 40.0
internal const val LEFT_STOP_M = 150.0
// How far from the line's stops a position can be and still say which stop the bus is at.
internal const val ON_LINE_M = 300.0
// Faster than anyone walks: about 16 km/h.
private const val BUS_SPEED_MPS = 4.5f
// Fixes in a row at that speed along the line before it counts as riding: one can be a GPS jump.
private const val RIDING_FIXES = 2

internal fun distanceTo(fix: Fix, stop: JourneyStop) = haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon)

/** The stop of [leg] you are at or passing, if you are anywhere near its line. */
internal fun nearestStop(fix: Fix, leg: JourneyLeg): Int? {
    val (index, meters) = leg.stops.mapIndexed { i, s -> i to distanceTo(fix, s) }.minByOrNull { it.second } ?: return null
    return index.takeIf { meters <= ON_LINE_M }
}

/** Waited at the stop and now away from it, at a later stop of the line: the bus took you. */
internal fun leftOnBus(fix: Fix, leg: JourneyLeg): Boolean =
    distanceTo(fix, leg.boarding) > LEFT_STOP_M && (nearestStop(fix, leg) ?: 0) >= 1

/**
 * Whether you got on without the stop ever showing it (a GPS that never quite put you there):
 * moving at a bus's speed along the line past the stop to board at, for a few fixes in a row.
 * Only being near the line says nothing: the trip may start beside it, or walk along it to the stop.
 */
internal class RidingCheck {
    private var inARow = 0

    fun onBus(fix: Fix, leg: JourneyLeg): Boolean {
        val speed = fix.speed
        val moving = speed != null && speed >= BUS_SPEED_MPS && leftOnBus(fix, leg)
        inARow = if (moving) inARow + 1 else 0
        return inARow >= RIDING_FIXES
    }

    fun reset() {
        inARow = 0
    }
}
