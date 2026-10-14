package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

internal fun isClockTime(minutes: Int) = minutes >= 60

internal fun clockAt(nowMs: Long, minutes: Int): String =
    CLOCK.format(Instant.ofEpochMilli(nowMs + minutes * 60_000L).atZone(CLUJ_ZONE))

internal fun arrivingText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.follow_now)
    isClockTime(minutes) -> context.getString(R.string.follow_arriving_at, clockAt(nowMs, minutes))
    else -> context.getString(R.string.follow_arriving, context.getString(R.string.eta_minutes, minutes))
}

/** "Arriving at stop" and "in 3 min", split so a long stop name can't push the time out of view. */
internal fun arrivingAtText(context: Context, stop: String, minutes: Int, nowMs: Long): Pair<String, String?> = when {
    minutes <= 0 -> context.getString(R.string.follow_now_stop, stop) to null
    isClockTime(minutes) -> context.getString(R.string.follow_arriving_stop, stop) to context.getString(R.string.follow_at_clock, clockAt(nowMs, minutes))
    else -> context.getString(R.string.follow_arriving_stop, stop) to context.getString(R.string.follow_in, context.getString(R.string.eta_minutes, minutes))
}

internal fun chipText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.now)
    isClockTime(minutes) -> clockAt(nowMs, minutes)
    else -> context.getString(R.string.eta_minutes, minutes)
}

private fun leaveIn(leaveAt: Long, nowMs: Long) = ((leaveAt - nowMs) / 60_000.0).roundToInt().coerceAtLeast(0)

/** "Leave in 8 min" and "57 in 20 min · 12 min walk", without the walk when it's unknown. */
internal fun leaveText(context: Context, line: String, minutes: Int, leaveAt: Long, walk: Int?, nowMs: Long): Pair<String, String> {
    val leave = leaveIn(leaveAt, nowMs)
    val title = when {
        leave <= 0 -> context.getString(R.string.leave_now)
        isClockTime(leave) -> context.getString(R.string.leave_at, clockAt(nowMs, leave))
        else -> context.getString(R.string.leave_in, context.getString(R.string.eta_minutes, leave))
    }
    val bus = if (isClockTime(minutes)) context.getString(R.string.follow_at_clock, clockAt(nowMs, minutes))
    else context.getString(R.string.follow_in, context.getString(R.string.eta_minutes, minutes))
    val detail = if (walk == null) context.getString(R.string.leave_bus, line, bus)
    else context.getString(R.string.leave_detail, line, bus, context.getString(R.string.eta_minutes, walk))
    return title to detail
}

internal fun leaveChip(context: Context, leaveAt: Long, nowMs: Long): String {
    val leave = leaveIn(leaveAt, nowMs)
    return if (leave <= 0) context.getString(R.string.leave_now) else chipText(context, leave, nowMs)
}
