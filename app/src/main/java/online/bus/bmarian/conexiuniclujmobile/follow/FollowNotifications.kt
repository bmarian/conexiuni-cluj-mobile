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

object FollowNotifications {
    const val FOLLOW_CHANNEL = "follow"
    const val ALERT_CHANNEL = "follow_alerts"

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

    /** [alert] re-posts it on the alert channel so this one update buzzes and pops up. */
    fun ongoing(context: Context, state: FollowState, alert: Boolean = false): Notification {
        ensureChannels(context)
        val followed = state.followed
        val minutes = state.minutes
        val nowMs = System.currentTimeMillis()
        val color = routeColor(followed.color).toArgb()
        val (title, text) = if (minutes == null) context.getString(R.string.follow_waiting) to null else arrivingAtText(context, followed.stopName, minutes, nowMs)
        val builder = Notification.Builder(context, if (alert) ALERT_CHANNEL else FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(LineIcon.of(context, followed.shortName, followed.color))
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
            builder.setShortCriticalText(if (minutes == null) followed.shortName else chipText(context, minutes, nowMs))
            builder.setStyle(progress(context, state, color))
        }
        return builder.build()
    }

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
            .setProgressTrackerIcon(trackerIcon(context, R.drawable.ic_bus))
            .setProgress(done)
    }

    fun ended(context: Context, followed: Followed, reason: String?) {
        val message = when (reason) {
            "left" -> context.getString(R.string.follow_ended, followed.stopName)
            "none" -> context.getString(R.string.follow_no_bus)
            else -> null
        }
        if (message == null) return
        ensureChannels(context)
        val done = Notification.Builder(context, FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(LineIcon.of(context, followed.shortName, followed.color))
            .setContentTitle(message)
            .setColor(routeColor(followed.color).toArgb())
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(openStop(context, followed.stopId))
            .build()
        runCatching { manager(context).notify(ENDED_NOTIFICATION_ID, done) }
    }

    fun showMirror(context: Context, state: FollowState) {
        val notification = ongoing(context, state)
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

class StopFollowingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)
        if (FollowManager.state.value?.id == id) return FollowManager.stop(context)
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
