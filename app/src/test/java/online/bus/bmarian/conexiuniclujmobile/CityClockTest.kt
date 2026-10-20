package online.bus.bmarian.conexiuniclujmobile

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.clujClock
import online.bus.bmarian.conexiuniclujmobile.follow.clockAt
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleCalendar
import online.bus.bmarian.conexiuniclujmobile.logic.cityAt
import online.bus.bmarian.conexiuniclujmobile.logic.clujDate
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.logic.nextDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.nextOccurrence
import online.bus.bmarian.conexiuniclujmobile.logic.scheduleDayAt
import online.bus.bmarian.conexiuniclujmobile.logic.stopDepartures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class CityClockTest {
    private val fixture = Api.json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("network/network_fixture.json").bufferedReader().readText(),
    ).jsonObject
    private val index = NetworkIndex(Api.json.decodeFromJsonElement<NetworkBundle>(fixture["bundle"]!!))
    private val stopId = fixture["stop_id"]!!.jsonPrimitive.int

    private fun utc(text: String) = Instant.parse(text).toEpochMilli()

    private fun <T> inEveryZone(read: () -> T): T {
        val device = TimeZone.getDefault()
        try {
            val answers = listOf("Europe/Bucharest", "America/New_York", "Asia/Tokyo").map { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                zone to read()
            }
            for ((zone, answer) in answers) assertEquals(zone, answers.first().second, answer)
            return answers.first().second
        } finally {
            TimeZone.setDefault(device)
        }
    }

    @Test
    fun aStopListsTheSameDeparturesWhateverTheDeviceZone() {
        // Sunday 05:00 in Cluj is Saturday 22:00 in New York; then the night the clocks go back.
        for (at in listOf("2026-10-18T02:00:00Z", "2026-10-24T20:30:00Z", "2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z", "2026-10-25T05:00:00Z").map(::utc)) {
            val departures = inEveryZone {
                val info = index.stopInfo(stopId, at)!!
                stopDepartures(info, null, LocalDateTime.ofInstant(Instant.ofEpochMilli(at), CLUJ_ZONE), at)
            }
            assertTrue(departures.isNotEmpty())
        }
    }

    @Test
    fun theTimetableDayIsTheCitysDate() {
        assertEquals("sunday" to 5, inEveryZone { clujClock(utc("2026-10-18T02:00:00Z"), ScheduleCalendar()) })
        val calendar = ScheduleCalendar(mapOf("2026-10-25" to "weekday"))
        // Already the 25th in Tokyo, still the 24th in Cluj.
        assertEquals("saturday", inEveryZone { scheduleDayAt(utc("2026-10-24T20:30:00Z"), calendar) })
        // Already the 25th in Cluj, still the 24th in New York.
        assertEquals("weekday" to 1, inEveryZone { clujClock(utc("2026-10-24T22:30:00Z"), calendar) })
        assertEquals("weekday" to 3, inEveryZone { clujClock(utc("2026-10-25T01:30:00Z"), calendar) })
        assertEquals(DayOfWeek.FRIDAY, inEveryZone { clujDate(utc("2026-10-23T16:00:00Z")).dayOfWeek })
        assertEquals(DayOfWeek.SATURDAY, inEveryZone { clujDate(utc("2026-10-23T21:30:00Z")).dayOfWeek })
    }

    @Test
    fun aCityDateAndMinuteIsTheMomentTheCitysClockReadsIt() {
        assertEquals(utc("2026-10-24T21:30:00Z"), inEveryZone { cityAt(LocalDate.parse("2026-10-24"), 24 * 60 + 30) })
        // The hour that comes twice resolves to the second, as on the website.
        assertEquals(utc("2026-10-25T01:30:00Z"), inEveryZone { cityAt(LocalDate.parse("2026-10-25"), 3 * 60 + 30) })
        assertEquals(utc("2026-10-25T10:00:00Z"), inEveryZone { cityAt(LocalDate.parse("2026-10-25"), 12 * 60) })
        assertEquals(utc("2026-03-29T01:30:00Z"), inEveryZone { cityAt(LocalDate.parse("2026-03-29"), 3 * 60 + 30) })
    }

    @Test
    fun remindersFireOnTheCitysClockTheNightTheClocksGoBack() {
        val slots = mapOf("weekday" to 7 * 60, "saturday" to 24 * 60 + 30, "sunday" to 10 * 60)
        val every = (1..7).toList()
        // Saturday's 00:30 bus, still on summer time.
        assertEquals(utc("2026-10-24T21:30:00Z"), inEveryZone { nextOccurrence(slots, every, null, utc("2026-10-24T19:00:00Z")) })
        // Sunday's 10:00 bus on winter time, not an hour early.
        assertEquals(utc("2026-10-25T08:00:00Z"), inEveryZone { nextOccurrence(slots, every, null, utc("2026-10-24T22:00:00Z")) })

        val times = mapOf("saturday" to listOf(24 * 60 + 30), "sunday" to listOf(10 * 60))
        assertEquals(LocalDate.parse("2026-10-24") to 24 * 60 + 30, inEveryZone { nextDeparture(times, utc("2026-10-24T21:00:00Z")) })
        assertEquals(LocalDate.parse("2026-10-25") to 10 * 60, inEveryZone { nextDeparture(times, utc("2026-10-24T22:00:00Z")) })
    }

    @Test
    fun clockTimesCountRealMinutes() {
        // 06:30 in Cluj, Saturday 23:30 in New York.
        val morning = LocalDateTime.ofInstant(Instant.ofEpochMilli(utc("2026-10-18T03:30:00Z")), CLUJ_ZONE)
        assertEquals("08:00", inEveryZone { formatMinutesFromNow(90, morning, "now") })
        // 90 minutes after 02:30 summer time is 03:00 winter time.
        assertEquals("03:00", inEveryZone { formatMinutesFromNow(90, LocalDateTime.parse("2026-10-25T02:30"), "now") })
        assertEquals("03:00", inEveryZone { clockAt(utc("2026-10-24T23:30:00Z"), 90) })
    }
}
