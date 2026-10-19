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
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.Links
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.cityPrefs
import online.bus.bmarian.conexiuniclujmobile.logic.changeDirection
import online.bus.bmarian.conexiuniclujmobile.logic.changeSummary
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL = "route-changes"
private const val PREFS = "push"
private const val WORK = "route-changes"

/** The server only sends web push, so the app polls, each city with followed lines. */
class RouteChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cities = (Cities.list.value.map { it.slug } + Cities.DEFAULT).distinct()
            .associateWith { Services.routeUpdates.followedIn(it) }
            .filterValues { it.isNotEmpty() }
        if (cities.isEmpty()) return Result.success()
        val hour = ZonedDateTime.now(ZoneId.of("Europe/Bucharest")).hour
        if (hour < 8 || hour >= 20) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        var failed = false
        for ((city, followed) in cities) if (!check(city, followed)) failed = true
        return if (failed) Result.retry() else Result.success()
    }

    /** False when the server could not be asked. */
    private suspend fun check(city: String, followed: List<String>): Boolean {
        val prefs = cityPrefs(applicationContext, PREFS, city)
        // From the first look on; what came before isn't news.
        if (!prefs.contains("since")) {
            prefs.edit { putLong("since", System.currentTimeMillis()) }
            ensureChannel(applicationContext)
            return true
        }
        val changes = try {
            val routes = followed.sorted().joinToString(",") { Api.encode(it) }
            Api.getFresh<List<RouteChange>>("route-changes?routes=$routes", city)
        } catch (_: Exception) {
            return false
        }

        val since = prefs.getLong("since", System.currentTimeMillis())
        val notified = prefs.getStringSet("notified", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toMutableSet()
        val fresh = changes.filter { it.detectedAt > since && it.id !in notified }
        val newestPerLine = fresh.groupBy { it.routeShortName }.mapValues { (_, list) -> list.maxBy { it.detectedAt } }
        for ((_, change) in newestPerLine) notify(city, change)
        notified += fresh.map { it.id }
        prefs.edit { putStringSet("notified", notified.sortedDescending().take(500).map { "$it" }.toSet()) }
        return true
    }

    private fun notify(city: String, change: RouteChange) {
        val context = applicationContext
        ensureChannel(context)
        val target = if (change.routeId != 0) "/route/${change.routeId}/${changeDirection(change)}?change=${change.id}" else "/"
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse(Links.web(target, city)))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, "$city/${change.id}".hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
            NotificationManagerCompat.from(context).notify("route-change-$city-${change.routeShortName}", 1, notification)
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
    }
}
