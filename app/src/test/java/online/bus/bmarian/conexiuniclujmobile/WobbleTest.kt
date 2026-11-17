package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.Wobble
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WobbleTest {
    private fun outline(seed: Int = 7, amplitude: Float = 2f) =
        Wobble.outline(120f, 40f, floatArrayOf(10f, 14f, 8f, 16f), amplitude, wavelength = 26f, step = 4f, seed = seed)

    @Test
    fun staysWithinTheAmplitudeOfTheBox() {
        val points = outline()
        for (i in points.indices step 2) {
            assertTrue(points[i] in -2f..122f)
            assertTrue(points[i + 1] in -2f..42f)
        }
    }

    @Test
    fun isTheSameEveryTimeForTheSameSeed() {
        assertArrayEquals(outline(), outline(), 0f)
    }

    @Test
    fun differsBetweenSeeds() {
        assertTrue(!outline(1).contentEquals(outline(2)))
    }

    @Test
    fun noAmplitudeIsTheRoundedRectangle() {
        val points = outline(amplitude = 0f)
        assertEquals(10f, points[0], 0.001f)
        assertEquals(0f, points[1], 0.001f)
    }

    @Test
    fun noiseRepeatsAroundTheLoop() {
        for (x in listOf(0.3f, 1.7f, 4.2f)) assertTrue(abs(Wobble.noise(x, 5, 3) - Wobble.noise(x + 5, 5, 3)) < 1e-5f)
    }

    @Test
    fun cornersNeverExceedHalfTheShortSide() {
        val points = Wobble.outline(40f, 20f, floatArrayOf(50f, 50f, 50f, 50f), 0f, 26f, 4f, 0)
        for (i in points.indices step 2) assertTrue(points[i + 1] in -0.001f..20.001f)
    }
}
