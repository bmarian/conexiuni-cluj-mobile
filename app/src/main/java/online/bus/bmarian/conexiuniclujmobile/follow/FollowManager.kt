package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import java.util.UUID

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

/** A reminder's departure: [atMs] is its timetable time at the stop. */
@Serializable
data class FollowTarget(val atMs: Long)

/** Rounded, so clocks a few seconds apart agree on the minute. */
fun minutesUntil(atMs: Long, nowMs: Long): Int = ((atMs - nowMs + 30_000) / 60_000).toInt().coerceAtLeast(0)

data class FollowState(
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    val arrivalAt: Long? = null,
    val target: FollowTarget? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
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

object FollowManager {
    internal val mutableFollows = MutableStateFlow<List<FollowState>>(emptyList())
    val follows: StateFlow<List<FollowState>> = mutableFollows

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
        return true
    }

    fun stop(context: Context, id: String) {
        if (follows.value.none { it.id == id }) return
        mutableFollows.update { list -> list.filterNot { it.id == id } }
        // The service drops it when it leaves the list.
        save(context)
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
        val local = follows.value.map { SavedFollow(it.id, it.followed, it.target, it.startedAt) }
        prefs(context).edit { putString("follows", Api.json.encodeToString(local)) }
    }

    /** The follows of a process the system killed, for the restarted service. */
    internal fun restore(context: Context): List<FollowState> {
        val saved = prefs(context).getString("follows", null)
            ?.let { runCatching { Api.json.decodeFromString<List<SavedFollow>>(it) }.getOrNull() }
            .orEmpty()
        val known = follows.value.map { it.id }.toSet()
        val restored = saved.filter { it.id !in known }.map { FollowState(it.id, it.followed, target = it.target, startedAt = it.startedAt) }
        mutableFollows.update { it + restored }
        return follows.value
    }
}
