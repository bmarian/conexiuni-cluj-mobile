package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.DaySchedule
import online.bus.bmarian.conexiuniclujmobile.data.Frequency
import online.bus.bmarian.conexiuniclujmobile.data.HourlyStopOffsets
import online.bus.bmarian.conexiuniclujmobile.data.Timetable
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.math.ceil

// Port of the timetable grid in RouteView.vue.

enum class TimetableTab(val dayType: String) { Weekdays("weekday"), Saturday("saturday"), Sunday("sunday") }

fun todayTab(now: LocalDateTime): TimetableTab = when (now.dayOfWeek) {
    DayOfWeek.SUNDAY -> TimetableTab.Sunday
    DayOfWeek.SATURDAY -> TimetableTab.Saturday
    else -> TimetableTab.Weekdays
}

fun Timetable.scheduleFor(tab: TimetableTab): DaySchedule = when (tab) {
    TimetableTab.Weekdays -> weekdays
    TimetableTab.Saturday -> saturday
    TimetableTab.Sunday -> sunday
}

fun dayHasContent(d: DaySchedule): Boolean = d.entries.isNotEmpty() || d.inFrequency != null || d.outFrequency != null

fun availableTabs(timetable: Timetable): List<TimetableTab> =
    TimetableTab.entries.filter { dayHasContent(timetable.scheduleFor(it)) }

fun isPastTab(tab: TimetableTab, today: TimetableTab) = tab.ordinal < today.ordinal

fun activeFrequency(timetable: Timetable, tab: TimetableTab, outgoing: Boolean): Frequency? {
    val sched = timetable.scheduleFor(tab)
    return if (outgoing) sched.inFrequency else sched.outFrequency
}

fun formatAbsoluteMinutes(absMin: Int): String {
    val dayMin = ((absMin % 1440) + 1440) % 1440
    return "%02d:%02d".format(dayMin / 60, dayMin % 60)
}

fun tripHourlyOffsets(offsets: Map<String, HourlyStopOffsets>, tripId: String, stops: List<IndexedStop>): Map<String, List<Int>>? {
    val trip = offsets[tripId] ?: return null
    if (trip.stopIds.size != stops.size) return null
    return if (trip.stopIds.withIndex().all { (i, id) -> id == stops[i].stop.stopId }) trip.hourlyOffsetSeconds else null
}

fun offsetsAt(hourly: Map<String, List<Int>>?, departureMin: Int, stops: List<IndexedStop>): List<Int> {
    val seconds = hourly?.get("${Math.floorDiv(departureMin, 60) % 24}")
    return seconds?.map { ceil(it / 60.0).toInt() } ?: stops.map { it.offsetFromStart }
}

data class TimetableChip(val time: String, val startMin: Int?, val isPast: Boolean)

fun chipsForDay(
    timetable: Timetable,
    tab: TimetableTab,
    outgoing: Boolean,
    hourly: Map<String, List<Int>>?,
    stops: List<IndexedStop>,
    now: LocalDateTime,
): List<TimetableChip> {
    val sched = timetable.scheduleFor(tab)
    if (sched.entries.isEmpty()) return emptyList()
    val today = todayTab(now)
    val isToday = tab == today
    val dayIsPast = isPastTab(tab, today)
    val nowMinutes = minutesOfDay(now)
    return sched.entries.mapNotNull { entry ->
        val raw = (if (outgoing) entry.departureIn else entry.departureOut).trim()
        if (raw.isEmpty()) return@mapNotNull null
        val departureMin = timeStringToMinutes(raw) ?: return@mapNotNull TimetableChip(raw, null, false)
        val startMin = departureMin + (offsetsAt(hourly, departureMin, stops).firstOrNull() ?: 0)
        TimetableChip(raw, startMin, dayIsPast || (isToday && startMin < nowMinutes))
    }
}

class HourGroup(val hour: String, val chips: List<TimetableChip>, val isNextDay: Boolean)

fun chipsByHour(entries: List<TimetableChip>): List<HourGroup> =
    entries.filter { it.startMin != null }
        .sortedBy { it.startMin }
        .groupBy { Math.floorDiv(it.startMin!!, 60) }
        .map { (h, chips) -> HourGroup(formatAbsoluteMinutes(h * 60).take(2), chips, h >= 24) }

class TripStop(val stop: IndexedStop, val arrival: String)

fun tripStops(selectedTime: String, hourly: Map<String, List<Int>>?, stops: List<IndexedStop>): List<TripStop> {
    val departureMin = timeStringToMinutes(selectedTime) ?: return emptyList()
    val offsets = offsetsAt(hourly, departureMin, stops)
    return stops.mapIndexed { idx, stop ->
        val offset = offsets.getOrNull(idx) ?: stop.offsetFromStart
        TripStop(stop, formatAbsoluteMinutes(departureMin + offset))
    }
}
