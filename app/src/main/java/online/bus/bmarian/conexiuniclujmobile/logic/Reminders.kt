package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.OUTGOING_SUFFIX
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// Times before this belong to the previous day's service.
private const val NIGHT_END_MIN = 180
private const val NEAREST_WITHIN_MIN = 20
private const val WALK_DETOUR = 1.25
private const val AT_STOP_METERS = 60.0

fun dayTypeOf(day: DayOfWeek): String = when (day) {
    DayOfWeek.SATURDAY -> TimetableTab.Saturday.dayType
    DayOfWeek.SUNDAY -> TimetableTab.Sunday.dayType
    else -> TimetableTab.Weekdays.dayType
}

fun dayTypeOf(date: LocalDate): String = scheduleDayOn(date)

/** The service day a departure belongs to and its minute in that day, past midnight counted on from 24:00. */
fun serviceSlot(ms: Long, zone: ZoneId): Pair<LocalDate, Int> {
    val at = Instant.ofEpochMilli(ms).atZone(zone)
    val minute = at.hour * 60 + at.minute
    return if (minute < NIGHT_END_MIN) at.toLocalDate().minusDays(1) to minute + 1440 else at.toLocalDate() to minute
}

/** Timetable minutes at the stop per day type, past midnight counted on from 24:00, each timed for its own hour. */
fun stopTimesByDayType(
    shape: ShapeInfo,
    tripId: String,
    stopId: Int,
    hourly: Map<String, HourlyStopOffsets?> = emptyMap(),
): Map<String, List<Int>> {
    val fallback = timeOffsetToStop(shape.stopTimes, tripId, stopId)
    val outgoing = tripId.endsWith(OUTGOING_SUFFIX)
    return TimetableTab.entries.associate { tab ->
        val trip = hourly[tab.dayType]
        val index = trip?.stopIds?.indexOf(stopId) ?: -1
        tab.dayType to departureMinutes(shape.timetable.scheduleFor(tab), outgoing)
            .map { departure ->
                val seconds = trip?.hourlyOffsetSeconds?.get("${Math.floorDiv(departure, 60) % 24}")?.getOrNull(index)
                val offsetSec = if (index >= 0 && seconds != null) seconds.toDouble() else fallback
                (departure + offsetSec / 60).roundToInt().let { m -> if (m < NIGHT_END_MIN) m + 1440 else m }
            }
            .sorted()
    }.filterValues { it.isNotEmpty() }
}

/** The day's last bus when [time] is null, otherwise the one nearest [time]. */
fun pickSlot(times: List<Int>, time: Int?): Int? {
    if (time == null) return times.maxOrNull()
    return times.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= NEAREST_WITHIN_MIN }
}

/** The first departure after [afterMs] as epoch ms. Starts a day back: late buses run past midnight. */
fun nextOccurrence(slots: Map<String, Int>, days: List<Int>, date: LocalDate?, afterMs: Long): Long? {
    val today = clujDate(afterMs)
    for (i in -1L..8L) {
        val day = today.plusDays(i)
        if (date != null && day != date) continue
        if (date == null && day.dayOfWeek.value !in days) continue
        val minute = slots[dayTypeOf(day)] ?: continue
        val at = cityAt(day, minute)
        if (at > afterMs) return at
    }
    return null
}

/** The first of the timetable [times] after [afterMs]: its service day and its minute in that day. */
fun nextDeparture(times: Map<String, List<Int>>, afterMs: Long): Pair<LocalDate, Int>? {
    val today = clujDate(afterMs)
    var first: Pair<LocalDate, Int>? = null
    var firstAt = Long.MAX_VALUE
    // Starts a day back: last night's late buses run past midnight.
    for (i in -1L..8L) {
        val day = today.plusDays(i)
        for (minute in times[dayTypeOf(day)].orEmpty()) {
            val at = cityAt(day, minute)
            if (at > afterMs && at < firstAt) {
                first = day to minute
                firstAt = at
            }
        }
    }
    return first
}

/** Minutes until the followed departure; the live bus counts when it's nearest [expected]. Null once gone. */
fun targetMinutes(scheduled: List<Double>, live: Int?, expected: Double, graceMinutes: Double): Int? {
    val ours = scheduled.minByOrNull { abs(it - expected) }?.takeIf { abs(it - expected) <= 1 } ?: expected
    if (live != null && scheduled.minByOrNull { abs(it - live) } == ours) return live
    if (ours < -graceMinutes) return null
    return ours.roundToInt().coerceAtLeast(0)
}

fun walkMinutes(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Int {
    val meters = haversineMeters(fromLat, fromLon, toLat, toLon)
    if (meters < AT_STOP_METERS) return 0
    return ceil(meters * WALK_DETOUR / WALK_SPEED).toInt()
}
