package online.bus.bmarian.conexiuniclujmobile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.assembleShapeInfo
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.chipsByHour
import online.bus.bmarian.conexiuniclujmobile.logic.chipsForDay
import online.bus.bmarian.conexiuniclujmobile.logic.stopsForDirection
import online.bus.bmarian.conexiuniclujmobile.logic.tripHourlyOffsets
import online.bus.bmarian.conexiuniclujmobile.logic.tripStops
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream

// Expected values from RouteView.vue run under Node (tools/parity).
class TimetableParityTest {
    @Serializable
    private class FixtureRoute(
        val route_id: Int,
        val timetable: Timetable,
        val stop_times: List<StopTime>,
        val hourly: Map<String, Map<String, HourlyStopOffsets>>,
    )

    @Serializable
    private class Scenario(val route: String, val nowMs: Long)

    @Serializable
    private class Fixture(val routes: Map<String, FixtureRoute>, val scenarios: List<Scenario>)

    @Serializable
    private class Group(val hour: String, val isNextDay: Boolean, val chips: List<JsonArray>)

    @Serializable
    private class TripView(val time: String, val stops: List<String>)

    @Serializable
    private class Grid(val label: String, val hourly: Boolean, val chips: List<JsonArray>, val groups: List<Group>, val trips: List<TripView>)

    private fun read(name: String): String =
        GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("parity/$name.gz")).bufferedReader().readText()

    private fun chip(a: JsonArray) = Triple(
        (a[0] as JsonPrimitive).contentOrNull,
        (a[1] as? JsonPrimitive)?.intOrNull,
        (a[2] as JsonPrimitive).booleanOrNull,
    )

    @Test
    fun gridMatchesTheWebsite() {
        val fixture = Api.json.decodeFromString<Fixture>(read("tt_fixture.json"))
        val expected = Api.json.decodeFromString<List<Grid>>(read("tt_expected.json"))
        val iterator = expected.iterator()
        var compared = 0
        for (scenario in fixture.scenarios) {
            val r = fixture.routes.getValue(scenario.route)
            val info = assembleShapeInfo(Route(r.route_id, scenario.route), r.timetable, r.stop_times)
            val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(scenario.nowMs), CLUJ_ZONE)
            for (dir in listOf("0", "1")) {
                val tripId = "${r.route_id}_$dir"
                val stops = stopsForDirection(info, tripId)
                for (tab in TimetableTab.entries) {
                    val want = iterator.next()
                    val day = tab.name.lowercase()
                    val hourly = tripHourlyOffsets(r.hourly[day].orEmpty(), tripId, stops)
                    val at = want.label
                    assertEquals("$at hourly", want.hourly, hourly != null)
                    val chips = chipsForDay(r.timetable, tab, dir == "0", hourly, stops, now)
                    assertEquals("$at chips", want.chips.map(::chip), chips.map { Triple(it.time, it.startMin, it.isPast) })
                    val groups = chipsByHour(chips)
                    assertEquals("$at hours", want.groups.map { it.hour to it.isNextDay }, groups.map { it.hour to it.isNextDay })
                    assertEquals("$at grouped", want.groups.map { g -> g.chips.map(::chip) }, groups.map { g -> g.chips.map { Triple(it.time, it.startMin, it.isPast) } })
                    for (trip in want.trips) {
                        assertEquals("$at trip ${trip.time}", trip.stops, tripStops(trip.time, hourly, stops).map { it.arrival })
                    }
                    compared++
                }
            }
        }
        println("compared $compared timetable grids")
    }
}
