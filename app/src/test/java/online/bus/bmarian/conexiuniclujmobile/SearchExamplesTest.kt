package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.DaySchedule
import online.bus.bmarian.conexiuniclujmobile.data.DestinationSummary
import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.NetworkTrip
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import online.bus.bmarian.conexiuniclujmobile.data.TimetableEntry
import online.bus.bmarian.conexiuniclujmobile.logic.searchExamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as utils/__tests__/searchExamples.spec.ts. */
class SearchExamplesTest {
    private fun stop(id: Int) = Stop(id, "S$id", 46.77, 23.6, stationId = id)

    private fun route(id: Int, weekdayDepartures: Int, stops: List<Int>) = NetworkRoute(
        routeId = id,
        shortName = "L$id",
        timetable = Timetable(weekdays = DaySchedule(entries = List(weekdayDepartures) { TimetableEntry() })),
        trips = listOf(NetworkTrip("${id}_0", stops.toIntArray(), IntArray(stops.size) { it })),
    )

    private val index = NetworkIndex(
        NetworkBundle(
            stops = listOf(1, 2, 3, 4).map(::stop),
            routes = listOf(route(1, 40, listOf(1, 2)), route(2, 90, listOf(2, 3)), route(3, 10, listOf(2, 4))),
        ),
    )

    private fun area(name: String, stops: Int, district: Boolean = true) = DestinationSummary(name, name, district, stops, 1)

    @Test
    fun picksTheBusiestLineStationAndNeighbourhood() {
        val examples = searchExamples(index, listOf(area("Cluj-Napoca", 50, district = false), area("Centru", 8), area("Mănăștur", 34)))
        assertEquals("L2", examples.line?.shortName)
        assertEquals("S2", examples.stop?.name)
        assertEquals("Mănăștur", examples.place)
    }

    @Test
    fun hasNoPlaceWithoutNeighbourhoods() {
        assertNull(searchExamples(index, listOf(area("Buzău", 20, district = false))).place)
    }
}
