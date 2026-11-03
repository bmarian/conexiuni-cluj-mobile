package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.NetworkTrip
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.boardingStop
import online.bus.bmarian.conexiuniclujmobile.logic.groupKey
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import org.junit.Assert.assertEquals
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
}
