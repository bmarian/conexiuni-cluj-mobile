package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.Notification
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.stopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.exactMinutesOfDay
import online.bus.bmarian.conexiuniclujmobile.logic.minutesUntil
import online.bus.bmarian.conexiuniclujmobile.logic.scheduledAtStop
import online.bus.bmarian.conexiuniclujmobile.logic.targetMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.walkMinutes
import java.time.Instant
import kotlin.math.roundToInt
import java.time.LocalDateTime

private const val TICK_MS = 15_000L
private const val LIVE_WAIT_MS = 6_000L
private const val STOP_INFO_REFRESH_MS = 120_000L
private const val LOCATION_MS = 30_000L
private const val SOON_MINUTES = 2
private const val GIVE_UP_MS = 90 * 60_000L

/** Follows every bus in [FollowManager.follows] that's local, each on its own card. */
class FollowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private val latest = mutableMapOf<String, FollowState>()
    private val cards = mutableMapOf<String, Notification>()
    // The follow whose card keeps the service in the foreground.
    private var foreground: String? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Stopped from the app or a notification: it leaves the list.
        scope.launch {
            FollowManager.follows.collect { list ->
                val ids = list.mapTo(HashSet()) { it.id }
                jobs.keys.filter { it !in ids }.forEach { finish(it, "stopped", done = null) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getStringExtra(EXTRA_ID)
        // Restarted by the system after its process was killed: pick the follows back up.
        val states = if (id == null) FollowManager.restore(this) else FollowManager.follows.value.filter { it.id == id && !it.remote }
        states.forEach(::begin)
        if (jobs.isEmpty()) {
            // Stopped before it started; the system still wants it in the foreground once.
            ServiceCompat.startForeground(this, FollowNotifications.slot("placeholder"), FollowNotifications.placeholder(this), TYPE)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        for ((id, state) in latest) {
            WearLink.sendState(this, state.toSync(end = "stopped"))
            FollowManager.ended(this, id)
        }
        super.onDestroy()
    }

    private val manager get() = getSystemService(NotificationManager::class.java)

    private fun begin(state: FollowState) {
        if (state.id in jobs) return
        val card = FollowNotifications.ongoing(this, state)
        cards[state.id] = card
        latest[state.id] = state
        val shown = foreground
        if (shown == null) {
            ServiceCompat.startForeground(this, FollowNotifications.slot(state.id), card, TYPE)
            foreground = state.id
        } else {
            // Every start request has to be answered with startForeground.
            ServiceCompat.startForeground(this, FollowNotifications.slot(shown), cards.getValue(shown), TYPE)
            manager.notify(FollowNotifications.slot(state.id), card)
        }
        WearLink.sendState(this, state.toSync())
        jobs[state.id] = scope.launch { track(state) }
    }

    private suspend fun track(start: FollowState) = coroutineScope {
        val followed = start.followed
        val target = start.target
        val repository = Services.repository
        val info = MutableStateFlow(repository.cachedStopInfo(followed.stopId)?.only(followed.routeId))
        // Nothing is judged before the first live snapshot, so a bus the timetable still shows as due isn't taken as arrived.
        val live = MutableStateFlow<LiveState?>(null)
        val ready = MutableStateFlow(false)
        val clock = MutableStateFlow(System.currentTimeMillis())
        val here = MutableStateFlow<Fix?>(null)

        launch {
            while (true) {
                runCatching { info.value = repository.stopInfo(followed.stopId).only(followed.routeId) }
                delay(STOP_INFO_REFRESH_MS)
            }
        }
        launch {
            LiveStream.live(listOf(followed.stopId), emptyList()).collect {
                live.value = it
                ready.value = true
            }
        }
        launch {
            delay(LIVE_WAIT_MS)
            ready.value = true
        }
        launch {
            while (true) {
                delay(TICK_MS)
                clock.value = System.currentTimeMillis()
            }
        }
        if (target != null) {
            launch {
                here.value = UserLocation.current(this@FollowService) ?: UserLocation.recent(this@FollowService)
                UserLocation.updates(this@FollowService, LOCATION_MS).catch {}.collect { fix -> if (fix != null) here.value = fix }
            }
        }

        val progress = Progress()
        combine(info.filterNotNull(), live, ready.filter { it }, clock) { stop, state, _, nowMs ->
            withContext(Dispatchers.Default) { reading(stop, state, followed, target, nowMs) to nowMs }
        }.collect { (reading, nowMs) ->
            if (nowMs - start.startedAt > GIVE_UP_MS) return@collect end(start.id, "stopped")
            update(start, reading, here.value, nowMs, progress)
        }
    }

    private class Reading(val minutes: Int, val live: Boolean, val later: List<Int>)

    private fun reading(stop: StopInfo, live: LiveState?, followed: Followed, target: FollowTarget?, nowMs: Long): Reading? {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        val departure = stopDepartures(stop, live, now, nowMs)
            .firstOrNull { it.routeId == followed.routeId && it.tripId == followed.tripId }
        if (target == null) {
            val first = departure?.nextTimes?.firstOrNull() ?: return null
            return Reading(first.minutes, !departure.isStatic, departure.nextTimes.drop(1).map { it.minutes })
        }
        // A reminder follows its own departure, not whichever comes next.
        val scheduled = scheduledAtStop(stop, followed.routeId, followed.tripId, now)
        val slot = exactMinutesOfDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(target.atMs), CLUJ_ZONE))
        val live = departure?.nextTimes?.firstOrNull()?.takeIf { it.isLive }?.minutes
        val minutes = targetMinutes(scheduled, live, minutesUntil(slot, exactMinutesOfDay(now)), stop.lateGraceMin) ?: return null
        return Reading(minutes, minutes == live, scheduled.filter { it > minutes + stop.lateGraceMin }.take(2).map { it.roundToInt() })
    }

    private class Progress {
        var lastMinutes: Int? = null
        var alertedSoon = false
        var arrived = false
        var toldToLeave = false
    }

    private fun update(start: FollowState, reading: Reading?, here: Fix?, nowMs: Long, progress: Progress) {
        val id = start.id
        val target = start.target
        if (reading == null) return end(id, if (target != null) "left" else "none")
        val minutes = reading.minutes
        // A jump after the bus was close means it passed, even if no reading caught it at 0.
        if (progress.alertedSoon && minutes > (progress.lastMinutes ?: 0) + 2) return end(id, "left")
        // A plain follow doesn't buzz on its first reading: the user just asked.
        val opening = progress.lastMinutes == null && target == null
        var alert = false
        var leaveAt: Long? = null
        var walk: Int? = null
        var ring = false
        if (target != null) {
            val minutesToWalk = here?.let { walkMinutes(it.lat, it.lon, target.lat, target.lon) }
            val leaveIn = minutes - leadMinutes(minutesToWalk)
            if (leaveIn <= 0 && !progress.toldToLeave) {
                progress.toldToLeave = true
                alert = true
                if (Services.settings.fullScreenAlarms.value) {
                    val (title, text) = leaveText(this, start.followed.shortName, minutes, nowMs, minutesToWalk, nowMs)
                    ReminderAlarm.ring(this, start.followed, nowMs + minutes * 60_000L, title, text)
                    ring = true
                }
            }
            // Until the bus is close or the user is already at the stop.
            if (minutesToWalk != 0 && minutes > SOON_MINUTES) {
                leaveAt = nowMs + leaveIn * 60_000L
                walk = minutesToWalk
            }
        }
        if (minutes <= SOON_MINUTES && !progress.alertedSoon) {
            progress.alertedSoon = true
            alert = alert || (!opening && minutes > 0)
        }
        if (minutes == 0 && !progress.arrived) {
            progress.arrived = true
            alert = !opening
        }
        progress.lastMinutes = minutes

        if (id !in jobs) return
        val state = start.copy(
            minutes = minutes,
            live = reading.live,
            later = reading.later,
            arrivalAt = nowMs + minutes * 60_000L,
            leaveAt = leaveAt,
            walkMinutes = walk,
        )
        latest[id] = state
        FollowManager.put(state)
        WearLink.sendState(this, state.toSync(alert = alert))
        val card = FollowNotifications.ongoing(this, state, alert && !ring)
        cards[id] = card
        manager.notify(FollowNotifications.slot(id), card)
    }

    private fun end(id: String, reason: String) {
        val state = latest[id] ?: return
        finish(id, reason, FollowNotifications.done(this, state.followed, reason))
    }

    /** [done] is posted over the card, or the card just goes. */
    private fun finish(id: String, reason: String, done: Notification?) {
        val job = jobs.remove(id) ?: return
        latest.remove(id)?.let { WearLink.sendState(this, it.toSync(end = reason)) }
        cards.remove(id)
        FollowManager.ended(this, id)
        val slot = FollowNotifications.slot(id)
        val next = jobs.keys.firstOrNull()
        when {
            next == null -> {
                foreground = null
                if (done == null) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                } else {
                    // Detaching re-posts the service's own copy, so the final state goes through it.
                    ServiceCompat.startForeground(this, slot, done, TYPE)
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                }
                stopSelf(lastStartId)
            }
            foreground == id -> {
                foreground = next
                ServiceCompat.startForeground(this, FollowNotifications.slot(next), cards.getValue(next), TYPE)
                // Moving the service off the card cancels it, so the final state goes up as its own.
                if (done != null) manager.notify(FollowNotifications.slot("$id-done"), done)
            }
            done == null -> manager.cancel(slot)
            else -> manager.notify(slot, done)
        }
        job.cancel()
    }

    companion object {
        const val EXTRA_ID = "id"
        private const val TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }
}

private fun StopInfo.only(routeId: Int) = copy(shapesInfo = shapesInfo.filter { it.routeId == routeId })
