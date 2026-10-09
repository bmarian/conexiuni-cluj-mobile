package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import java.util.UUID

/** One departure at one stop: a line in one direction. The same as the watch app's. */
@Serializable
data class Followed(
    val stopId: Int,
    val stopName: String,
    val routeId: Int,
    val tripId: String,
    val shortName: String,
    val color: String,
    val destination: String,
)

/**
 * What the device doing the following tells the other one, every time the countdown moves.
 * The watch app sends and reads the same JSON.
 */
@Serializable
data class FollowSync(
    // One follow, the same on both devices; a new follow on either side gets a new one.
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    // When the countdown reaches zero, in epoch milliseconds.
    val arrivalAt: Long? = null,
    // Set once it is over: "left", "none" (no more departures) or "stopped".
    val end: String? = null,
)

data class FollowState(
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    val arrivalAt: Long? = null,
    // Followed on the watch; this phone only shows its countdown, the way Maps mirrors navigation.
    val remote: Boolean = false,
) {
    fun toSync(end: String? = null) = FollowSync(id, followed, minutes, live, later, arrivalAt, end)
}

/** The bus being followed, here or on the watch: one at a time across both. */
object FollowManager {
    internal val mutableState = MutableStateFlow<FollowState?>(null)
    val state: StateFlow<FollowState?> = mutableState

    fun isFollowing(stopId: Int, routeId: Int, tripId: String) = state.value?.followed?.let {
        it.stopId == stopId && it.routeId == routeId && it.tripId == tripId
    } == true

    /** Follows a bus here; a watch in reach shows it at once. */
    fun start(context: Context, followed: Followed) {
        if (state.value?.remote == true) FollowNotifications.clearMirror(context)
        val id = UUID.randomUUID().toString()
        mutableState.value = FollowState(id, followed)
        val intent = Intent(context, FollowService::class.java)
            .putExtra(FollowService.EXTRA_FOLLOWED, Api.json.encodeToString(followed))
            .putExtra(FollowService.EXTRA_ID, id)
        ContextCompat.startForegroundService(context, intent)
        WearLink.showOnWatch(context, "/following")
    }

    /** Stops following, on both devices. */
    fun stop(context: Context) {
        val current = state.value ?: return
        mutableState.value = null
        if (current.remote) {
            FollowNotifications.clearMirror(context)
            WearLink.sendStop(context, current.id)
        } else {
            // The service tells the watch as it goes.
            context.stopService(Intent(context, FollowService::class.java))
        }
    }

    /** The watch is following a bus, or its countdown moved. */
    internal fun onRemoteState(context: Context, sync: FollowSync) {
        val current = state.value
        if (sync.end != null) {
            if (current?.remote == true && current.id == sync.id) {
                mutableState.value = null
                FollowNotifications.endMirror(context, sync)
            }
            return
        }
        // A new follow on the watch replaces the one here, as a new one here would.
        if (current != null && !current.remote && current.id != sync.id) {
            context.stopService(Intent(context, FollowService::class.java))
        }
        mutableState.value = FollowState(sync.id, sync.followed, sync.minutes, sync.live, sync.later, sync.arrivalAt, remote = true)
        FollowNotifications.showMirror(context, mutableState.value!!)
    }

    /** Stop was pressed on the watch. */
    internal fun onRemoteStop(context: Context, id: String) {
        val current = state.value ?: return
        if (!current.remote && current.id == id) stop(context)
    }
}
