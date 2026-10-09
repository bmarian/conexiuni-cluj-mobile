package online.bus.bmarian.conexiuniclujmobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Add departures widget" on a stop page: the launcher places it with this stop already chosen. */
object WidgetPinning {
    private const val EXTRA_STOP_ID = "stopId"
    private const val EXTRA_STOP_NAME = "stopName"
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun supported(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pinStop(context: Context, stopId: Int, name: String): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val placed = PendingIntent.getBroadcast(
            context, stopId,
            Intent(context, WidgetPinnedReceiver::class.java).putExtra(EXTRA_STOP_ID, stopId).putExtra(EXTRA_STOP_NAME, name),
            // The launcher adds the new widget's id to it.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return manager.requestPinAppWidget(ComponentName(context, StopWidgetReceiver::class.java), null, placed)
    }

    internal fun stopOf(intent: Intent): Pair<Int, String>? {
        val id = intent.getIntExtra(EXTRA_STOP_ID, -1).takeIf { it >= 0 } ?: return null
        return id to intent.getStringExtra(EXTRA_STOP_NAME).orEmpty()
    }
}

class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val (stopId, name) = WidgetPinning.stopOf(intent) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val app = context.applicationContext
        val done = goAsync()
        WidgetPinning.scope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(app, glanceId) {
                    it[StopWidget.STOP_ID] = stopId
                    it[StopWidget.STOP_NAME] = name
                }
                StopWidget().update(app, glanceId)
                StopWidgetWorker.schedule(app)
                StopWidgetWorker.refreshNow(app)
            } finally {
                done.finish()
            }
        }
    }
}
