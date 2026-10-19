package online.bus.bmarian.conexiuniclujmobile.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.stopDepartures
import online.bus.bmarian.conexiuniclujmobile.ui.stop.routeDestination
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class StopWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val manager = GlanceAppWidgetManager(context)
        for (id in manager.getGlanceIds(StopWidget::class.java)) {
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val stopId = prefs[StopWidget.STOP_ID] ?: continue
            // Widgets from before cities are Cluj's.
            val city = prefs[StopWidget.CITY] ?: Cities.DEFAULT
            val rows = runCatching { departures(stopId, city) }.getOrNull() ?: continue
            updateAppWidgetState(context, id) {
                it[StopWidget.ROWS] = Api.json.encodeToString(rows)
                it[StopWidget.UPDATED] = System.currentTimeMillis()
            }
            StopWidget().update(context, id)
        }
        return Result.success()
    }

    private suspend fun departures(stopId: Int, city: String): List<WidgetRow> {
        val info = Services.repository.stopInfo(stopId, city)
        val live = withTimeoutOrNull(8_000) { LiveStream.once(listOf(stopId), emptyList(), city) }
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        return stopDepartures(info, live, now, nowMs).map { departure ->
            WidgetRow(
                line = departure.shortName,
                color = departure.color,
                destination = routeDestination(departure.longName),
                times = departure.nextTimes.mapIndexed { i, arrival ->
                    val time = StopWidget.CLOCK.format(now.plusMinutes(arrival.minutes.toLong()))
                    if (i == 0 && departure.isStatic) "~$time" else time
                },
                live = !departure.isStatic,
            )
        }
    }

    companion object {
        private const val PERIODIC = "stop-widgets"
        private const val NOW = "stop-widgets-now"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StopWidgetWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StopWidgetWorker>()
                .setConstraints(online)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
