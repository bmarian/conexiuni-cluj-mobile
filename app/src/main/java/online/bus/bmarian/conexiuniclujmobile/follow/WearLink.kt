package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneySync

/** The stops and lines both devices keep, with when they last changed. The watch reads the same JSON. */
@Serializable
data class SharedFavorites(
    val stops: List<Int>,
    val routes: List<FavoriteRoute>,
    val updatedAt: Long,
)

/**
 * Talks to the watch app directly over the Wear OS data layer, like Maps hands navigation to
 * the watch. Both apps declare [CAPABILITY], so each only talks to a device that has the other.
 */
object WearLink {
    const val CAPABILITY = "conexiuni_follow"
    const val PATH_STATE = "/follow/state"
    const val PATH_STOP = "/follow/stop"
    const val PATH_JOURNEY_STATE = "/journey/state"
    const val PATH_JOURNEY_STOP = "/journey/stop"
    const val PATH_JOURNEY_PLAN = "/journey/plan"
    const val PATH_JOURNEY_PLAN_REQUEST = "/journey/plan/request"
    const val PATH_FAVORITES = "/sync/favorites"
    // Links only these apps open, so neither device's browser takes them.
    const val APP_SCHEME = "conexiunicluj"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableWatch = MutableStateFlow(false)
    /** A watch with the app is in reach: following, journeys and favourites show on it too. */
    val watchInReach: StateFlow<Boolean> = mutableWatch

    /** Watches for the watch app coming and going, and catches up on favourites changed there. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch { mutableWatch.value = peers(app).isNotEmpty() }
        runCatching {
            Wearable.getCapabilityClient(app).addListener({ info -> mutableWatch.value = info.nodes.isNotEmpty() }, CAPABILITY)
        }
        Services.favorites.onSharedChange = { publishFavorites(app) }
        scope.launch { catchUpFavorites(app) }
    }

    /** Checks again whether the watch app is in reach: "Open on watch" only shows when it is. */
    fun refreshReach(context: Context) {
        val app = context.applicationContext
        scope.launch { mutableWatch.value = peers(app).isNotEmpty() || relayed }
    }

    // A phone that never had a watch has no data layer at all; that's just no one to talk to.
    private suspend fun peers(context: Context): Set<Node> = runCatching {
        Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
    }.getOrDefault(emptySet())

    private suspend fun send(context: Context, path: String, data: ByteArray): Boolean {
        relay(path, data)
        val nodes = peers(context)
        mutableWatch.value = nodes.isNotEmpty() || relayed
        val client = Wearable.getMessageClient(context)
        var delivered = false
        for (node in nodes) {
            if (runCatching { client.sendMessage(node.id, path, data).await() }.isSuccess) delivered = true
        }
        return delivered
    }

    /** Sends where the followed bus is. */
    fun sendState(context: Context, sync: FollowSync) {
        val app = context.applicationContext
        scope.launch { send(app, PATH_STATE, Api.json.encodeToString(sync).encodeToByteArray()) }
    }

    fun sendStop(context: Context, id: String) {
        val app = context.applicationContext
        scope.launch { send(app, PATH_STOP, id.encodeToByteArray()) }
    }

    fun sendJourney(context: Context, sync: JourneySync) {
        val app = context.applicationContext
        scope.launch { send(app, PATH_JOURNEY_STATE, Api.json.encodeToString(sync).encodeToByteArray()) }
    }

    /** The whole trip, stops, paths and colours, for the watch's journey screen and its map. */
    fun sendJourneyPlan(context: Context, journey: Journey) {
        val app = context.applicationContext
        scope.launch { send(app, PATH_JOURNEY_PLAN, Api.json.encodeToString(journey).encodeToByteArray()) }
    }

    /** The watch's name and whether it has the app, for Settings. */
    sealed interface WatchStatus {
        data class Connected(val name: String) : WatchStatus
        data class NoApp(val name: String) : WatchStatus
        data object None : WatchStatus
    }

    private val mutableStatus = MutableStateFlow<WatchStatus>(WatchStatus.None)
    val status: StateFlow<WatchStatus> = mutableStatus

    suspend fun refreshStatus(context: Context) {
        val app = context.applicationContext
        val withApp = peers(app).firstOrNull()
        val connected = runCatching { Wearable.getNodeClient(app).connectedNodes.await() }.getOrDefault(emptyList()).firstOrNull()
        mutableStatus.value = when {
            withApp != null -> WatchStatus.Connected(withApp.displayName)
            connected != null -> WatchStatus.NoApp(connected.displayName)
            else -> WatchStatus.None
        }
        mutableWatch.value = withApp != null || relayed
    }

