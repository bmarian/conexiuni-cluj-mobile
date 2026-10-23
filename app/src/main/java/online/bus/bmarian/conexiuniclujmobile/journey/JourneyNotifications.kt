package online.bus.bmarian.conexiuniclujmobile.journey

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.follow.FollowNotifications
import online.bus.bmarian.conexiuniclujmobile.follow.NotificationArt
import online.bus.bmarian.conexiuniclujmobile.follow.chipText
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ownHex
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ridesApart
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor

internal const val JOURNEY_NOTIFICATION_ID = 11
private const val JOURNEY_ARRIVED_ID = 13
private const val WALK_GREY = 0xFF8A9590.toInt()

class JourneyProgress(val parts: List<Part>, val doneMinutes: Double, val riding: Boolean) {
    class Part(val leg: Int, val ride: Boolean, val minutes: Double, val color: String?)

    companion object {
        fun parts(journey: Journey): List<Part> = buildList {
            journey.legs.forEachIndexed { i, leg ->
                if (leg.walkMinutes >= 0.5) add(Part(i, false, leg.walkMinutes, null))
                add(Part(i, true, leg.rideMinutes.takeIf { it > 0 } ?: (leg.stops.lastIndex * 2.0), leg.colors.firstOrNull()))
            }
            if (!journey.endsAtStop) add(Part(-1, false, journey.walkEndMinutes.coerceAtLeast(1.0), null))
        }
    }
}

object JourneyNotifications {
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    /** [alert] re-posts it on the alert channel so this one update buzzes and pops up. */
    fun ongoing(context: Context, journey: Journey, sync: JourneySync, progress: JourneyProgress, alert: Boolean = false): Notification {
        FollowNotifications.ensureChannels(context)
        val apart = ridesApart(journey.legs.map { it.lines })
        val hex = ownHex(sync.line, sync.color, apart)
        val color = routeColor(hex ?: journey.legs.first().let { ownHex(it.lines.firstOrNull(), it.colors.firstOrNull(), apart) }).toArgb()
        val builder = Notification.Builder(context, if (alert) FollowNotifications.ALERT_CHANNEL else FollowNotifications.FOLLOW_CHANNEL)
            .setSmallIcon(if (progress.riding) R.drawable.ic_bus else R.drawable.ic_walk)
            .setContentTitle(sync.step)
            .setContentText(sync.detail)
            .setColor(color)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setShowWhen(false)
            // Not bridged: the watch shows its own.
            .setLocalOnly(true)
            .setContentIntent(open(context, journey))
            .addAction(action(context, R.string.journey_next, JourneyActionReceiver.NEXT, 20))
            .addAction(action(context, R.string.journey_end, JourneyActionReceiver.END, 21))
        sync.line?.let { builder.setLargeIcon(NotificationArt.badge(context, it, hex)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(chip(context, sync))
            builder.setStyle(bar(context, journey, progress, sync, hex))
        }
        return builder.build()
    }

    private fun chip(context: Context, sync: JourneySync): String {
        val arrival = sync.arrivalAt
        if (arrival != null) {
            val nowMs = System.currentTimeMillis()
            return chipText(context, ((arrival - nowMs) / 60_000).toInt(), nowMs)
        }
        return sync.line ?: context.getString(R.string.journey_card_label)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun bar(context: Context, journey: Journey, progress: JourneyProgress, sync: JourneySync, hex: String?): Notification.ProgressStyle {
        val apart = ridesApart(journey.legs.map { it.lines })
        val total = progress.parts.sumOf { it.minutes }.coerceAtLeast(1.0)
        // Segments must sum to the bar length; the last one takes the rounding.
        val lengths = progress.parts.map { (it.minutes / total * 1000).toInt().coerceAtLeast(1) }.toMutableList()
        lengths[lengths.lastIndex] += 1000 - lengths.sum()
        val segments = progress.parts.mapIndexed { i, part ->
            // The current ride takes the colour of the bus actually coming.
            val line = journey.legs.getOrNull(part.leg)?.lines?.firstOrNull()
            val color = if (!part.ride) part.color else hex.takeIf { part.leg == sync.legIndex } ?: ownHex(line, part.color, apart)
            Notification.ProgressStyle.Segment(lengths[i].coerceAtLeast(1))
                .setColor(color?.let { routeColor(it).toArgb() } ?: WALK_GREY)
        }
        var at = 0
        val points = lengths.dropLast(1).map { length ->
            at += length
            Notification.ProgressStyle.Point(at)
        }
        return Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(segments)
            .setProgressPoints(points)
            .setProgressTrackerIcon(if (progress.riding) NotificationArt.bus(context, hex) else NotificationArt.walker(context))
            .setProgressEndIcon(NotificationArt.destination(context))
            .setProgress((progress.doneMinutes / total * 1000).toInt().coerceIn(0, 1000))
    }

    fun arrived(context: Context, journey: Journey) {
        val notification = Notification.Builder(context, FollowNotifications.FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.journey_arrived))
            .setContentText(journey.destination.name)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .build()
        runCatching { manager(context).notify(JOURNEY_ARRIVED_ID, notification) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun open(context: Context, journey: Journey): PendingIntent {
        return PendingIntent.getActivity(
            context, 22,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData("conexiunicluj://bus.bmarian.online/journey".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, label: Int, action: String, code: Int) = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_bus),
        context.getString(label),
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, JourneyActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).build()
}

class JourneyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NEXT -> JourneyManager.next(context)
            END -> JourneyManager.stop(context)
        }
    }

    companion object {
        const val NEXT = "online.bmarian.bus.JOURNEY_NEXT"
        const val END = "online.bmarian.bus.JOURNEY_END"
    }
}
