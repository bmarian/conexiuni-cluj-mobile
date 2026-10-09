package online.bus.bmarian.conexiuniclujmobile.journey

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.follow.FollowNotifications

private const val REMINDER_NOTIFICATION_ID = 14

/**
 * "Remind me to leave" for a planned trip: an alarm at the time to set off, counting the walk
 * to the stop, that opens the plan again with its live times.
 */
object LeaveReminder {
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_PATH = "path"

    private const val PREFS = "leaveReminder"

    fun schedule(context: Context, atMs: Long, body: String, planPath: String) {
        // Kept so the alarm can be set again exactly once Android allows it.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("at", atMs)
            putString("body", body)
            putString("path", planPath)
        }
        val intent = Intent(context, LeaveReminderReceiver::class.java)
            .putExtra(EXTRA_TITLE, context.getString(R.string.remind_leave_now_title))
            .putExtra(EXTRA_BODY, body)
            .putExtra(EXTRA_PATH, planPath)
        val pending = PendingIntent.getBroadcast(context, 30, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Exact when Android allows it; otherwise the system may move it by a minute or two.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }
    }

    /** The reminder waiting to go off: when, and the plan it opens. */
    fun pending(context: Context): Pair<Long, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0L)
        if (at <= System.currentTimeMillis()) return null
        return at to (prefs.getString("path", null) ?: return null)
    }

    fun canBeExact(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The system page for "Alarms & reminders"; granting it sets the pending reminder again, exactly. */
    fun askExact(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun cancel(context: Context) {
        val pending = PendingIntent.getBroadcast(
            context, 30, Intent(context, LeaveReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pending != null) context.getSystemService(AlarmManager::class.java).cancel(pending)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    internal fun reschedule(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0L)
        if (at <= System.currentTimeMillis()) return
        schedule(context, at, prefs.getString("body", null).orEmpty(), prefs.getString("path", null) ?: "/")
    }

    internal fun show(context: Context, intent: Intent) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
        FollowNotifications.ensureChannels(context)
        val path = intent.getStringExtra(EXTRA_PATH) ?: "/"
        val open = PendingIntent.getActivity(
            context, 31,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData((BuildConfig.SITE_ORIGIN + path).toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, FollowNotifications.ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L)
            .setContentIntent(open)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(REMINDER_NOTIFICATION_ID, notification) }
    }
}

class LeaveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = LeaveReminder.show(context, intent)
}

/** Exact alarms were just allowed: the reminder waiting to go off is set again, on the minute. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) LeaveReminder.reschedule(context)
    }
}
