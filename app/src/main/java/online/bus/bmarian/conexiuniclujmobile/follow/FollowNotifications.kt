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

internal const val FOLLOW_NOTIFICATION_ID = 7
private const val MIRROR_NOTIFICATION_ID = 9
private const val ENDED_NOTIFICATION_ID = 8
private const val ALERT_NOTIFICATION_ID = 10

/**
 * The followed bus in the notification shade and, from Android 16, as a Live Update chip in
 * the status bar, like Maps navigation. The same card whether the phone or the watch follows.
 */
object FollowNotifications {
    // The journey's notifications share these: one quiet channel for the countdowns, one that buzzes.
    const val FOLLOW_CHANNEL = "follow"
    const val ALERT_CHANNEL = "follow_alerts"

    // Where the countdown started, so the progress bar fills as the bus gets closer.
    private var progressFrom: Pair<String, Int>? = null

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

    fun ongoing(context: Context, state: FollowState, watch: Boolean): Notification {
        ensureChannels(context)
        val followed = state.followed
        val minutes = state.minutes
        val arrival = state.arrivalAt
        val nowMs = System.currentTimeMillis()
        val text = if (minutes == null) context.getString(R.string.follow_waiting) else arrivingText(context, minutes, nowMs)
        val where = when {
            state.remote -> context.getString(R.string.follow_from_watch, followed.stopName)
            watch -> context.getString(R.string.follow_on_watch_too, followed.stopName)
            else -> followed.stopName
        }
        val color = routeColor(followed.color).toArgb()
        val builder = Notification.Builder(context, FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("${followed.shortName} → ${followed.destination}")
            .setContentText(text)
            .setSubText(where)
            .setColor(color)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // The watch shows its own countdown; a bridged copy would be a second one.
            .setLocalOnly(true)
            .setContentIntent(openStop(context, followed.stopId))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_bus),
                    context.getString(R.string.stop_following),
                    stopIntent(context, state),
                ).build(),
            )
        val counting = minutes != null && minutes > 0 && arrival != null
        builder.setShowWhen(counting).setUsesChronometer(counting).setChronometerCountDown(counting)
        if (counting) builder.setWhen(arrival!!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Live Updates came with Android 16 QPR1; before it the card still shows the progress.
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(if (minutes == null) followed.shortName else chipText(context, minutes, nowMs))
            builder.setStyle(progress(context, state, color))
        }
        return builder.build()
    }

    // A bar from when the follow started to the bus at the stop, the bus icon riding along it.
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun progress(context: Context, state: FollowState, color: Int): Notification.ProgressStyle {
        val minutes = state.minutes
        val from = progressFrom?.takeIf { it.first == state.id }?.second
        val start = when {
            minutes == null -> null
            from == null || minutes > from -> minutes.coerceAtLeast(1)
            else -> from
        }
        if (start != null) progressFrom = state.id to start
        val done = if (start == null || minutes == null) 0 else ((start - minutes.coerceAtLeast(0)) * 100 / start).coerceIn(0, 100)
        return Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(color)))
            .setProgressPoints(listOf(Notification.ProgressStyle.Point(100).setColor(color)))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_bus))
            .setProgress(done)
    }

    /** Two minutes out and at the stop: a heads-up, like the watch's buzz. */
    fun alert(context: Context, followed: Followed, minutes: Int) {
        ensureChannels(context)
        val text = if (minutes <= 0) context.getString(R.string.follow_now)
        else context.getString(R.string.follow_arriving, context.getString(R.string.eta_minutes, minutes))
        val notification = Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("${followed.shortName} → ${followed.destination}")
            .setContentText(text)
            .setSubText(followed.stopName)
            .setColor(routeColor(followed.color).toArgb())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60_000L)
            .setContentIntent(openStop(context, followed.stopId))
            .build()
        runCatching { manager(context).notify(ALERT_NOTIFICATION_ID, notification) }
    }

    fun ended(context: Context, followed: Followed, reason: String?) {
        val message = when (reason) {
            "left" -> context.getString(R.string.follow_ended, followed.stopName)
            "none" -> context.getString(R.string.follow_no_bus)
            else -> null
        }
        manager(context).cancel(ALERT_NOTIFICATION_ID)
        if (message == null) return
        ensureChannels(context)
        val done = Notification.Builder(context, FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("${followed.shortName} → ${followed.destination}")
            .setContentText(message)
            .setColor(routeColor(followed.color).toArgb())
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(openStop(context, followed.stopId))
            .build()
        runCatching { manager(context).notify(ENDED_NOTIFICATION_ID, done) }
    }

    /** The watch's follow, shown here. Updates come every few seconds while it runs. */
    fun showMirror(context: Context, state: FollowState) {
        val notification = ongoing(context, state, watch = false)
        // Nobody to update it if the watch goes out of reach: it goes once the bus should have come.
        val left = (state.arrivalAt ?: System.currentTimeMillis()) - System.currentTimeMillis()
        val timed = Notification.Builder.recoverBuilder(context, notification)
            .setTimeoutAfter(left.coerceAtLeast(0) + 5 * 60_000L)
            .build()
        runCatching { manager(context).notify(MIRROR_NOTIFICATION_ID, timed) }
    }

    fun endMirror(context: Context, sync: FollowSync) {
        clearMirror(context)
        ended(context, sync.followed, sync.end)
    }

    fun clearMirror(context: Context) {
        manager(context).cancel(MIRROR_NOTIFICATION_ID)
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
        context, 2,
        Intent(context, StopFollowingReceiver::class.java)
            .putExtra(StopFollowingReceiver.EXTRA_ID, state.id)
            .putExtra(StopFollowingReceiver.EXTRA_REMOTE, state.remote),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The notification's Stop button, for a follow here or on the watch. */
class StopFollowingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)
        if (FollowManager.state.value?.id == id) return FollowManager.stop(context)
        // The app was closed since: the watch's countdown is still up, and the watch still following.
        if (id != null && intent.getBooleanExtra(EXTRA_REMOTE, false)) {
            FollowNotifications.clearMirror(context)
            WearLink.sendStop(context, id)
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_REMOTE = "remote"
    }
}
