package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Within the hour, "5 min"; further off, the clock time it comes, as the stop page shows it. */
internal fun isClockTime(minutes: Int) = minutes >= 60

internal fun clockAt(nowMs: Long, minutes: Int): String =
    CLOCK.format(Instant.ofEpochMilli(nowMs + minutes * 60_000L).atZone(CLUJ_ZONE))

/** "Arriving in 5 min", "Arriving now" or "Arriving at 13:51". */
internal fun arrivingText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.follow_now)
    isClockTime(minutes) -> context.getString(R.string.follow_arriving_at, clockAt(nowMs, minutes))
    else -> context.getString(R.string.follow_arriving, context.getString(R.string.eta_minutes, minutes))
}

/** The status bar chip: "5 min", "now" or "13:51". */
internal fun chipText(context: Context, minutes: Int, nowMs: Long): String = when {
    minutes <= 0 -> context.getString(R.string.now)
    isClockTime(minutes) -> clockAt(nowMs, minutes)
    else -> context.getString(R.string.eta_minutes, minutes)
}
