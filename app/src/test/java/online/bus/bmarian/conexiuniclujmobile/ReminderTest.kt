package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.DaySchedule
import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import online.bus.bmarian.conexiuniclujmobile.data.TimetableEntry
import online.bus.bmarian.conexiuniclujmobile.follow.FollowSync
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.leadMinutes
import online.bus.bmarian.conexiuniclujmobile.follow.resolveSlots
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.nextOccurrence
import online.bus.bmarian.conexiuniclujmobile.logic.pickSlot
import online.bus.bmarian.conexiuniclujmobile.logic.stopTimesByDayType
import online.bus.bmarian.conexiuniclujmobile.logic.targetMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.walkMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTest {
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(CLUJ_ZONE).toInstant().toEpochMilli()

    private val shape = ShapeInfo(
        shortName = "57",
        routeId = 57,
        stopTimesPlural = listOf(
            StopTime("57_0", 1, offsetArrivalTime = 0.0, stopSequence = 1),
            StopTime("57_0", 2, offsetArrivalTime = 600.0, stopSequence = 2),
        ),
        timetable = Timetable(
            weekdays = DaySchedule(entries = listOf("05:30", "22:50", "23:55").map { TimetableEntry(departureIn = it) }),
            saturday = DaySchedule(entries = listOf("06:00", "22:30").map { TimetableEntry(departureIn = it) }),
        ),
    )

    @Test
    fun timesAtTheStopCountPastMidnightOnFromTheDayBefore() {
        val times = stopTimesByDayType(shape, "57_0", 2)
        assertEquals(listOf(5 * 60 + 40, 23 * 60, 24 * 60 + 5), times["weekday"])
        assertEquals(listOf(6 * 60 + 10, 22 * 60 + 40), times["saturday"])
        assertNull(times["sunday"])
    }

    @Test
    fun eachDepartureUsesItsOwnHoursTravelTime() {
        // Ten minutes to the stop in the morning, fifteen at night.
        val hourly = HourlyStopOffsets(listOf(1, 2), mapOf("5" to listOf(0, 600), "22" to listOf(0, 900), "23" to listOf(0, 840)))
        val times = stopTimesByDayType(shape, "57_0", 2, mapOf("weekday" to hourly))
        assertEquals(listOf(5 * 60 + 40, 23 * 60 + 5, 24 * 60 + 9), times["weekday"])
        // No hourly times for Saturday: the trip's own offsets.
        assertEquals(listOf(6 * 60 + 10, 22 * 60 + 40), times["saturday"])
    }

    @Test
    fun theLastBusIsTheDaysLatestAndATimeFindsItsNearest() {
        val times = stopTimesByDayType(shape, "57_0", 2)
        assertEquals(mapOf("weekday" to 24 * 60 + 5, "saturday" to 22 * 60 + 40), resolveSlots(times, null))
        // 23:00 on weekdays is 20 min from Saturday's 22:40: still the same evening bus.
        assertEquals(mapOf("weekday" to 23 * 60, "saturday" to 22 * 60 + 40), resolveSlots(times, 23 * 60))
        assertNull(pickSlot(listOf(600, 700), 721))
    }

    @Test
    fun theNextDepartureSkipsDaysWithoutTheReminderOrABus() {
        val slots = mapOf("weekday" to 23 * 60 + 42, "saturday" to 23 * 60)
        val every = (1..7).toList()
        // 2026-10-09 is a Friday.
        assertEquals(ms("2026-10-09T23:42"), nextOccurrence(slots, every, null, ms("2026-10-09T20:00"), CLUJ_ZONE))
        assertEquals(ms("2026-10-10T23:00"), nextOccurrence(slots, every, null, ms("2026-10-09T23:50"), CLUJ_ZONE))
        // No Sunday bus: on to Monday.
        assertEquals(ms("2026-10-12T23:42"), nextOccurrence(slots, every, null, ms("2026-10-10T23:30"), CLUJ_ZONE))
        assertEquals(ms("2026-10-12T23:42"), nextOccurrence(slots, listOf(1), null, ms("2026-10-09T20:00"), CLUJ_ZONE))
    }

    @Test
    fun aLateBusRunsOnItsServiceDayAndAOneOffOnlyOnce() {
        val slots = mapOf("weekday" to 24 * 60 + 5)
        // Friday's last bus leaves on Saturday at 00:05, and is still Friday's.
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, listOf(5), null, ms("2026-10-09T23:00"), CLUJ_ZONE))
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, listOf(5), null, ms("2026-10-10T00:01"), CLUJ_ZONE))
        val friday = LocalDate.parse("2026-10-09")
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, emptyList(), friday, ms("2026-10-09T08:00"), CLUJ_ZONE))
        assertNull(nextOccurrence(slots, emptyList(), friday, ms("2026-10-10T00:06"), CLUJ_ZONE))
    }

    @Test
    fun theLiveBusOnlyCountsWhenItsOurs() {
        // Ours is the 20 min one; the live bus is the one before it.
        assertEquals(20, targetMinutes(listOf(4.0, 20.0), live = 5, expected = 20.0, graceMinutes = 3.0))
        // Ours, running 3 min late.
        assertEquals(7, targetMinutes(listOf(4.0, 20.0), live = 7, expected = 4.0, graceMinutes = 3.0))
        // The last bus, late and not tracked.
        assertEquals(0, targetMinutes(listOf(-2.0), live = null, expected = -2.0, graceMinutes = 3.0))
        assertNull(targetMinutes(emptyList(), live = null, expected = -5.0, graceMinutes = 3.0))
    }

    @Test
    fun walksAreRoundedUpAndZeroAtTheStop() {
        assertEquals(0, walkMinutes(46.77, 23.59, 46.7702, 23.5901))
        // About 1 km as the crow flies.
        assertEquals(16, walkMinutes(46.77, 23.59, 46.779, 23.59))
    }

    @Test
    fun withoutAFixItsAFixedTwentyMinutes() {
        assertEquals(17, leadMinutes(12))
        assertEquals(5, leadMinutes(0))
        assertEquals(20, leadMinutes(null))
    }

    @Test
    fun aFollowWithoutLeaveTimesStillReads() {
        val followed = Followed(2, "B", 57, "57_0", "57", "#ff0000", "Somewhere")
        val old = """{"id":"a","followed":${Api.json.encodeToString(followed)},"minutes":4}"""
        val sync = Api.json.decodeFromString<FollowSync>(old)
        assertNull(sync.leaveAt)
        assertEquals(4, sync.minutes)
    }
}
