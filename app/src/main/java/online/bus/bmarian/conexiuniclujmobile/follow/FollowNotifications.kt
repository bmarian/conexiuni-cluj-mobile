package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import androidx.compose.ui.graphics.toArgb

private const val DONE_SHOWN_MS = 2 * 60_000L
private const val FIRST_SLOT = 100

object FollowNotifications {
    const val FOLLOW_CHANNEL = "follow"
    const val ALERT_CHANNEL = "follow_alerts"

    private val progressFrom = mutableMapOf<String, Int>()
    private val slots = mutableMapOf<String, Int>()

    /** Each follow's card gets its own notification id. */
    @Synchronized
    fun slot(id: String): Int = slots.getOrPut(id) { FIRST_SLOT + slots.size }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
        val manager = manager(context)
        if (manager.getNotificationChannel(FOLLOW_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(FOLLOW_CHANNEL, context.getString(R.string.follow_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        if (manager.getNotificationChannel(ALERT_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, context.getString(R.string.follow_alerts_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 120, 120)
                },
            )
        }
    }

    /** [alert] re-posts it on the alert channel so this one update buzzes and pops up. */
    fun ongoing(context: Context, state: FollowState, alert: Boolean = false): Notification {
        ensureChannels(context)
        val followed = state.followed
        val minutes = state.minutes
        val nowMs = System.currentTimeMillis()
        val color = routeColor(followed.color).toArgb()
        val leaveAt = state.leaveAt?.takeIf { minutes != null }
        val (title, text) = when {
            minutes == null -> context.getString(R.string.follow_waiting) to null
            leaveAt != null -> leaveText(context, followed.shortName, minutes, leaveAt, state.walkMinutes, nowMs)
            else -> arrivingAtText(context, followed.stopName, minutes, nowMs)
        }
        val builder = Notification.Builder(context, if (alert) ALERT_CHANNEL else FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(NotificationArt.badge(context, followed.shortName, followed.color))
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setShowWhen(false)
            // Not bridged: the watch shows its own.
            .setLocalOnly(true)
            .setContentIntent(openStop(context, followed.stopId))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_bus),
                    context.getString(R.string.stop_following),
                    stopIntent(context, state),
                ).build(),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Live Updates need Android 16 QPR1.
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(
                when {
                    minutes == null -> followed.shortName
                    leaveAt != null -> leaveChip(context, leaveAt, nowMs)
                    else -> chipText(context, minutes, nowMs)
                },
            )
            val done = percentDone(state)
            builder.setStyle(
                if (leaveAt == null) progress(context, state.followed, color, done)
                else walking(context, color, done, leavePoint(state.id, leadMinutes(state.walkMinutes))),
            )
        }
        return builder.build()
    }

    private fun percentDone(state: FollowState): Int {
        val minutes = state.minutes ?: return 0
        val from = progressFrom[state.id]
        val start = if (from == null || minutes > from) minutes.coerceAtLeast(1) else from
        progressFrom[state.id] = start
        return ((start - minutes.coerceAtLeast(0)) * 100 / start).coerceIn(0, 100)
    }

    /** Where on the bar it's time to leave: [lead] minutes before the bus. */
    private fun leavePoint(id: String, lead: Int): Int? {
        val start = progressFrom[id] ?: return null
        return ((start - lead) * 100 / start).takeIf { it in 1..99 }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun walking(context: Context, color: Int, done: Int, leave: Int?): Notification.ProgressStyle =
        Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(color)))
            .setProgressPoints(listOfNotNull(leave?.let { Notification.ProgressStyle.Point(it).setColor(color) }))
            .setProgressTrackerIcon(NotificationArt.walker(context))
            .setProgressEndIcon(NotificationArt.stop(context))
            .setProgress(done)

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun progress(context: Context, followed: Followed, color: Int, done: Int): Notification.ProgressStyle =
        Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(color)))
            .setProgressTrackerIcon(NotificationArt.bus(context, followed.color))
            .setProgressEndIcon(NotificationArt.stop(context))
            .setProgress(done)

    /** The follow card's final state, posted over it once the bus has come. Null when it should just go. */
    fun done(context: Context, followed: Followed, reason: String?): Notification? {
        val title = when (reason) {
            "left" -> context.getString(R.string.follow_done, followed.stopName)
            "none" -> context.getString(R.string.follow_no_bus)
            else -> return null
        }
        ensureChannels(context)
        val color = routeColor(followed.color).toArgb()
        val builder = Notification.Builder(context, FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(NotificationArt.badge(context, followed.shortName, followed.color))
            .setContentTitle(title)
            .setColor(color)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(DONE_SHOWN_MS)
            .setContentIntent(openStop(context, followed.stopId))
        if (reason == "left" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) builder.setStyle(progress(context, followed, color, 100))
        return builder.build()
    }

    /** For a reminder that can't start following from the background: one nudge, no live card. */
    fun nudge(context: Context, followed: Followed, atMs: Long, walk: Int?) {
        ensureChannels(context)
        val nowMs = System.currentTimeMillis()
        val minutes = ((atMs - nowMs) / 60_000).toInt().coerceAtLeast(0)
        val leaveAt = atMs - leadMinutes(walk) * 60_000L
        val (title, text) = leaveText(context, followed.shortName, minutes, leaveAt, walk, nowMs)
        val notification = Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(NotificationArt.badge(context, followed.shortName, followed.color))
            .setContentTitle(title)
            .setContentText(text)
            .setColor(routeColor(followed.color).toArgb())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter((atMs - nowMs).coerceAtLeast(0) + 5 * 60_000L)
            .setContentIntent(openStop(context, followed.stopId))
            .build()
        runCatching { manager(context).notify(slot("nudge-${followed.stopId}-${followed.tripId}"), notification) }
    }

    /** Answers a start request that has nothing left to follow. */
    fun placeholder(context: Context): Notification {
        ensureChannels(context)
        return Notification.Builder(context, FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.follow_waiting))
            .setLocalOnly(true)
            .build()
    }

    fun showMirror(context: Context, state: FollowState) {
        val notification = ongoing(context, state)
        val left = (state.arrivalAt ?: System.currentTimeMillis()) - System.currentTimeMillis()
        val timed = Notification.Builder.recoverBuilder(context, notification)
            .setTimeoutAfter(left.coerceAtLeast(0) + 5 * 60_000L)
            .build()
        runCatching { manager(context).notify(slot(state.id), timed) }
    }

    fun endMirror(context: Context, sync: FollowSync) {
        val done = done(context, sync.followed, sync.end) ?: return clearMirror(context, sync.id)
        runCatching { manager(context).notify(slot(sync.id), done) }
    }

    fun clearMirror(context: Context, id: String) {
        manager(context).cancel(slot(id))
    }

    private fun openStop(context: Context, stopId: Int): PendingIntent = PendingIntent.getActivity(
        context, stopId,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("${BuildConfig.SITE_ORIGIN}/stop/$stopId"))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(context: Context, state: FollowState): PendingIntent = PendingIntent.getBroadcast(
        context, slot(state.id),
        Intent(context, StopFollowingReceiver::class.java)
            .putExtra(StopFollowingReceiver.EXTRA_ID, state.id)
            .putExtra(StopFollowingReceiver.EXTRA_REMOTE, state.remote),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class StopFollowingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        if (FollowManager.follows.value.any { it.id == id }) return FollowManager.stop(context, id)
        if (intent.getBooleanExtra(EXTRA_REMOTE, false)) {
            FollowNotifications.clearMirror(context, id)
            WearLink.sendStop(context, id)
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_REMOTE = "remote"
    }
}
