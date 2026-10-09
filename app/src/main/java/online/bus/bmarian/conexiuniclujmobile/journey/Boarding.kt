package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters

internal const val AT_STOP_M = 40.0
internal const val LEFT_STOP_M = 150.0
internal const val ON_LINE_M = 300.0
// About 16 km/h.
private const val BUS_SPEED_MPS = 4.5f
private const val RIDING_FIXES = 2

internal fun distanceTo(fix: Fix, stop: JourneyStop) = haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon)

internal fun nearestStop(fix: Fix, leg: JourneyLeg): Int? {
    val (index, meters) = leg.stops.mapIndexed { i, s -> i to distanceTo(fix, s) }.minByOrNull { it.second } ?: return null
    return index.takeIf { meters <= ON_LINE_M }
}

internal fun leftOnBus(fix: Fix, leg: JourneyLeg): Boolean =
    distanceTo(fix, leg.boarding) > LEFT_STOP_M && (nearestStop(fix, leg) ?: 0) >= 1

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