    /**
     * Brings the watch app up on what was just started here, the way Maps opens its navigation
     * on the watch. [path] is "/following" or "/journey".
     */
    fun showOnWatch(context: Context, path: String) {
        val app = context.applicationContext
        scope.launch { openOnWatch(app, path) }
    }

    /**
     * Opens a stop or a line on the watch, like Maps' "Send to watch". [path] is the website's,
     * "/stop/391" or "/route/25/0". Returns whether a watch was asked.
     */
    suspend fun openOnWatch(context: Context, path: String): Boolean {
        val nodes = peers(context)
        if (nodes.isEmpty()) return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("$APP_SCHEME://bus.bmarian.online$path"))
        val helper = RemoteActivityHelper(context)
        var asked = false
        for (node in nodes) {
            val opened = runCatching { withContext(Dispatchers.IO) { helper.startRemoteActivity(intent, node.id).get() } }
            if (opened.isSuccess) asked = true
        }
        return asked
    }

    // Debug builds only: every message also goes to the log, for tools/wear-relay.py to carry to an
    // emulator that can't be paired. A message it brings back counts as a watch in reach.
    private const val RELAY_TAG = "WearRelay"
    private var relayed = false

    private fun relay(path: String, data: ByteArray) {
        if (!BuildConfig.DEBUG) return
        // A log line holds about 4 KB: a journey's plan goes in numbered parts, path#1/3 and so on.
        val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
        val parts = encoded.chunked(3_000)
        if (parts.size <= 1) Log.d(RELAY_TAG, "$path $encoded")
        else parts.forEachIndexed { i, part -> Log.d(RELAY_TAG, "$path#${i + 1}/${parts.size} $part") }
    }

    internal fun relayedFromWatch() {
        relayed = true
        mutableWatch.value = true
    }

    /** A message from the watch, whichever way it came. */
    internal fun onMessage(context: Context, path: String, data: ByteArray) {
        when (path) {
            PATH_STATE -> runCatching { Api.json.decodeFromString<FollowSync>(data.decodeToString()) }
                .getOrNull()
                ?.let { FollowManager.onRemoteState(context, it) }
            PATH_STOP -> FollowManager.onRemoteStop(context, data.decodeToString())
            PATH_JOURNEY_STOP -> JourneyManager.onRemoteStop(context, data.decodeToString())
            PATH_JOURNEY_PLAN_REQUEST -> JourneyManager.onPlanRequest(context, data.decodeToString())
            PATH_FAVORITES -> onFavorites(context, data.decodeToString())
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("wear_sync", Context.MODE_PRIVATE)

    // Last write wins: each device keeps the time of the newest favourites it has, its own or the other's.
    private fun publishFavorites(context: Context) {
        val favorites = Services.favorites
        val now = System.currentTimeMillis()
        prefs(context).edit { putLong("favoritesAt", now) }
        val shared = SharedFavorites(favorites.stops.value, favorites.routes.value, now)
        relay(PATH_FAVORITES, Api.json.encodeToString(shared).encodeToByteArray())
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(PATH_FAVORITES).apply {
                    dataMap.putString("json", Api.json.encodeToString(shared))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
            }
        }
    }

    internal fun onFavorites(context: Context, json: String) {
        val shared = runCatching { Api.json.decodeFromString<SharedFavorites>(json) }.getOrNull() ?: return
        val prefs = prefs(context)
        if (shared.updatedAt <= prefs.getLong("favoritesAt", 0L)) return
        prefs.edit { putLong("favoritesAt", shared.updatedAt) }
        Services.favorites.replaceShared(shared.stops, shared.routes)
    }

    // Changed on the watch while this app wasn't running: the item is still there to read.
    private suspend fun catchUpFavorites(context: Context) {
        runCatching {
            val local = Wearable.getNodeClient(context).localNode.await().id
            val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*$PATH_FAVORITES")).await()
            val newest = items.use { buffer ->
                buffer.filter { it.uri.host != local }
                    .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString("json") }
            }.mapNotNull { runCatching { Api.json.decodeFromString<SharedFavorites>(it) }.getOrNull() }
                .maxByOrNull { it.updatedAt }
            if (newest != null) onFavorites(context, Api.json.encodeToString(newest))
        }
    }
}

/** Wakes the app when the watch starts, updates or stops something, or changes favourites. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WearLink.onMessage(applicationContext, event.path, event.data)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val local = runCatching { com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(this).localNode).id }.getOrNull()
        events.forEach { event ->
            val item = event.dataItem
            if (event.type != DataEvent.TYPE_CHANGED || item.uri.path != WearLink.PATH_FAVORITES || item.uri.host == local) return@forEach
            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { WearLink.onFavorites(applicationContext, it) }
        }
    }
}
