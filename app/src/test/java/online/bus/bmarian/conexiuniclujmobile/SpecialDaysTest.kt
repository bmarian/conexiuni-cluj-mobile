package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.DaySchedule
import online.bus.bmarian.conexiuniclujmobile.data.NetworkBundle
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import online.bus.bmarian.conexiuniclujmobile.data.TimetableEntry
import online.bus.bmarian.conexiuniclujmobile.data.clujClock
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.DayNote
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedStop
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleCalendar
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleDays
import online.bus.bmarian.conexiuniclujmobile.logic.SpecialDay
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.chipsForDay
import online.bus.bmarian.conexiuniclujmobile.logic.daySchedule
import online.bus.bmarian.conexiuniclujmobile.logic.isPastTab
import online.bus.bmarian.conexiuniclujmobile.logic.nextOccurrence
import online.bus.bmarian.conexiuniclujmobile.logic.plainTodayTab
import online.bus.bmarian.conexiuniclujmobile.logic.scheduleDayAt
import online.bus.bmarian.conexiuniclujmobile.logic.specialDayOn
import online.bus.bmarian.conexiuniclujmobile.logic.todayTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class SpecialDaysTest {
    private val calendar = ScheduleCalendar(
        mapOf("2026-12-01" to "sunday", "2025-12-25" to "sunday"),
        mapOf("2026-12-01" to DayNote("holiday", "national_day"), "2025-12-25" to DayNote("holiday", "christmas")),
    )

    private fun utc(text: String) = Instant.parse(text).toEpochMilli()

    private fun withCalendar(block: () -> Unit) {
        ScheduleDays.use(calendar)
        try {
            block()
        } finally {
            ScheduleDays.use(ScheduleCalendar())
        }
    }

    private val timetable = Timetable(
        weekdays = DaySchedule(entries = listOf("08:00", "18:00").map { TimetableEntry(departureIn = it) }),
        saturday = DaySchedule(entries = listOf("09:00").map { TimetableEntry(departureIn = it) }),
        sunday = DaySchedule(entries = listOf("10:00", "20:00").map { TimetableEntry(departureIn = it) }),
    )
    private val stops = listOf(IndexedStop(StopTime("1_0", 1), 0.0))

    @Test
    fun aHolidayRunsTheTimetableTheBundleNames() {
        assertEquals("sunday", scheduleDayAt(utc("2026-12-01T08:00:00Z"), calendar))
        assertEquals(SpecialDay("sunday", DayNote("holiday", "national_day")), specialDayOn(LocalDate.parse("2026-12-01"), calendar))
        withCalendar {
            assertEquals(timetable.sunday, daySchedule(timetable, LocalDateTime.parse("2026-12-01T10:00")))
            assertEquals("sunday" to 10, clujClock(utc("2026-12-01T08:00:00Z")))
        }
    }

    @Test
    fun anOrdinaryDayFollowsTheWeekday() {
        assertEquals("weekday", scheduleDayAt(utc("2026-12-02T08:00:00Z"), calendar))
        assertEquals("saturday", scheduleDayAt(utc("2026-12-05T08:00:00Z"), calendar))
        assertNull(specialDayOn(LocalDate.parse("2026-12-02"), calendar))
    }

    @Test
    fun aBundleWithoutTheFieldsKeepsTheWeekdayRule() {
        val old = NetworkIndex(Api.json.decodeFromString<NetworkBundle>("""{"version":"v1","routes":[],"stops":[]}"""))
        assertEquals("weekday", scheduleDayAt(utc("2026-12-01T08:00:00Z"), old.calendar))
        assertNull(specialDayOn(LocalDate.parse("2026-12-01"), old.calendar))
        assertEquals("weekday", scheduleDayAt(utc("2026-12-01T08:00:00Z")))

        val fresh = NetworkIndex(
            Api.json.decodeFromString<NetworkBundle>(
                """{"schedule_days":{"2026-12-01":"sunday","2026-12-02":"bogus"},"day_notes":{"2026-12-01":{"reason":"holiday","holiday":"national_day"}}}""",
            ),
        )
        assertEquals(SpecialDay("sunday", DayNote("holiday", "national_day")), specialDayOn(LocalDate.parse("2026-12-01"), fresh.calendar))
        assertEquals("weekday", scheduleDayAt(utc("2026-12-02T08:00:00Z"), fresh.calendar))
    }

    @Test
    fun justAfterMidnightCountsTheClujDateWhateverTheDeviceZone() {
        val device = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            // 00:30 on 1 Dec in Cluj, still 30 Nov in New York.
            assertEquals("sunday" to 0, clujClock(utc("2026-11-30T22:30:00Z"), calendar))
            // 00:30 on 2 Dec in Cluj, still the 1 Dec holiday in New York.
            assertEquals("weekday", scheduleDayAt(utc("2026-12-01T22:30:00Z"), calendar))
        } finally {
            TimeZone.setDefault(device)
        }
    }

    @Test
    fun aThursdayOnTheSundayTimetableDoesNotFinishTheWeek() {
        val noon = LocalDateTime.parse("2025-12-25T12:00")
        assertEquals(TimetableTab.Sunday, todayTab(noon, calendar))
        assertEquals(TimetableTab.Weekdays, plainTodayTab(noon))
        assertFalse(isPastTab(TimetableTab.Weekdays, plainTodayTab(noon)))
        assertFalse(isPastTab(TimetableTab.Saturday, plainTodayTab(noon)))

        val weekdays = chipsForDay(timetable, TimetableTab.Weekdays, true, null, stops, noon, calendar)
        assertEquals(listOf(false, false), weekdays.map { it.isPast })
        val sunday = chipsForDay(timetable, TimetableTab.Sunday, true, null, stops, noon, calendar)
        assertEquals(listOf(true, false), sunday.map { it.isPast })

        val saturdayNoon = LocalDateTime.parse("2025-12-27T12:00")
        assertEquals(listOf(true, true), chipsForDay(timetable, TimetableTab.Weekdays, true, null, stops, saturdayNoon, calendar).map { it.isPast })
    }

    @Test
    fun aReminderSkipsAHolidayWithoutItsTimetable() = withCalendar {
        val slots = mapOf("weekday" to 7 * 60)
        val every = (1..7).toList()
        val after = LocalDateTime.parse("2026-11-30T08:00").atZone(CLUJ_ZONE).toInstant().toEpochMilli()
        val next = LocalDateTime.parse("2026-12-02T07:00").atZone(CLUJ_ZONE).toInstant().toEpochMilli()
        assertEquals(next, nextOccurrence(slots, every, null, after, CLUJ_ZONE))
    }
}
