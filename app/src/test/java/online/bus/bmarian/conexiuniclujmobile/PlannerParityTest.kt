package online.bus.bmarian.conexiuniclujmobile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.logic.PlanResponse
import online.bus.bmarian.conexiuniclujmobile.logic.PlannedTimeEntry
import online.bus.bmarian.conexiuniclujmobile.logic.Planner
import online.bus.bmarian.conexiuniclujmobile.logic.StopLookup
import online.bus.bmarian.conexiuniclujmobile.logic.TimeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.abs

// Expected values from RoutePlanningView.vue run under Node (tools/parity).
class PlannerParityTest {
    @Serializable
    private class FixtureStop(val stop_id: Int, val stop_name: String = "", val stop_lat: Double = 0.0, val stop_lon: Double = 0.0)

    @Serializable
    private class Scenario(val label: String, val nowMs: Long, val mode: String, val timeValue: String = "", val response: PlanResponse)

    @Serializable
    private class Fixture(val stops: List<FixtureStop>, val scenarios: List<Scenario>)

    @Serializable
    private class Entry(
        val minutes: Double,
        val live: Boolean,
        val routeId: Int? = null,
        val tripId: String? = null,
        val transferRouteId: Int? = null,
        val transferTripId: String? = null,
    )

    @Serializable
    private class Built(
        val key: String,
        val routeIds: List<List<Int>>,
        val tripIds: List<List<String>>,
        val nextTimes: List<Entry>,
        val total: Double?,
        val duration: Double,
    )

    @Serializable
    private class Ticked(val key: String, val nextTimes: List<Entry>, val isLive: Boolean)

    @Serializable
    private class Expected(val label: String, val built: List<Built>, val ticked: List<Ticked>)

    private fun read(name: String): String =
        GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("parity/$name.gz")).bufferedReader().readText()

    private fun assertEntries(at: String, want: List<Entry>, got: List<PlannedTimeEntry>) {
        assertEquals("$at count", want.size, got.size)
        for ((i, pair) in want.zip(got).withIndex()) {
            val (w, g) = pair
            assert(abs(w.minutes - g.minutes) < 1e-6) { "$at[$i] minutes: want ${w.minutes}, got ${g.minutes}" }
            assertEquals("$at[$i] live", w.live, g.isLive)
            assertEquals("$at[$i] route", w.routeId, g.routeId)
            assertEquals("$at[$i] trip", w.tripId, g.tripId)
            assertEquals("$at[$i] transfer route", w.transferRouteId, g.transferRouteId)
            assertEquals("$at[$i] transfer trip", w.transferTripId, g.transferTripId)
        }
    }

    @Test
    fun plansMatchTheWebsite() {
        val fixture = Api.json.decodeFromString<Fixture>(read("plan_fixture.json"))
        // JSON.stringify writes Infinity as null.
        val expected = Api.json.parseToJsonElement(read("plan_expected.json")).jsonArray.map { scenario ->
            Api.json.decodeFromJsonElement(Expected.serializer(), scenario.jsonObject)
        }
        val byId = fixture.stops.associateBy { it.stop_id }
        var compared = 0
        for ((scenario, want) in fixture.scenarios.zip(expected)) {
            val stops = object : StopLookup {
                override fun name(id: Int) = byId[id]?.stop_name ?: scenario.response.stops["$id"]?.name
                override fun position(id: Int) = (byId[id]?.let { it.stop_lat to it.stop_lon })
                    ?: scenario.response.stops["$id"]?.let { it.lat to it.lon }
            }
            val mode = TimeMode.entries.first { it.key == scenario.mode }
            val planner = Planner(scenario.response.shapes, stops)
            val built = planner.buildPlans(scenario.response, scenario.nowMs, mode, scenario.timeValue)
            val label = scenario.label
            assertEquals("$label plans", want.built.map { it.key }, built.map { it.key })
            for ((w, g) in want.built.zip(built)) {
                val at = "$label ${w.key}"
                assertEquals("$at routes", w.routeIds, g.legs.map { it.routeIds })
                assertEquals("$at trips", w.tripIds, g.legs.map { it.tripIds })
                assertEntries(at, w.nextTimes, g.nextTimes)
                val total = Planner.computeTotalMinutes(g)
                assertEquals("$at total", w.total ?: Double.POSITIVE_INFINITY, total, 1e-9)
                assertEquals("$at duration", w.duration, Planner.getJourneyDuration(g), 1e-9)
                compared++
            }
            val ticked = planner.applyTiming(built, scenario.nowMs + 7 * 60_000, mode, scenario.timeValue, emptyMap())
            for ((w, g) in want.ticked.zip(ticked)) {
                assertEntries("$label ${w.key} tick", w.nextTimes, g.nextTimes)
                assertEquals("$label ${w.key} tick live", w.isLive, g.isLive)
            }
        }
        println("compared $compared plans across ${expected.size} scenarios")
    }
}
