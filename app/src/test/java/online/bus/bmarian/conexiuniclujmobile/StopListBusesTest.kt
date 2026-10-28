package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import online.bus.bmarian.conexiuniclujmobile.logic.Arrival
import online.bus.bmarian.conexiuniclujmobile.logic.DueArrivals
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedStop
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.directionKeys
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.logic.partBetween
import online.bus.bmarian.conexiuniclujmobile.logic.pathThrough
import online.bus.bmarian.conexiuniclujmobile.logic.rowAlong
import online.bus.bmarian.conexiuniclujmobile.logic.stopsAlong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Port of utils/__tests__/stopListBuses.spec.ts, plus the other ports the animations use.
class StopListBusesTest {
    private val mPerLon = haversineMeters(46.77, 0.0, 46.77, 1.0)
    private fun lonAt(m: Double) = 23.55 + m / mPerLon
    private val path = pathThrough(doubleArrayOf(46.77, 46.77), doubleArrayOf(lonAt(0.0), lonAt(3000.0)))
    private fun stop(id: Int, m: Double, north: Double = 0.0) = StopTime("t", id, lat = 46.77 + north / 111_000.0, lon = lonAt(m))
    private val stops = stopsAlong(path, listOf(0.0, 1000.0, 2000.0, 3000.0).mapIndexed { i, m -> IndexedStop(stop(i, m), 0.0) })

    @Test
    fun aPointSitsBetweenTheStopsItLiesBetween() {
        assertEquals(1.5f, rowAlong(stops, 1500.0), 0.01f)
        assertEquals(2f, rowAlong(stops, 2000.0), 0.01f)
    }

    @Test
    fun aPointBeforeTheFirstStopOrPastTheLastStaysOnTheList() {
        assertEquals(0f, rowAlong(stops, -20.0))
        assertEquals(3f, rowAlong(stops, 3200.0))
    }

    @Test
    fun aStopOffThePathIsSkipped() {
        assertEquals(1f, rowAlong(listOf(0.0, null, 2000.0), 1000.0), 1e-5f)
    }

    @Test
    fun theStopAcrossTheStreetKeepsItsKeyAndOthersGetTheirOwn() {
        val there = listOf(stop(1, 0.0), stop(2, 500.0), stop(3, 1000.0))
        val back = listOf(stop(13, 1000.0, north = 30.0), stop(14, 700.0, north = 30.0), stop(11, 0.0, north = 30.0))
        val (thereKeys, backKeys) = directionKeys(there, back) { it.stopId }
        assertEquals(thereKeys[2], backKeys[0])
        assertEquals(thereKeys[0], backKeys[2])
        assertTrue(backKeys[1] !in thereKeys)
    }

    @Test
    fun onlyABusThatCountsDownToNowIsNewlyDue() {
        fun departure(minutes: Int) = StopDeparture(1, "35", "#1D71B8", "29_0", "", listOf(Arrival(minutes, true, 7)), minutes, false)
        val due = DueArrivals()
        assertTrue(due.newlyDue(listOf(departure(0))).isEmpty())
        assertTrue(due.newlyDue(listOf(departure(2))).isEmpty())
        assertEquals(1, due.newlyDue(listOf(departure(0))).size)
        assertTrue(due.newlyDue(listOf(departure(0))).isEmpty())
    }

    @Test
    fun aPartOfALineRunsBetweenItsShares() {
        val line = listOf(LatLon(46.77, lonAt(0.0)), LatLon(46.77, lonAt(1000.0)), LatLon(46.77, lonAt(2000.0)))
        val part = partBetween(line, 0.25f, 0.75f)
        assertEquals(500.0, haversineMeters(line[0].lat, line[0].lon, part.first().lat, part.first().lon), 1.0)
        assertEquals(1500.0, haversineMeters(line[0].lat, line[0].lon, part.last().lat, part.last().lon), 1.0)
        assertEquals(3, part.size)
        assertTrue(partBetween(line, 0.5f, 0.5f).isEmpty())
    }
}
