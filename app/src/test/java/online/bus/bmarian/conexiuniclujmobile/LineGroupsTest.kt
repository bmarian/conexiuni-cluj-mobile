package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.NetworkTrip
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.Departure
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.Leg
import online.bus.bmarian.conexiuniclujmobile.logic.LegLine
import online.bus.bmarian.conexiuniclujmobile.logic.groupKey
import online.bus.bmarian.conexiuniclujmobile.logic.legsNear
import online.bus.bmarian.conexiuniclujmobile.logic.meetingLines
import online.bus.bmarian.conexiuniclujmobile.logic.planLegs
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.timeLegs
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as utils/__tests__/lineGroups.spec.ts. */
class LineGroupsTest {
    private fun stop(id: Int, station: Int = id) = Stop(id, "S$id", 46.77 + id / 1000.0, 23.6, stationId = station)

    private fun route(id: Int, slug: String, trips: Map<String, List<Int>>) = NetworkRoute(
        routeId = id,
        shortName = slug.uppercase(),
        longName = "$slug start - $slug end",
        slug = slug,
        trips = trips.map { (tripId, stops) -> NetworkTrip(tripId, stops.toIntArray(), IntArray(stops.size) { it }) },
    )

    // A rides 1→6 through station 4; B and C leave 4 for 8; F leaves from 14, another platform of 4; D meets nothing.
    private val index = NetworkIndex(
        NetworkBundle(
            stops = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15).map { stop(it) } + stop(14, 4),
            routes = listOf(
                route(1, "a", mapOf("1_0" to listOf(6, 5, 4, 3, 2, 1), "1_1" to listOf(1, 2, 3, 4, 5, 6))),
                route(2, "b", mapOf("2_1" to listOf(7, 4, 8, 9))),
                route(3, "c", mapOf("3_1" to listOf(10, 4, 8, 11))),
                route(4, "d", mapOf("4_0" to listOf(12, 13))),
                route(5, "e", mapOf("5_0" to listOf(3, 4, 9))),
                route(6, "f", mapOf("6_0" to listOf(14, 15))),
            ),
        ),
    )

    private val at2 = { stops: List<Int>, _: GroupLine -> if (2 in stops) 2 else stops.first() }
    private val group = listOf(GroupLine(1, "1"), GroupLine(2, "1"), GroupLine(3, "1"))

    @Test
    fun `reads back what it writes, once per line`() {
        val lines = listOf(GroupLine(1, "1"), GroupLine(2, "1"), GroupLine(4, "0"))
        assertEquals("a.intors,b.intors,d", index.writeLines(lines))
        assertEquals(lines, index.readLines("a.intors,b.intors,d,a,zz"))
        assertEquals("1/1,2/1,4/0", groupKey(lines))
    }

    @Test
    fun `changes where the next line meets the one before, and takes lines leaving together as one leg`() {
        assertEquals(
            listOf(
                Leg(listOf(LegLine(1, "1", 2))),
                Leg(listOf(LegLine(2, "1", 4), LegLine(3, "1", 4)), listOf(LegLine(1, "1", 4))),
            ),
            index.planLegs(group, at2),
        )
    }

    @Test
    fun `changes where a line riding along leaves the way, as the picker names it`() {
        val legs = index.planLegs(listOf(GroupLine(1, "1"), GroupLine(5, "0")), at2)
        assertEquals(Leg(listOf(LegLine(5, "0", 4)), listOf(LegLine(1, "1", 4))), legs[1])
    }

    @Test
    fun `keeps the change when you are past it, boarding at your nearest stop before it`() {
        assertEquals(
            listOf(Leg(listOf(LegLine(1, "1", 3))), Leg(listOf(LegLine(2, "1", 4)), listOf(LegLine(1, "1", 4)))),
            index.legsNear(listOf(GroupLine(1, "1"), GroupLine(2, "1")), 46.775, 23.6),
        )
    }

    @Test
    fun `walks to another platform of the same station`() {
        val legs = index.planLegs(listOf(GroupLine(1, "1"), GroupLine(6, "0")), at2)
        assertEquals(Leg(listOf(LegLine(6, "0", 14)), listOf(LegLine(1, "1", 4))), legs[1])
    }

    @Test
    fun `keeps a line through the same stop going elsewhere, and one that never meets, on their own`() {
        val legs = index.planLegs(listOf(GroupLine(2, "1"), GroupLine(5, "0"), GroupLine(4, "0"))) { stops, _ -> if (4 in stops) 4 else stops.first() }
        assertEquals(
            listOf(Leg(listOf(LegLine(2, "1", 4))), Leg(listOf(LegLine(5, "0", 4))), Leg(listOf(LegLine(4, "0", 12)))),
            legs,
        )
    }

    @Test
    fun `lists each line once, where it leaves the way after boarding`() {
        assertEquals(
            listOf(LegLine(2, "1", 4) to 2, LegLine(3, "1", 4) to 2, LegLine(5, "0", 4) to 2, LegLine(6, "0", 14) to 2),
            index.meetingLines(LegLine(1, "1", 2)),
        )
        assertEquals(listOf(2, 3, 6), index.meetingLines(LegLine(1, "1", 3)).map { it.first.routeId })
    }

    private val min = 60_000L
    private val buses = mapOf(
        "1@2" to listOf(Departure(1, 3 * min, true, 7), Departure(1, 14 * min, false)),
        "1@4" to listOf(Departure(1, 2 * min, true, 6), Departure(1, 11 * min, true, 7), Departure(1, 22 * min, false)),
        "2@4" to listOf(Departure(2, 13 * min, true), Departure(2, 28 * min, false)),
        "3@4" to listOf(Departure(3, 6 * min, true), Departure(3, 10 * min, false), Departure(3, 21 * min, false), Departure(3, 35 * min, false)),
    )
    private val at = { l: LegLine -> buses["${l.routeId}@${l.stopId}"].orEmpty() }

    @Test
    fun `counts the change from the bus you take, found by its vehicle, and strikes the last one you miss`() {
        val (start, next) = timeLegs(index.planLegs(group, at2), at, at, { _, _ -> 0L }, 3)
        assertEquals(3 * min, start.caught?.at)
        assertEquals(11 * min, next.arrival)
        assertEquals(10 * min, next.missed?.at)
        assertEquals(13 * min, next.caught?.at)
        assertEquals(listOf(13, 21, 28).map { it * min }, next.shown.map { it.at })
    }

    @Test
    fun `takes the next bus to arrive when the vehicle is not known, and adds the walk`() {
        val unknown = { l: LegLine -> at(l).map { it.copy(vehicleId = null) } }
        val next = timeLegs(index.planLegs(group, at2), unknown, unknown, { _, _ -> 3 * min }, 3)[1]
        assertEquals(11 * min, next.arrival)
        assertEquals(21 * min, next.caught?.at)
    }

    @Test
    fun `shows no change once nothing more reaches it`() {
        val none = { l: LegLine -> if (l.stopId == 4 && l.routeId == 1) emptyList() else at(l) }
        val next = timeLegs(index.planLegs(group, at2), none, none, { _, _ -> 0L }, 3)[1]
        assertNull(next.arrival)
        assertNull(next.caught)
    }
}
