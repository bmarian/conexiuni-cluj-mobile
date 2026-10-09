package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
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
)

data class FollowState(
    val id: String,
    val followed: Followed,
    val minutes: Int? = null,
    val live: Boolean = false,
    val later: List<Int> = emptyList(),
    val arrivalAt: Long? = null,
    val remote: Boolean = false,
) {
    fun toSync(end: String? = null, alert: Boolean = false) = FollowSync(id, followed, minutes, live, later, arrivalAt, end, alert)
}

object FollowManager {
    internal val mutableState = MutableStateFlow<FollowState?>(null)
    val state: StateFlow<FollowState?> = mutableState

    fun isFollowing(stopId: Int, routeId: Int, tripId: String) = state.value?.followed?.let {
        it.stopId == stopId && it.routeId == routeId && it.tripId == tripId
    } == true

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

    fun stop(context: Context) {
        val current = state.value ?: return
        mutableState.value = null
        if (current.remote) {
            FollowNotifications.clearMirror(context)
            WearLink.sendStop(context, current.id)
        } else {
            context.stopService(Intent(context, FollowService::class.java))
        }
    }

    internal fun onRemoteState(context: Context, sync: FollowSync) {
        val current = state.value
        if (sync.end != null) {
            if (current?.remote == true && current.id == sync.id) {
                mutableState.value = null
                FollowNotifications.endMirror(context, sync)
            }
            return
        }
        if (current != null && !current.remote && current.id != sync.id) {
            context.stopService(Intent(context, FollowService::class.java))
        }
        mutableState.value = FollowState(sync.id, sync.followed, sync.minutes, sync.live, sync.later, sync.arrivalAt, remote = true)
        FollowNotifications.showMirror(context, mutableState.value!!)
    }

    internal fun onRemoteStop(context: Context, id: String) {
        val current = state.value ?: return
        if (!current.remote && current.id == id) stop(context)
    }
}
