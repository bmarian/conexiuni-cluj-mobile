package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.Motion
import online.bus.bmarian.conexiuniclujmobile.journey.leftOnBus
import online.bus.bmarian.conexiuniclujmobile.journey.offLine
import online.bus.bmarian.conexiuniclujmobile.journey.ridePace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class JourneyBoardingTest {
    // Made-up line going east, stops ~380 m apart, ridden in 4 minutes.
    private val leg = JourneyLeg(
        routeIds = listOf(1),
        tripIds = listOf("1_0"),
        lines = listOf("1"),
        colors = listOf("#22c55e"),
        stops = listOf(
            JourneyStop(1, "A", 46.7700, 23.5800),
            JourneyStop(2, "B", 46.7700, 23.5850),
            JourneyStop(3, "C", 46.7700, 23.5900),
            JourneyStop(4, "D", 46.7700, 23.5950),
        ),
        rideMinutes = 4.0,
    )
    private val pace = ridePace(leg)
    private val degPerMeter = 1 / (111_320 * cos(Math.toRadians(46.77)))

    private fun fix(lon: Double, lat: Double = 46.7702) = Fix(lat, lon, 10f, null, null)

    // A fix every 5 s eastwards from `lon` at `mps`; true where it says riding.
    private fun move(motion: Motion, lon: Double, mps: Double, fixes: Int): List<Boolean> = (0 until fixes).map { i ->
        val f = fix(lon + i * 5 * mps * degPerMeter)
        motion.update(f, i * 5_000L, pace)
        motion.riding(f, i * 5_000L, pace)
    }

    @Test
    fun paceIsHalfwayBetweenAWalkAndTheRide() {
        assertEquals((80.0 / 60 + 1144 / 240.0) / 2, pace!!, 0.05)
    }

    @Test
    fun standingNearTheLineIsNotRidingIt() {
        val start = fix(23.5848)
        assertTrue(leftOnBus(start, leg))
        val motion = Motion()
        repeat(10) { motion.update(start, it * 5_000L, pace) }
        assertFalse(motion.riding(start, 50_000, pace))
        assertFalse(motion.walking())
    }

    @Test
    fun walkingAlongTheLineIsWalking() {
        val motion = Motion()
        assertTrue(move(motion, 23.5880, 1.4, 12).none { it })
        assertTrue(motion.walking())
    }

    @Test
    fun movingAtBusSpeedAlongTheLineIsRiding() {
        assertEquals(listOf(false, false, true, true), move(Motion(), 23.5850, 8.0, 4))
    }

    @Test
    fun ridingNeedsNoSpeedFromTheFixes() {
        assertTrue(move(Motion(), 23.5850, 5.0, 6).last())
    }

    @Test
    fun aGpsJumpAndBackIsNotRiding() {
        val motion = Motion()
        val results = listOf(23.5850, 23.5865, 23.5850, 23.5850).mapIndexed { i, lon ->
            motion.update(fix(lon), i * 5_000L, pace)
            motion.riding(fix(lon), i * 5_000L, pace)
        }
        assertEquals(listOf(false, false, false, false), results)
    }

    @Test
    fun walkingAwayFromTheLineIsOffIt() {
        assertFalse(offLine(fix(23.5875), leg))
        assertTrue(offLine(fix(23.5875, 46.7700 + 100 / 110_540.0), leg))
        assertFalse(offLine(Fix(46.7700 + 100 / 110_540.0, 23.5875, 150f, null, null), leg))
    }
}
