package online.bus.bmarian.conexiuniclujmobile.logic

import android.content.Context
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.RouteChangeItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Port of frontend/src/utils/routeChanges.ts.

private val KIND_ORDER = listOf("later", "earlier", "trips_added", "trips_removed", "stops_added", "stops_removed")
private val DAY_ORDER = listOf("weekdays", "saturday", "sunday")

private fun dayPhrase(day: String) = when (day) {
    "weekdays" -> R.string.w_route_change_day_weekdays
    "saturday" -> R.string.w_route_change_day_saturday
    else -> R.string.w_route_change_day_sunday
}

private fun kindLabel(kind: String) = when (kind) {
    "later", "earlier" -> R.string.w_route_change_kind_retimed
    "trips_added" -> R.string.w_route_change_kind_trips_added
    "trips_removed" -> R.string.w_route_change_kind_trips_removed
    "stops_added" -> R.string.w_route_change_kind_stops_added
    else -> R.string.w_route_change_kind_stops_removed
}

fun changeKinds(change: RouteChange): List<String> {
    val present = change.changes.map { it.kind }.toSet()
    return KIND_ORDER.filter { it in present }
}

fun changeKindLabels(change: RouteChange, context: Context): List<String> =
    changeKinds(change).map { context.getString(kindLabel(it)) }.distinct()

fun changeTitle(change: RouteChange, context: Context): String =
    context.getString(if (change.source == "stops") R.string.w_route_change_stops else R.string.w_route_change_timetable)

fun joinList(items: List<String>, and: String): String {
    if (items.size <= 1) return items.firstOrNull() ?: ""
    return "${items.dropLast(1).joinToString(", ")} $and ${items.last()}"
}

fun changeSummary(change: RouteChange, context: Context): String {
    if (change.source == "stops") {
        fun stopsOf(kind: String) = change.changes.filter { it.kind == kind }.flatMap { it.stops.orEmpty() }.toSet()
        val added = stopsOf("stops_added")
        val removed = stopsOf("stops_removed")
        if (added.isNotEmpty() && removed.isNotEmpty()) return context.getString(R.string.w_route_change_summary_stops_both)
        if (added.isNotEmpty()) {
            return context.getString(if (added.size == 1) R.string.w_route_change_summary_stop_added else R.string.w_route_change_summary_stops_added)
        }
        return context.getString(if (removed.size == 1) R.string.w_route_change_summary_stop_removed else R.string.w_route_change_summary_stops_removed)
    }
    val present = change.changes.flatMap { it.days.orEmpty() }.toSet()
    val days = DAY_ORDER.filter { it in present }.map { context.getString(dayPhrase(it)) }
    if (days.isEmpty()) return context.getString(R.string.w_route_change_summary_timetable_any)
    return context.getString(
        if (days.size == 1) R.string.w_route_change_summary_timetable_one else R.string.w_route_change_summary_timetable_many,
        joinList(days, context.getString(R.string.w_route_change_list_and)),
    )
}

class DayGroup(val key: String, val days: List<String>, val since: String?, val items: MutableList<RouteChangeItem>)

fun groupByDays(items: List<RouteChangeItem>): List<DayGroup> {
    val groups = mutableListOf<DayGroup>()
    for (item in items) {
        val key = item.days.orEmpty().joinToString(",")
        val group = groups.find { it.key == key } ?: DayGroup(key, item.days.orEmpty(), item.since, mutableListOf()).also { groups += it }
        group.items += item
    }
    return groups
}

fun changeDirection(change: RouteChange): String = change.changes.firstOrNull()?.direction ?: "0"

private fun dateLocale(locale: String): Locale = if (locale == "en") Locale.UK else Locale.forLanguageTag("ro-RO")

/** "8 Oct 2026" or "8 oct. 2026", like toLocaleDateString with day, short month and year. */
fun formatChangeDate(ms: Long, locale: String, withYear: Boolean = true): String {
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return formatDate(date, locale, withYear)
}

private fun formatDate(date: LocalDate, locale: String, withYear: Boolean): String =
    date.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", dateLocale(locale)))

