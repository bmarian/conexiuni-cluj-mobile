package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.toArgb
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor

object ReminderAlarm {
    const val CHANNEL = "alarms"
    const val ID = 99
    const val RING_MS = 5 * 60_000L

    private var tone: Ringtone? = null
    private var buzz: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val silence = Runnable { quiet() }
    // Ringtones only loop on Android 9+.
    private val replay = object : Runnable {
        override fun run() {
            val tone = tone ?: return
            if (!tone.isPlaying) tone.play()
            handler.postDelayed(this, 1_000)
        }
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(context: Context) {
        val manager = manager(context)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        // Channel sounds play as notifications, not alarms, so the alarm plays its own.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun ring(context: Context, followed: Followed, busAt: Long, title: String, text: String?, journey: Boolean = false) {
        ensureChannel(context)
        val screen = PendingIntent.getActivity(
            context, ID,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_JOURNEY, journey)
                .putExtra(AlarmActivity.EXTRA_TITLE, title)
                .putExtra(AlarmActivity.EXTRA_TEXT, text)
                .putExtra(AlarmActivity.EXTRA_LINE, followed.shortName)
                .putExtra(AlarmActivity.EXTRA_COLOR, followed.color)
                .putExtra(AlarmActivity.EXTRA_DESTINATION, followed.destination)
                .putExtra(AlarmActivity.EXTRA_STOP, followed.stopId)
                .putExtra(AlarmActivity.EXTRA_STOP_NAME, followed.stopName)
                .putExtra(AlarmActivity.EXTRA_AT, busAt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, ID, Intent(context, AlarmStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setLargeIcon(NotificationArt.badge(context, followed.shortName, followed.color))
            .setContentTitle(title)
            .setContentText(text)
            .setColor(routeColor(followed.color).toArgb())
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setDeleteIntent(stop)
            .setOngoing(true)
            .setTimeoutAfter(RING_MS)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_bus), context.getString(R.string.alarm_stop), stop).build(),
            )
            .build()
        runCatching { manager(context).notify(ID, notification) }
        sound(context.applicationContext)
    }

    private fun sound(context: Context) {
        quiet()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        tone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true else handler.post(replay)
            play()
        }
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        buzz = vibrator(context).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION") vibrate(pattern, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
        handler.postDelayed(silence, RING_MS)
    }

    private fun vibrator(context: Context): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    private fun quiet() {
        tone?.stop()
        tone = null
        buzz?.cancel()
        buzz = null
        handler.removeCallbacks(silence)
        handler.removeCallbacks(replay)
    }

    fun stop(context: Context) {
        manager(context).cancel(ID)
        quiet()
    }

    fun ringing(context: Context) = manager(context).activeNotifications.any { it.id == ID }
}

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ReminderAlarm.stop(context)
}
