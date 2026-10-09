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
private const val DONE_SHOWN_MS = 2 * 60_000L

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
            builder.setShortCriticalText(if (minutes == null) followed.shortName else chipText(context, minutes, nowMs))
            builder.setStyle(progress(context, state.followed, color, percentDone(state)))
        }
        return builder.build()
    }

    private fun percentDone(state: FollowState): Int {
        val minutes = state.minutes ?: return 0
        val from = progressFrom?.takeIf { it.first == state.id }?.second
        val start = if (from == null || minutes > from) minutes.coerceAtLeast(1) else from
        progressFrom = state.id to start
        return ((start - minutes.coerceAtLeast(0)) * 100 / start).coerceIn(0, 100)
    }

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

    fun showMirror(context: Context, state: FollowState) {
        val notification = ongoing(context, state)
        val left = (state.arrivalAt ?: System.currentTimeMillis()) - System.currentTimeMillis()
        val timed = Notification.Builder.recoverBuilder(context, notification)
            .setTimeoutAfter(left.coerceAtLeast(0) + 5 * 60_000L)
            .build()
        runCatching { manager(context).notify(MIRROR_NOTIFICATION_ID, timed) }
    }

    fun endMirror(context: Context, sync: FollowSync) {
        val done = done(context, sync.followed, sync.end) ?: return clearMirror(context)
        runCatching { manager(context).notify(MIRROR_NOTIFICATION_ID, done) }
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
