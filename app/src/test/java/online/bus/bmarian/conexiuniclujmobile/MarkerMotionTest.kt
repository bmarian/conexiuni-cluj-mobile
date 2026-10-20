package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.map.Snappy
import online.bus.bmarian.conexiuniclujmobile.ui.map.flightMs
import online.bus.bmarian.conexiuniclujmobile.ui.map.pulseScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Port of utils/__tests__/markerMotion.spec.ts.
class MarkerMotionTest {
    @Test
    fun easesOutAndEndsExactlyAtTheTarget() {
        assertEquals(0f, Snappy.transform(0f), 1e-4f)
        assertEquals(1f, Snappy.transform(1f), 1e-4f)
        assertTrue(Snappy.transform(0.5f) > 0.5f)
    }

    @Test
    fun pulsesUpAndBack() {
        assertEquals(1f, pulseScale(0f), 1e-4f)
        assertEquals(1.18f, pulseScale(0.4f), 1e-4f)
        assertEquals(1f, pulseScale(1f), 1e-4f)
    }

    @Test
    fun fliesShortHopsQuicklyAndCapsLongOnes() {
        assertEquals(350, flightMs(0.0, 0.0))
        assertEquals(660, flightMs(0.0, 2.0))
        assertEquals(1100, flightMs(4000.0, 3.0))
        assertTrue(flightMs(800.0, 1.0) > flightMs(200.0, 1.0))
    }
}
