package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyPoint
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.RideTracker
import online.bus.bmarian.conexiuniclujmobile.journey.RouteLine
import online.bus.bmarian.conexiuniclujmobile.journey.ridePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class JourneyRideTest {
    private val lat = 46.77
    private val m = 1 / (111_320 * cos(Math.toRadians(lat)))
    private fun east(meters: Double, north: Double = 0.0) = JourneyPoint(lat + north / 110_540, 23.58 + meters * m)
    private fun fix(meters: Double, north: Double = 0.0, accuracy: Float = 10f) = east(meters, north).let { Fix(it.lat, it.lon, accuracy, null, null) }

    // Out east 1 km, then back west on the next street, 40 m north.
    private val path = listOf(east(0.0), east(1000.0), east(1000.0, 40.0), east(0.0, 40.0))
    private val leg = JourneyLeg(
        routeIds = listOf(1), tripIds = listOf("1_0"), lines = listOf("1"), colors = listOf("#22c55e"),
        stops = listOf(east(0.0), east(500.0), east(1000.0), east(1000.0, 40.0), east(500.0, 40.0), east(100.0, 40.0))
            .mapIndexed { i, p -> JourneyStop(i, "S$i", p.lat, p.lon) },
        rideMinutes = 8.0,
        path = path,
    )

    @Test
    fun aFixNearTheWayBackDoesNotSkipStops() {
        val ride = RideTracker(leg, 0)
        ride.update(fix(300.0), 20_000)
        // The way back passes right here, three stops later.
        ride.update(fix(300.0, 38.0), 25_000)
        assertEquals(0, ride.stop)
        assertTrue(ride.along < 400)
        assertFalse(ride.there)
    }

    @Test
    fun stopsAreCountedAsTheBusPassesThem() {
        val ride = RideTracker(leg, 0)
        var t = 0L
        fun at(meters: Double, north: Double = 0.0) {
            t += 15_000
            ride.update(fix(meters, north), t)
        }
        listOf(150.0, 300.0, 450.0, 600.0).forEach { at(it) }
        assertEquals(1, ride.stop)
        listOf(750.0, 900.0, 1000.0).forEach { at(it) }
        at(1000.0, 40.0)
        at(800.0, 40.0)
        assertEquals(3, ride.stop)
        assertFalse(ride.there)
        listOf(600.0, 400.0, 250.0, 100.0).forEach { at(it, 40.0) }
        assertEquals(4, ride.stop)
        assertTrue(ride.there)
    }

    @Test
    fun aFixFarFromTheLineIsLeftOut() {
        val ride = RideTracker(leg, 0)
        ride.update(fix(600.0, -200.0), 60_000)
        assertEquals(0, ride.stop)
    }

    @Test
    fun afterAGapItCatchesUp() {
        val ride = RideTracker(leg, 0)
        ride.update(fix(100.0), 10_000)
        ride.update(fix(900.0), 70_000)
        assertEquals(1, ride.stop)
    }

    @Test
    fun aLocateStaysWithinItsBounds() {
        val line = RouteLine(path)
        val p = east(300.0, 38.0)
        assertEquals(1740.0, line.locate(p.lat, p.lon)!!.along, 5.0)
        assertEquals(300.0, line.locate(p.lat, p.lon, 0.0, 500.0)!!.along, 5.0)
        assertEquals(500.0, line.locate(east(900.0).lat, east(900.0).lon, 0.0, 500.0)!!.along, 1.0)
    }

    @Test
    fun theRideIsCutFromTheLinesShape() {
        val stops = listOf(east(0.0), east(500.0), east(1000.0), east(500.0, 40.0))
        val cut = RouteLine(ridePath(path, stops, 1, 3))
        assertEquals(500.0 + 40.0 + 500.0, cut.length, 5.0)
        assertEquals(0.0, cut.locate(east(500.0).lat, east(500.0).lon)!!.along, 1.0)
    }
}
