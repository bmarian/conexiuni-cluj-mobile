package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.data.VehicleStream
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.computeStopDepartures
import java.time.Instant
import java.time.LocalDateTime

private const val TICK_MS = 15_000L
private const val STOP_INFO_REFRESH_MS = 120_000L
private const val SOON_MINUTES = 2
private const val GIVE_UP_MS = 90 * 60_000L

class FollowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var current: FollowState? = null
    private var ended = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val followed = intent?.getStringExtra(EXTRA_FOLLOWED)
            ?.let { runCatching { Api.json.decodeFromString<Followed>(it) }.getOrNull() }
        val id = intent?.getStringExtra(EXTRA_ID)
        if (followed == null || id == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Restarted by the system: the state comes from the redelivered intent.
        val state = FollowManager.state.value?.takeIf { it.id == id } ?: FollowState(id, followed)
        FollowManager.mutableState.value = state
        current = state
        ended = false

        ServiceCompat.startForeground(
            this, FOLLOW_NOTIFICATION_ID, FollowNotifications.ongoing(this, state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        WearLink.sendState(this, state.toSync())
        job?.cancel()
        job = scope.launch { track(state) }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        scope.cancel()
        val state = current
        if (state != null && !ended) WearLink.sendState(this, state.toSync(end = "stopped"))
        if (state != null && FollowManager.state.value?.id == state.id) FollowManager.mutableState.value = null
        super.onDestroy()
    }

    private suspend fun track(start: FollowState) = coroutineScope {
        val followed = start.followed
        val repository = Services.repository
        val startedAt = System.currentTimeMillis()
        val info = MutableStateFlow(repository.cachedStopInfo(followed.stopId)?.only(followed.routeId))
        val shapes = runCatching { repository.shapes(listOf(followed.tripId)) }.getOrDefault(emptyMap())
        val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())
        val clock = MutableStateFlow(startedAt)

        launch {
            while (true) {
                runCatching { info.value = repository.stopInfo(followed.stopId).only(followed.routeId) }
                delay(STOP_INFO_REFRESH_MS)
            }
        }
        launch { VehicleStream.vehicles(listOf(followed.tripId)).collect { vehicles.value = it } }
        launch {
            while (true) {
                delay(TICK_MS)
                clock.value = System.currentTimeMillis()
            }
        }

        val progress = Progress()
        combine(info.filterNotNull(), vehicles, clock) { stop, buses, nowMs ->
            withContext(Dispatchers.Default) {
                val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
                computeStopDepartures(stop, shapes, buses, now, nowMs)
                    .firstOrNull { it.routeId == followed.routeId && it.tripId == followed.tripId } to nowMs
            }
        }.collect { (departure, nowMs) ->
            if (nowMs - startedAt > GIVE_UP_MS) return@collect end(start, "stopped")
            update(start, departure, nowMs, progress)
        }
    }

    private class Progress {
        var lastMinutes: Int? = null
        var alertedSoon = false
        var arrived = false
    }

    private fun update(start: FollowState, departure: StopDeparture?, nowMs: Long, progress: Progress) {
        val followed = start.followed
        val first = departure?.nextTimes?.firstOrNull() ?: return end(start, "none")
        val minutes = first.minutes
        if (progress.arrived && minutes > (progress.lastMinutes ?: 0) + 2) return end(start, "left")
        var alert = false
        if (minutes <= SOON_MINUTES && !progress.alertedSoon && minutes > 0) {
            progress.alertedSoon = true
            alert = true
        }
        if (minutes == 0 && !progress.arrived) {
            progress.arrived = true
            progress.alertedSoon = true
            alert = true
        }
        progress.lastMinutes = minutes

        val state = FollowState(
            id = start.id,
            followed = followed,
            minutes = minutes,
            live = !departure.isStatic,
            later = departure.nextTimes.drop(1).map { it.minutes },
            arrivalAt = nowMs + minutes * 60_000L,
        )
        current = state
        if (FollowManager.state.value?.id != start.id) return
        FollowManager.mutableState.value = state
        WearLink.sendState(this, state.toSync())
        getSystemService(NotificationManager::class.java)
            .notify(FOLLOW_NOTIFICATION_ID, FollowNotifications.ongoing(this, state, alert))
    }

    private fun end(state: FollowState, reason: String) {
        ended = true
        WearLink.sendState(this, (current ?: state).toSync(end = reason))
        FollowNotifications.ended(this, state.followed, reason)
        if (FollowManager.state.value?.id == state.id) FollowManager.mutableState.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        job?.cancel()
    }

    companion object {
        const val EXTRA_FOLLOWED = "followed"
        const val EXTRA_ID = "id"
    }
}

private fun StopInfo.only(routeId: Int) = copy(shapesInfo = shapesInfo.filter { it.routeId == routeId })
