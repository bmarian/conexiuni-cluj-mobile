package online.bus.bmarian.conexiuniclujmobile.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.logic.changeDirection
import online.bus.bmarian.conexiuniclujmobile.logic.changeSummary
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL = "route-changes"
private const val PREFS = "push"
private const val WORK = "route-changes"

/**
 * The website gets a web push when a followed line changes. The server only speaks to
 * browsers, so the app asks it itself every hour and posts the same notification: one per
 * line, the newest change, only between 08:00 and 20:00 in Cluj.
 */
class RouteChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Services.settings
        if (!settings.pushNotifications.value || !settings.showTimetableChanges.value) return Result.success()
        val followed = Services.routeUpdates.followed.value
        if (followed.isEmpty()) return Result.success()
        val hour = ZonedDateTime.now(ZoneId.of("Europe/Bucharest")).hour
        if (hour < 8 || hour >= 20) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        val changes = try {
            val routes = followed.sorted().joinToString(",") { Api.encode(it) }
            Api.getFresh<List<RouteChange>>("route-changes?routes=$routes")
        } catch (_: Exception) {
            return Result.retry()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong("since", System.currentTimeMillis())
        val notified = prefs.getStringSet("notified", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toMutableSet()
        val fresh = changes.filter { it.detectedAt > since && it.id !in notified }
        // Only the newest pending change of each line goes out.
        val newestPerLine = fresh.groupBy { it.routeShortName }.mapValues { (_, list) -> list.maxBy { it.detectedAt } }
        for ((_, change) in newestPerLine) notify(change)
        notified += fresh.map { it.id }
        prefs.edit { putStringSet("notified", notified.sortedDescending().take(500).map { "$it" }.toSet()) }
        return Result.success()
    }

    private fun notify(change: RouteChange) {
        val context = applicationContext
        ensureChannel(context)
        val target = if (change.routeId != 0) "/route/${change.routeId}/${changeDirection(change)}?change=${change.id}" else "/"
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse(BuildConfig.SITE_ORIGIN + target))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, change.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.push_line, change.routeShortName, changeTitle(change, context)))
            .setContentText(changeSummary(change, context))
            .setWhen(change.detectedAt)
            .setShowWhen(true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            // The tag per line replaces an older notice about the same line.
            NotificationManagerCompat.from(context).notify("route-change-${change.routeShortName}", 1, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.w_timetable_changes), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RouteChangeWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Notifications start with what changes from now on, like a fresh web push subscription. */
        fun markEnabled(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong("since", System.currentTimeMillis()) }
            ensureChannel(context)
        }
    }
}
