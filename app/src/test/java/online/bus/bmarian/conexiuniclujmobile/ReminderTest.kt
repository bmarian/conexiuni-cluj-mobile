package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.DaySchedule
import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.StopTime
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import online.bus.bmarian.conexiuniclujmobile.data.TimetableEntry
import online.bus.bmarian.conexiuniclujmobile.follow.FollowState
import online.bus.bmarian.conexiuniclujmobile.follow.FollowSync
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.justNotify
import online.bus.bmarian.conexiuniclujmobile.follow.leadMinutes
import online.bus.bmarian.conexiuniclujmobile.follow.tellAt
import online.bus.bmarian.conexiuniclujmobile.follow.resolveSlots
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.nextDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.nextOccurrence
import online.bus.bmarian.conexiuniclujmobile.logic.pickSlot
import online.bus.bmarian.conexiuniclujmobile.logic.stopTimesByDayType
import online.bus.bmarian.conexiuniclujmobile.logic.targetMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.walkMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(ms("2026-10-09T23:42"), nextOccurrence(slots, every, null, ms("2026-10-09T20:00")))
        assertEquals(ms("2026-10-10T23:00"), nextOccurrence(slots, every, null, ms("2026-10-09T23:50")))
        // No Sunday bus: on to Monday.
        assertEquals(ms("2026-10-12T23:42"), nextOccurrence(slots, every, null, ms("2026-10-10T23:30")))
        assertEquals(ms("2026-10-12T23:42"), nextOccurrence(slots, listOf(1), null, ms("2026-10-09T20:00")))
    }

    @Test
    fun aLateBusRunsOnItsServiceDayAndAOneOffOnlyOnce() {
        val slots = mapOf("weekday" to 24 * 60 + 5)
        // Friday's last bus leaves on Saturday at 00:05, and is still Friday's.
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, listOf(5), null, ms("2026-10-09T23:00")))
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, listOf(5), null, ms("2026-10-10T00:01")))
        val friday = LocalDate.parse("2026-10-09")
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, emptyList(), friday, ms("2026-10-09T08:00")))
        assertNull(nextOccurrence(slots, emptyList(), friday, ms("2026-10-10T00:06")))
    }

    @Test
    fun theNextBusCountsLastNightsLateOneAndSkipsDaysWithout() {
        val times = mapOf("weekday" to listOf(5 * 60 + 40, 23 * 60, 24 * 60 + 5), "saturday" to listOf(6 * 60 + 10, 22 * 60 + 40))
        val friday = LocalDate.parse("2026-10-09")
        assertEquals(friday to 23 * 60, nextDeparture(times, ms("2026-10-09T20:00")))
        // Past midnight, Friday's last bus is still to come and still Friday's.
        assertEquals(friday to 24 * 60 + 5, nextDeparture(times, ms("2026-10-10T00:01")))
        assertEquals(LocalDate.parse("2026-10-10") to 6 * 60 + 10, nextDeparture(times, ms("2026-10-10T00:06")))
        // No Sunday bus: Monday's first.
        assertEquals(LocalDate.parse("2026-10-12") to 5 * 60 + 40, nextDeparture(times, ms("2026-10-10T23:00")))
        assertNull(nextDeparture(emptyMap(), ms("2026-10-10T23:00")))
    }

    @Test
    fun aOneOffOnTheNextBusRunsOnItsServiceDay() {
        val slots = mapOf("weekday" to 24 * 60 + 5)
        val (day, minute) = nextDeparture(mapOf("weekday" to listOf(24 * 60 + 5)), ms("2026-10-10T00:01"))!!
        assertEquals(slots.values.single(), minute)
        assertEquals(ms("2026-10-10T00:05"), nextOccurrence(slots, emptyList(), day, ms("2026-10-10T00:01")))
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
        // At the stop within 60 m, a walk just past it.
        assertEquals(0, walkMinutes(46.77, 23.59, 46.77053, 23.59))
        assertEquals(1, walkMinutes(46.77, 23.59, 46.77055, 23.59))
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
    fun theHeadsUpComesTenMinutesBeforeItsTimeToLeaveOrTwentyBeforeTheBusWithoutAFix() {
        val bus = ms("2026-10-19T06:00")
        // A five minute walk: leave by 05:50, so told at 05:40.
        assertEquals(ms("2026-10-19T05:40"), tellAt(bus, 5))
        assertEquals(ms("2026-10-19T05:28"), tellAt(bus, 17))
        assertEquals(ms("2026-10-19T05:40"), tellAt(bus, null))
        assertEquals(ms("2026-10-19T05:40"), tellAt(bus, 0))
    }

    @Test
    fun atTheStopOrTooLateItsJustToldWhenItComes() {
        assertTrue(justNotify(0, 30))
        // A 12 min walk and 5 to spare: a bus 17 min away is too late to time.
        assertTrue(justNotify(12, 17))
        assertFalse(justNotify(12, 18))
        // No fix: twenty minutes.
        assertTrue(justNotify(null, 20))
        assertFalse(justNotify(null, 21))
    }

    @Test
    fun aFollowWithoutLeaveTimesStillReads() {
        val followed = Followed(2, "B", 57, "57_0", "57", "#ff0000", "Somewhere")
        val old = """{"id":"a","followed":${Api.json.encodeToString(followed)},"minutes":4}"""
        val sync = Api.json.decodeFromString<FollowSync>(old)
        assertNull(sync.leaveAt)
        assertNull(sync.leaveBy)
        assertEquals(4, sync.minutes)
    }

    @Test
    fun theWatchIsToldWhenToGoBy() {
        val followed = Followed(2, "B", 57, "57_0", "57", "#ff0000", "Somewhere")
        val state = FollowState("a", followed, minutes = 20, leaveAt = 1791441990000, walkMinutes = 8, leaveBy = 1791441960000)
        val json = Api.json.encodeToString(state.toSync(alert = true))
        val sync = Api.json.decodeFromString<FollowSync>(json)
        assertEquals(1791441960000, sync.leaveBy)
        assertEquals(1791441990000, sync.leaveAt)
        assertTrue(sync.alert)
    }
}
