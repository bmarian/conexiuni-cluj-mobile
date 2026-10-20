package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import java.util.UUID

/** Shared with the watch app. */
@Serializable
data class Followed(
    val stopId: Int,
    val stopName: String,
    val routeId: Int,
    val tripId: String,
    val shortName: String,
    val color: String,
    val destination: String,
    // Saved before cities: Cluj.
    val city: String = Cities.DEFAULT,
)

/** Wire format shared with the watch app. */
@Serializable
data class FollowSync(
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    // Epoch ms.
    val arrivalAt: Long? = null,
    // "left", "none" or "stopped" once over.
    val end: String? = null,
    // Buzz for this update.
    val alert: Boolean = false,
    // Epoch ms to set off for the stop, while a reminder waits for the user to go.
    val leaveAt: Long? = null,
    val walkMinutes: Int? = null,
    // Epoch ms to go by, from the heads-up until it's time to leave.
    val leaveBy: Long? = null,
)

/** A reminder's departure: [atMs] is its timetable time at the stop. */
@Serializable
data class FollowTarget(val atMs: Long)

/** Rounded: the watch sends whole minutes and the clocks drift a few seconds. */
fun minutesUntil(atMs: Long, nowMs: Long): Int = ((atMs - nowMs + 30_000) / 60_000).toInt().coerceAtLeast(0)

data class FollowState(
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    val arrivalAt: Long? = null,
    val remote: Boolean = false,
    val target: FollowTarget? = null,
    val leaveAt: Long? = null,
    val walkMinutes: Int? = null,
    val leaveBy: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    fun toSync(end: String? = null, alert: Boolean = false) =
        FollowSync(id, followed, minutes, live, later, arrivalAt, end, alert, leaveAt, walkMinutes, leaveBy)

    fun matches(stopId: Int, routeId: Int, tripId: String, city: String = Cities.current.value) =
        followed.city == city && followed.stopId == stopId && followed.routeId == routeId && followed.tripId == tripId

    /** The bus at any of a station's platforms. */
    fun matches(stopIds: Collection<Int>, routeId: Int, tripId: String, city: String = Cities.current.value) =
        followed.city == city && followed.stopId in stopIds && followed.routeId == routeId && followed.tripId == tripId

    /** Counted down from the last reading, so it keeps moving between readings. */
    fun minutesAt(nowMs: Long): Int? = arrivalAt?.let { minutesUntil(it, nowMs) } ?: minutes
}

@Serializable
private class SavedFollow(val id: String, val followed: Followed, val target: FollowTarget? = null, val startedAt: Long)

private const val REMOTE_STALE_MS = 15 * 60_000L
private const val REMOTE_PAST_ARRIVAL_MS = 10 * 60_000L

object FollowManager {
    internal val mutableFollows = MutableStateFlow<List<FollowState>>(emptyList())
    val follows: StateFlow<List<FollowState>> = mutableFollows

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // A watch follow the watch stops talking about is dropped.
    private val expiries = mutableMapOf<String, Job>()

    fun find(stopId: Int, routeId: Int, tripId: String) = follows.value.firstOrNull { it.matches(stopId, routeId, tripId) }

    fun isFollowing(stopId: Int, routeId: Int, tripId: String) = find(stopId, routeId, tripId) != null

    /** False when the system won't let it start from the background. */
    fun start(context: Context, followed: Followed, target: FollowTarget? = null): Boolean {
        if (follows.value.any { it.matches(followed.stopId, followed.routeId, followed.tripId, followed.city) }) return true
        val state = FollowState(UUID.randomUUID().toString(), followed, target = target)
        mutableFollows.update { it + state }
        save(context)
        try {
            ContextCompat.startForegroundService(context, Intent(context, FollowService::class.java).putExtra(FollowService.EXTRA_ID, state.id))
        } catch (_: IllegalStateException) {
            ended(context, state.id)
            return false
        }
        WearLink.showOnWatch(context, "/following")
        return true
    }

    fun stop(context: Context, id: String) {
        val current = follows.value.firstOrNull { it.id == id } ?: return
        mutableFollows.update { list -> list.filterNot { it.id == id } }
        if (current.remote) {
            expiries.remove(id)?.cancel()
            FollowNotifications.clearMirror(context, id)
            WearLink.sendStop(context, id)
        } else {
            // The service drops it when it leaves the list.
            save(context)
        }
    }

    internal fun put(state: FollowState) {
        mutableFollows.update { list -> if (list.none { it.id == state.id }) list else list.map { if (it.id == state.id) state else it } }
    }

    internal fun ended(context: Context, id: String) {
        mutableFollows.update { list -> list.filterNot { it.id == id } }
        save(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("follow", Context.MODE_PRIVATE)

    private fun save(context: Context) {
        val local = follows.value.filterNot { it.remote }.map { SavedFollow(it.id, it.followed, it.target, it.startedAt) }
        prefs(context).edit { putString("follows", Api.json.encodeToString(local)) }
    }

    /** The local follows of a process the system killed, for the restarted service. */
    internal fun restore(context: Context): List<FollowState> {
        val saved = prefs(context).getString("follows", null)
            ?.let { runCatching { Api.json.decodeFromString<List<SavedFollow>>(it) }.getOrNull() }
            .orEmpty()
        val known = follows.value.map { it.id }.toSet()
        val restored = saved.filter { it.id !in known }.map { FollowState(it.id, it.followed, target = it.target, startedAt = it.startedAt) }
        mutableFollows.update { it + restored }
        return follows.value.filterNot { it.remote }
    }

    internal fun onRemoteState(context: Context, sync: FollowSync) {
        if (sync.end != null) {
            if (follows.value.any { it.id == sync.id && it.remote }) {
                expiries.remove(sync.id)?.cancel()
                mutableFollows.update { list -> list.filterNot { it.id == sync.id } }
                FollowNotifications.endMirror(context, sync)
            }
            return
        }
        if (follows.value.any { it.id == sync.id && !it.remote }) return
        val state = FollowState(
            sync.id, sync.followed, sync.minutes, sync.live, sync.later, sync.arrivalAt,
            remote = true, leaveAt = sync.leaveAt, walkMinutes = sync.walkMinutes, leaveBy = sync.leaveBy,
        )
        mutableFollows.update { list -> if (list.any { it.id == sync.id }) list.map { if (it.id == sync.id) state else it } else list + state }
        FollowNotifications.showMirror(context, state)
        val now = System.currentTimeMillis()
        expiries.remove(sync.id)?.cancel()
        expiries[sync.id] = scope.launch {
            delay(maxOf(REMOTE_STALE_MS, (sync.arrivalAt ?: 0) + REMOTE_PAST_ARRIVAL_MS - now))
            expiries.remove(sync.id)
            mutableFollows.update { list -> list.filterNot { it.id == sync.id } }
            FollowNotifications.clearMirror(context, sync.id)
        }
    }

    internal fun onRemoteStop(context: Context, id: String) {
        if (follows.value.any { it.id == id && !it.remote }) stop(context, id)
    }
}
