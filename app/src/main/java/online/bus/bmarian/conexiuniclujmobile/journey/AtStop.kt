package online.bus.bmarian.conexiuniclujmobile.journey

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.computeStopDepartures
import online.bus.bmarian.conexiuniclujmobile.ui.stop.routeDestination
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val CHANNEL = "at_stop"
private const val RADIUS_M = 70f
private const val DWELL_MS = 60_000
private const val NOTIFICATION_TAG = "at-stop"

object AtStop {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 40,
        Intent(context, AtStopReceiver::class.java),
        // Mutable: the geofence API fills in the intent.
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun sync(context: Context, enabled: Boolean, stopIds: List<Int>, stops: List<Stop>) {
        val app = context.applicationContext
        scope.launch {
            val client = LocationServices.getGeofencingClient(app)
            runCatching { client.removeGeofences(pending(app)).await() }
            if (!enabled || !allowed(app)) return@launch
            val byId = stops.associateBy { it.stopId }
            val fences = stopIds.mapNotNull { byId[it] }.take(90).map { stop ->
                Geofence.Builder()
                    .setRequestId("${stop.stopId}")
                    .setCircularRegion(stop.lat, stop.lon, RADIUS_M)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setLoiteringDelay(DWELL_MS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            if (fences.isEmpty()) return@launch
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofences(fences)
                .build()
            runCatching { client.addGeofences(request, pending(app)).await() }
        }
    }

    internal suspend fun show(context: Context, stopId: Int) {
        val repository = Services.repository
        val info = runCatching { repository.stopInfo(stopId) }.getOrNull() ?: return
        val name = repository.stops.value.find { it.stopId == stopId }?.name ?: return
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.now(CLUJ_ZONE)
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val lines = computeStopDepartures(info, emptyMap(), emptyMap(), now, nowMs).take(5).map { d ->
            val times = d.nextTimes.take(2).joinToString(", ") { clock.format(now.plusMinutes(it.minutes.toLong())) }
            "${d.shortName} → ${routeDestination(d.longName)} · $times"
        }
        if (lines.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.at_stop_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            context, 41,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData("${WearLink.APP_SCHEME}://bus.bmarian.online/stop/$stopId".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.at_stop_title, name))
            .setContentText(lines.first())
            .setStyle(Notification.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L)
            .build()
        runCatching { manager.notify(NOTIFICATION_TAG, stopId, notification) }
    }

    internal fun hide(context: Context, stopId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_TAG, stopId)
    }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }
}

class AtStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toIntOrNull() }
        val app = context.applicationContext
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                val done = goAsync()
                AtStop.launch {
                    try {
                        ids.forEach { AtStop.show(app, it) }
                    } finally {
                        done.finish()
                    }
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { AtStop.hide(app, it) }
        }
    }
}

/** Fences don't survive reboots or updates; Services.init adds them back. */
class AtStopRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
