package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.RidingCheck
import online.bus.bmarian.conexiuniclujmobile.journey.leftOnBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyBoardingTest {
    // Made-up line going east, stops ~380 m apart.
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
    )

    private fun fix(lon: Double, speed: Float?) = Fix(46.7702, lon, 10f, null, speed)

    @Test
    fun standingNearTheLineIsNotRidingIt() {
        // Near the second stop before the walk began.
        val start = fix(23.5848, null)
        assertTrue(leftOnBus(start, leg))
        val riding = RidingCheck()
        repeat(10) { assertFalse(riding.onBus(start, leg)) }
        repeat(10) { assertFalse(riding.onBus(fix(23.5848, 0f), leg)) }
    }

    @Test
    fun walkingAlongTheLineIsNotRidingIt() {
        val riding = RidingCheck()
        listOf(23.5880, 23.5881, 23.5882, 23.5883, 23.5884).forEach { lon ->
            assertFalse(riding.onBus(fix(lon, 1.4f), leg))
        }
    }

    @Test
    fun movingAtBusSpeedAlongTheLineIsRiding() {
        val riding = RidingCheck()
        assertFalse(riding.onBus(fix(23.5850, 8f), leg))
        assertTrue(riding.onBus(fix(23.5862, 8f), leg))
    }

    @Test
    fun oneGpsJumpIsNotEnough() {
        val riding = RidingCheck()
        val results = listOf(8f, 0.5f, 8f, 0.5f).mapIndexed { i, speed -> riding.onBus(fix(23.5850 + i * 0.0002, speed), leg) }
        assertEquals(listOf(false, false, false, false), results)
    }

    @Test
    fun withoutASpeedPositionAloneNeverSaysRiding() {
        val riding = RidingCheck()
        listOf(23.5850, 23.5870, 23.5890, 23.5910).forEach { lon -> assertFalse(riding.onBus(fix(lon, null), leg)) }
    }

    @Test
    fun atTheStopToBoardAtIsNotRiding() {
        val riding = RidingCheck()
        repeat(3) { assertFalse(riding.onBus(fix(23.5801, 9f), leg)) }
    }
}
