package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyPoint
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.NavFollow
import online.bus.bmarian.conexiuniclujmobile.journey.RouteLine
import online.bus.bmarian.conexiuniclujmobile.journey.StepType
import online.bus.bmarian.conexiuniclujmobile.journey.rideRoute
import online.bus.bmarian.conexiuniclujmobile.journey.stopsAlong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class JourneyFollowTest {
    private val lat = 46.77
    private val m = 1 / (111_320 * cos(Math.toRadians(lat)))
    private fun east(meters: Double, north: Double = 0.0) = JourneyPoint(lat + north / 110_540, 23.58 + meters * m)

    // East 1 km, then north 1 km.
    private val corner = RouteLine(listOf(east(0.0), east(1000.0), east(1000.0, 1000.0)))

    @Test
    fun locatesAlongAndOffTheLine() {
        val p = east(400.0, 30.0)
        val spot = corner.locate(p.lat, p.lon)!!
        assertEquals(400.0, spot.along, 2.0)
        assertEquals(30.0, spot.off, 1.0)
        assertEquals(90.0, spot.bearing, 1.0)
        val up = east(1010.0, 500.0)
        assertEquals(1500.0, corner.locate(up.lat, up.lon)!!.along, 2.0)
        assertEquals(0.0, corner.locate(up.lat, up.lon)!!.bearing, 1.0)
    }

    @Test
    fun aLineBackOnItselfKeepsItsOrder() {
        // Out east and back west on the same street.
        val there = RouteLine(listOf(east(0.0), east(1000.0), east(0.0, 40.0)))
        val p = east(300.0, 10.0)
        assertEquals(300.0, there.locate(p.lat, p.lon)!!.along, 2.0)
        assertEquals(1700.0, there.locate(p.lat, p.lon, from = 1200.0)!!.along, 5.0)
    }

    @Test
    fun whatIsLeftStartsWhereYouAre() {
        val left = corner.after(600.0)
        assertEquals(3, left.size)
        assertEquals(600.0, corner.locate(left[0].lat, left[0].lon)!!.along, 1.0)
        assertTrue(corner.after(5000.0).isEmpty())
    }

    @Test
    fun stopsAreCountedAlongTheRide() {
        val leg = JourneyLeg(
            routeIds = listOf(1), tripIds = listOf("1_0"), lines = listOf("1"), colors = listOf("#22c55e"),
            stops = listOf(east(0.0), east(1000.0), east(1000.0, 700.0)).mapIndexed { i, p -> JourneyStop(i, "S$i", p.lat, p.lon) },
            rideMinutes = 6.0,
            path = listOf(east(0.0), east(1000.0), east(1000.0, 1000.0)),
        )
        val along = stopsAlong(leg, rideRoute(leg))
        listOf(0.0, 1000.0, 1700.0).zip(along).forEach { (want, got) -> assertEquals(want, got, 5.0) }
    }

    private val leg = JourneyLeg(
        routeIds = listOf(1), tripIds = listOf("1_0"), lines = listOf("1"), colors = listOf("#22c55e"),
        stops = listOf(JourneyStop(1, "A", east(0.0).lat, east(0.0).lon), JourneyStop(2, "B", east(1000.0).lat, east(1000.0).lon)),
        rideMinutes = 4.0,
        path = listOf(east(0.0), east(1000.0)),
    )
    private val journey = Journey("j", JourneyStop(-1, "Home", east(1200.0).lat, east(1200.0).lon), listOf(leg))

    private fun fix(meters: Double, north: Double = 0.0, accuracy: Float = 10f) = east(meters, north).let { Fix(it.lat, it.lon, accuracy, null, null) }

    @Test
    fun onTheRideItSitsOnTheLineFacingAlongIt() {
        val follow = NavFollow(journey)
        val (shown) = follow.update(fix(300.0, 15.0), StepType.RIDE, 0)
        assertEquals(0.0, rideRoute(leg).locate(shown.lat, shown.lon)!!.off, 0.5)
        assertEquals(90f, shown.bearing!!, 1f)
    }

    @Test
    fun onTheRideItNeverSlidesBack() {
        val follow = NavFollow(journey)
        follow.update(fix(300.0), StepType.RIDE, 0)
        val (shown, progress) = follow.update(fix(292.0), StepType.RIDE, 0)
        assertEquals(300.0, progress.along, 0.5)
        assertEquals(300.0, rideRoute(leg).locate(shown.lat, shown.lon)!!.along, 0.5)
    }

    @Test
    fun standingStillStaysStill() {
        val follow = NavFollow(journey)
        val (first) = follow.update(fix(0.0, -60.0), StepType.WAIT, 0)
        val (second) = follow.update(fix(4.0, -63.0), StepType.WAIT, 0)
        assertEquals(first.lat, second.lat, 0.0)
        assertEquals(first.lon, second.lon, 0.0)
        assertEquals(90f, second.bearing!!, 1f)
    }

    @Test
    fun offTheLineItTurnsOnlyWhenItTrulyMoved() {
        val follow = NavFollow(journey)
        follow.update(fix(0.0, -200.0), StepType.WALK, 0)
        val (shown) = follow.update(fix(0.0, -150.0), StepType.WALK, 0)
        assertEquals(0f, shown.bearing!!, 1f)
        assertNull(NavFollow(journey).update(fix(0.0, -200.0), StepType.WALK, 0).first.bearing)
    }
}
