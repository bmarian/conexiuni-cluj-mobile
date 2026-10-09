package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

internal fun isClockTime(minutes: Int) = minutes >= 60

internal fun clockAt(nowMs: Long, minutes: Int): String =
    CLOCK.format(Instant.ofEpochMilli(nowMs + minutes * 60_000L).atZone(CLUJ_ZONE))

internal fun arrivingText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.follow_now)
    isClockTime(minutes) -> context.getString(R.string.follow_arriving_at, clockAt(nowMs, minutes))
    else -> context.getString(R.string.follow_arriving, context.getString(R.string.eta_minutes, minutes))
}

internal fun chipText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.now)
    isClockTime(minutes) -> clockAt(nowMs, minutes)
    else -> context.getString(R.string.eta_minutes, minutes)
}
