package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.NetworkTrip
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.StopRole
import online.bus.bmarian.conexiuniclujmobile.logic.StretchEnd
import online.bus.bmarian.conexiuniclujmobile.logic.boardingStop
import online.bus.bmarian.conexiuniclujmobile.logic.flipLine
import online.bus.bmarian.conexiuniclujmobile.logic.followedPart
import online.bus.bmarian.conexiuniclujmobile.logic.groupKey
import online.bus.bmarian.conexiuniclujmobile.logic.passedBy
import online.bus.bmarian.conexiuniclujmobile.logic.pickStop
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.stopRows
import online.bus.bmarian.conexiuniclujmobile.logic.stretchNames
import online.bus.bmarian.conexiuniclujmobile.logic.stretchSpan
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as utils/__tests__/lineGroups.spec.ts. */
class LineGroupsTest {
    private fun stop(id: Int) = Stop(id, "S$id", 46.77 + id / 1000.0, 23.6, stationId = id)

    private fun route(id: Int, slug: String, trips: Map<String, List<Int>>) = NetworkRoute(
        routeId = id,
        shortName = slug.uppercase(),
        longName = "$slug start - $slug end",
        slug = slug,
        trips = trips.map { (tripId, stops) -> NetworkTrip(tripId, stops.toIntArray(), IntArray(stops.size) { it }) },
    )

    private val index = NetworkIndex(
        NetworkBundle(
            stops = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13).map { stop(it) },
            routes = listOf(
                route(1, "a", mapOf("1_0" to listOf(6, 5, 4, 3, 2, 1), "1_1" to listOf(1, 2, 3, 4, 5, 6))),
                route(2, "b", mapOf("2_1" to listOf(7, 4, 8, 9))),
                route(4, "d", mapOf("4_0" to listOf(12, 13))),
            ),
        ),
    )

    @Test
    fun `reads back what it writes, once per line`() {
        val lines = listOf(GroupLine(1, "1"), GroupLine(2, "1"), GroupLine(4, "0"))
        assertEquals("a.intors,b.intors,d", index.writeLines(lines))
        assertEquals(lines, index.readLines("a.intors,b.intors,d,a,zz"))
        assertEquals("1/1,2/1,4/0", groupKey(lines))
    }

    @Test
    fun `boards at the stop nearest you, never at the last one, and at the first without a location`() {
        val a = GroupLine(1, "1")
        assertEquals(3, index.boardingStop(a, 46.773, 23.6))
        assertEquals(5, index.boardingStop(a, 46.776, 23.6))
        assertEquals(1, index.boardingStop(a, null, null))
    }

    private val a = GroupLine(1, "1")
    private val b = GroupLine(2, "1")

    @Test
    fun `boards at the chosen stop, and without one before the get-off stop`() {
        assertEquals(4, index.boardingStop(a.copy(from = 4), 46.771, 23.6))
        assertEquals(2, index.boardingStop(a.copy(to = 3), 46.776, 23.6))
    }

    @Test
    fun `reads back the stops a line is followed between`() {
        val lines = listOf(a.copy(from = 2, to = 5), b.copy(to = 8), GroupLine(4, "0"))
        assertEquals("a.intors:2:5,b.intors::8,d", index.writeLines(lines))
        assertEquals(lines, index.readLines("a.intors:2:5,b.intors::8,d"))
        assertEquals("1/1/2/5,2/1//8,4/0", groupKey(lines))
        assertEquals("S2" to "S5", index.stretchNames(lines[0]))
    }

    @Test
    fun `drops a stop the line does not serve, one out of order, the last to board at and the first to get off at`() {
        assertEquals(listOf(a.copy(to = 5)), index.readLines("a.intors:7:5"))
        assertEquals(listOf(a.copy(from = 5)), index.readLines("a.intors:5:2"))
        assertEquals(listOf(a), index.readLines("a.intors:6"))
        assertEquals(listOf(a), index.readLines("a.intors::1"))
    }

    @Test
    fun `flips between the same stations, and to the whole line where there is no way back`() {
        assertEquals(GroupLine(1, "0", 5, 2), index.flipLine(a.copy(from = 2, to = 5)))
        assertEquals(GroupLine(2, "0"), index.flipLine(b.copy(from = 4, to = 8)))
    }

    @Test
    fun `takes a tap for the end being chosen`() {
        val chosen = a.copy(from = 3, to = 5)
        assertEquals(a.copy(from = 3) to StretchEnd.To, index.pickStop(a, StretchEnd.From, 3))
        assertEquals(chosen to StretchEnd.To, index.pickStop(a.copy(from = 3), StretchEnd.To, 5))
        assertEquals(a.copy(from = 2) to StretchEnd.To, index.pickStop(chosen, StretchEnd.To, 2))
        assertEquals(a.copy(from = 3) to StretchEnd.To, index.pickStop(chosen, StretchEnd.To, 6))
        assertEquals(a.copy(to = 5) to StretchEnd.From, index.pickStop(chosen, StretchEnd.From, 3))
        assertEquals(a.copy(from = 5) to StretchEnd.To, index.pickStop(chosen, StretchEnd.From, 5))
        assertEquals(a to StretchEnd.From, index.pickStop(a, StretchEnd.From, 6))
        assertEquals(a to StretchEnd.To, index.pickStop(a, StretchEnd.To, 1))
    }

    @Test
    fun `paints the list between the two stops and dims the rest`() {
        val rows = index.stopRows(a.copy(from = 2, to = 4), null)
        assertEquals(listOf(StopRole.Pass, StopRole.Board, StopRole.Pass, StopRole.Leave, StopRole.Pass, StopRole.Pass), rows.map { it.role })
        assertEquals(listOf(true, false, false, false, true, true), rows.map { it.dimmed })
        assertEquals(false to true, rows[1].paintedTop to rows[1].paintedBottom)
        assertEquals(true to false, rows[3].paintedTop to rows[3].paintedBottom)
        assertEquals(StopRole.Near, index.stopRows(a, 3)[2].role)
    }

    private val shape = (1..6).map { ShapePoint("1_1", 46.77 + it / 1000.0, 23.6, it) }

    @Test
    fun `has no part to cut for a line followed whole`() {
        assertNull(index.stretchSpan(a, shape))
    }

    @Test
    fun `cuts the shape at the two stops and tells the buses past the second`() {
        val line = a.copy(from = 2, to = 5)
        assertEquals(listOf(46.772, 46.773, 46.774, 46.775), index.followedPart(line, shape)!!.map { Math.round(it.lat * 1000) / 1000.0 })
        val span = index.stretchSpan(line, shape)!!
        assertTrue(span.passedBy(Vehicle(latitude = 46.776, longitude = 23.6)))
        assertFalse(span.passedBy(Vehicle(latitude = 46.771, longitude = 23.6)))
    }
}
