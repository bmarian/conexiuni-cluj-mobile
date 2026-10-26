package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.route.ARROW_MS
import online.bus.bmarian.conexiuniclujmobile.ui.route.arrowPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

// Port of utils/__tests__/directionArrow.spec.ts.
class DirectionArrowTest {
    private fun turned(a: Double) = (a % 360 + 360) % 360

    @Test
    fun startsOnItsSpotFacingTheNameAndLandsThereFacingTheHeading() {
        for ((rest, heading) in listOf(90.0 to 292.0, 270.0 to 112.0, 90.0 to 90.0, 270.0 to 271.0, 90.0 to 0.0)) {
            val start = arrowPose(rest, heading, 0f)
            assertEquals(0f, hypot(start.x, start.y), 0f)
            assertEquals(rest, start.heading, 0.0)
            val end = arrowPose(rest, heading, ARROW_MS.toFloat())
            assertEquals(0f, hypot(end.x, end.y), 1e-5f)
            assertEquals(turned(heading), turned(end.heading), 1e-6)
        }
    }

    @Test
    fun tapsTowardsTheNameTwiceBackOnItsSpotInBetween() {
        assertTrue(arrowPose(270.0, 112.0, 61f).x < -4)
        assertTrue(abs(arrowPose(270.0, 112.0, 209.9f).x) < 0.01)
        assertTrue(arrowPose(270.0, 112.0, 270f).x < -4)
        assertTrue(arrowPose(90.0, 292.0, 61f).x > 4)
    }

    @Test
    fun circlesRoundItsSpotWithoutSpinningBetweenFrames() {
        for (rest in listOf(90.0, 270.0)) {
            val frames = (0..ARROW_MS step 16).map { arrowPose(rest, 112.0, it.toFloat()) }
            assertTrue(frames.minOf { it.x } < -3 && frames.maxOf { it.x } > 3)
            assertTrue(frames.minOf { it.y } < -3 && frames.maxOf { it.y } > 3)
            frames.zipWithNext().forEach { (a, b) -> assertTrue(abs(b.heading - a.heading) < 30) }
        }
    }
}