// CTP writes service_start as DD.MM.YYYY.
fun formatServiceStart(since: String, locale: String): String {
    val parts = since.split('.').map { it.toIntOrNull() ?: 0 }
    val d = parts.getOrElse(0) { 0 }
    val m = parts.getOrElse(1) { 0 }
    val y = parts.getOrElse(2) { 0 }
    if (d == 0 || m == 0 || y == 0) return since
    return runCatching { formatDate(LocalDate.of(y, m, d), locale, false) }.getOrDefault(since)
}

fun daysLabel(days: List<String>?, context: Context): String = days.orEmpty().joinToString(", ") {
    context.getString(
        when (it) {
            "weekdays" -> R.string.w_weekdays
            "saturday" -> R.string.w_saturday
            else -> R.string.w_sunday
        },
    )
}

fun itemLabel(item: RouteChangeItem, context: Context): String {
    val times = item.times.orEmpty()
    return when (item.kind) {
        "later", "earlier" -> context.getString(R.string.w_route_change_retimed, "${item.shifts?.size ?: 0}")
        "trips_added" -> if (item.all == true) {
            context.getString(R.string.w_route_change_now_runs, times.firstOrNull() ?: "", times.lastOrNull() ?: "")
        } else {
            context.getString(R.string.w_route_change_trips_added, "${times.size}")
        }
        "trips_removed" -> if (item.all == true) context.getString(R.string.w_route_change_no_longer_runs)
        else context.getString(R.string.w_route_change_trips_removed, "${times.size}")
        "stops_added" -> context.getString(R.string.w_route_change_stops_added)
        else -> context.getString(R.string.w_route_change_stops_removed)
    }
}

class ChangeEntry(val text: String, val old: String? = null, val struck: Boolean = false)

fun itemEntries(item: RouteChangeItem): List<ChangeEntry> {
    val shifts = item.shifts.orEmpty()
    if (shifts.isNotEmpty()) return shifts.map { ChangeEntry(it.to, old = it.from) }
    if (item.all == true) return emptyList()
    val stops = item.stops.orEmpty()
    if (stops.isNotEmpty()) return stops.map { ChangeEntry(it) }
    return item.times.orEmpty().map { ChangeEntry(it, struck = item.kind == "trips_removed") }
}

class ChangeRow(val kind: String, val label: String, val entries: List<ChangeEntry>)

private val ROW_ORDER = listOf("retimed", "trips_added", "trips_removed", "stops_added", "stops_removed")

// The service day starts around 04:00, so 00:30 belongs after 23:30.
private fun serviceMinutes(time: String): Int {
    val m = timeStringToMinutes(time) ?: 0
    return if (m < 240) m + 1440 else m
}

fun changeRows(items: List<RouteChangeItem>, context: Context): List<ChangeRow> {
    fun isShift(item: RouteChangeItem) = item.kind == "later" || item.kind == "earlier"
    val shifts = items.filter(::isShift).flatMap { it.shifts.orEmpty() }.sortedBy { serviceMinutes(it.from) }
    val rows = items.filterNot(::isShift).map { ChangeRow(it.kind, itemLabel(it, context), itemEntries(it)) }.toMutableList()
    if (shifts.isNotEmpty()) {
        rows += ChangeRow(
            "retimed",
            context.getString(R.string.w_route_change_retimed, "${shifts.size}"),
            shifts.map { ChangeEntry(it.to, old = it.from) },
        )
    }
    return rows.sortedBy { ROW_ORDER.indexOf(it.kind) }
}

class DirectionGroup(val direction: String, val toward: String, val items: MutableList<RouteChangeItem>)

fun groupByDirection(change: RouteChange): List<DirectionGroup> {
    val groups = mutableListOf<DirectionGroup>()
    for (item in change.changes) {
        val group = groups.find { it.direction == item.direction }
            ?: DirectionGroup(item.direction, item.toward ?: "", mutableListOf()).also { groups += it }
        group.items += item
    }
    return groups
}
