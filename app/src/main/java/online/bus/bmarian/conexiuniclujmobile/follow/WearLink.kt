package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
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
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.data.Links
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneySync

@Serializable
data class WalkRequest(val id: String, val city: String, val lat: Double, val lon: Double)

@Serializable
data class WalkAnswer(val id: String, val walk: Int? = null, val at: Long = 0)

/** Shared with the watch app, one data item per city. */
@Serializable
data class SharedFavorites(
    val stops: List<Int>,
    val routes: List<FavoriteRoute>,
    val updatedAt: Long,
)

object WearLink {
    const val CAPABILITY = "conexiuni_follow"
    const val PATH_STATE = "/follow/state"
    const val PATH_STOP = "/follow/stop"
    const val PATH_JOURNEY_STATE = "/journey/state"
    const val PATH_JOURNEY_STOP = "/journey/stop"
    const val PATH_JOURNEY_PLAN = "/journey/plan"
    const val PATH_JOURNEY_PLAN_REQUEST = "/journey/plan/request"
    const val PATH_WALK_REQUEST = "/follow/walk/request"
    const val PATH_WALK = "/follow/walk"
    // Cluj's; other cities' under it, as /sync/favorites/{city}. Older builds know only Cluj's.
    const val PATH_FAVORITES = "/sync/favorites"
    const val PATH_LANGUAGE = "/sync/language"
    const val PATH_COLORS = "/sync/colors"
    const val PATH_CITY = "/sync/city"

    fun favoritesPath(city: String) = if (city == Cities.DEFAULT) PATH_FAVORITES else "$PATH_FAVORITES/$city"

    /** The city a favorites item belongs to; null for another path. */
    fun favoritesCity(path: String?): String? = when {
        path == PATH_FAVORITES -> Cities.DEFAULT
        path != null && path.startsWith("$PATH_FAVORITES/") -> path.removePrefix("$PATH_FAVORITES/").takeIf(Cities::isSlug)
        else -> null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableWatch = MutableStateFlow(false)
    val watchInReach: StateFlow<Boolean> = mutableWatch

    private val mutableWithoutApp = MutableStateFlow(false)
    /** A connected watch without the app, until it's been offered once. */
    val watchWithoutApp: StateFlow<Boolean> = mutableWithoutApp

    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch { mutableWatch.value = peers(app).isNotEmpty() }
        runCatching {
            Wearable.getCapabilityClient(app).addListener({ info ->
                mutableWatch.value = info.nodes.isNotEmpty()
                checkWatchApp(app)
            }, CAPABILITY)
        }
        Services.favorites.onSharedChange = { city -> publishFavorites(app, city) }
        scope.launch { catchUpFavorites(app) }
        scope.launch {
            Services.settings.locale.collect { tag -> if (prefs(app).getString("language", null) != tag) publishLanguage(app, tag) }
        }
        scope.launch {
            Services.settings.colorfulLines.collect { on ->
                val prefs = prefs(app)
                if (!prefs.contains("colorful") || prefs.getBoolean("colorful", false) != on) publishColors(app, on)
            }
        }
        scope.launch {
            Cities.current.collect { city -> if (prefs(app).getString("city", null) != city) publishCity(app, city) }
        }
    }

    fun refreshReach(context: Context) {
        val app = context.applicationContext
        scope.launch { mutableWatch.value = peers(app).isNotEmpty() || relayed }
        checkWatchApp(app)
    }

    private suspend fun peers(context: Context): Set<Node> = runCatching {
        Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
    }.getOrDefault(emptySet())

    private suspend fun watchesWithoutApp(context: Context): List<Node> = runCatching {
        val withApp = Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_ALL).await().nodes.map { it.id }.toSet()
        Wearable.getNodeClient(context).connectedNodes.await().filter { it.id !in withApp }
    }.getOrDefault(emptyList())

    private fun checkWatchApp(context: Context) {
        if (prefs(context).getBoolean("watchAppOffered", false)) return
        scope.launch { mutableWithoutApp.value = watchesWithoutApp(context).isNotEmpty() }
    }

    /** Opens the app's Play page on each watch that doesn't have it. */
    fun installOnWatch(context: Context) {
        val app = context.applicationContext
        skipWatchApp(app)
        scope.launch {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData("market://details?id=${app.packageName}".toUri())
            val helper = RemoteActivityHelper(app)
            for (node in watchesWithoutApp(app)) {
                runCatching { withContext(Dispatchers.IO) { helper.startRemoteActivity(intent, node.id).get() } }
            }
        }
    }

    fun skipWatchApp(context: Context) {
        prefs(context).edit { putBoolean("watchAppOffered", true) }
        mutableWithoutApp.value = false
    }

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

    fun sendJourneyPlan(context: Context, journey: Journey) {
        val app = context.applicationContext
        scope.launch { send(app, PATH_JOURNEY_PLAN, Api.json.encodeToString(journey).encodeToByteArray()) }
    }

    fun showOnWatch(context: Context, path: String) {
        val app = context.applicationContext
        scope.launch { openOnWatch(app, path) }
    }

    suspend fun openOnWatch(context: Context, path: String): Boolean {
        val nodes = peers(context)
        if (nodes.isEmpty()) return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData("${Links.SCHEME}://${Links.HOST}$path".toUri())
        val helper = RemoteActivityHelper(context)
        var asked = false
        for (node in nodes) {
            val opened = runCatching { withContext(Dispatchers.IO) { helper.startRemoteActivity(intent, node.id).get() } }
            if (opened.isSuccess) asked = true
        }
        return asked
    }

    // Debug only: logged for tools/wear-relay.py.
    private const val RELAY_TAG = "WearRelay"
    private var relayed = false

    private fun relay(path: String, data: ByteArray) {
        if (!BuildConfig.DEBUG) return
        // Logcat lines cap at ~4 KB: long messages go as path#i/n.
        val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
        val parts = encoded.chunked(3_000)
        if (parts.size <= 1) Log.d(RELAY_TAG, "$path $encoded")
        else parts.forEachIndexed { i, part -> Log.d(RELAY_TAG, "$path#${i + 1}/${parts.size} $part") }
    }

    internal fun relayedFromWatch() {
        relayed = true
        mutableWatch.value = true
    }

    internal fun onMessage(context: Context, path: String, data: ByteArray) {
        when (path) {
            PATH_STATE -> runCatching { Api.json.decodeFromString<FollowSync>(data.decodeToString()) }
                .getOrNull()
                ?.let { FollowManager.onRemoteState(context, it) }
            PATH_STOP -> FollowManager.onRemoteStop(context, data.decodeToString())
            PATH_JOURNEY_STOP -> JourneyManager.onRemoteStop(context, data.decodeToString())
            PATH_JOURNEY_PLAN_REQUEST -> JourneyManager.onPlanRequest(context, data.decodeToString())
            PATH_WALK_REQUEST -> runCatching { Api.json.decodeFromString<WalkRequest>(data.decodeToString()) }
                .getOrNull()
                ?.let { request -> scope.launch { answerWalk(context, request) } }
            PATH_FAVORITES -> onFavorites(context, Cities.DEFAULT, data.decodeToString())
        }
    }

    /** A reminder set on the watch: the walk from where the phone is now. */
    private suspend fun answerWalk(context: Context, request: WalkRequest) {
        val (walk, at) = Reminders.walkTo(context, request.city, request.lat, request.lon) ?: (null to 0L)
        send(context, PATH_WALK, Api.json.encodeToString(WalkAnswer(request.id, walk, at)).encodeToByteArray())
    }

    private fun prefs(context: Context) = context.getSharedPreferences("wear_sync", Context.MODE_PRIVATE)

    // Saved before cities under the bare key: Cluj's.
    private fun favoritesAtKey(city: String) = if (city == Cities.DEFAULT) "favoritesAt" else "favoritesAt:$city"

    // Newest write wins, city by city.
    private fun publishFavorites(context: Context, city: String) {
        val saved = Services.favorites.saved(city)
        val now = System.currentTimeMillis()
        prefs(context).edit { putLong(favoritesAtKey(city), now) }
        val shared = SharedFavorites(saved.stops, saved.routes, now)
        val path = favoritesPath(city)
        relay(path, Api.json.encodeToString(shared).encodeToByteArray())
        scope.launch {
            runCatching {
                val request = PutDataMapRequest.create(path).apply {
                    dataMap.putString("json", Api.json.encodeToString(shared))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
            }
        }
    }

    // The watch shows the phone's city.
    private suspend fun publishCity(context: Context, city: String) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_CITY).apply {
                dataMap.putString("city", city)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            prefs(context).edit { putString("city", city) }
        }
    }

    private suspend fun publishLanguage(context: Context, tag: String) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_LANGUAGE).apply {
                dataMap.putString("locale", tag)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            prefs(context).edit { putString("language", tag) }
        }
    }

    private suspend fun publishColors(context: Context, colorful: Boolean) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_COLORS).apply {
                dataMap.putBoolean("colorful", colorful)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            prefs(context).edit { putBoolean("colorful", colorful) }
        }
    }

    internal fun onFavorites(context: Context, city: String, json: String) {
        val shared = runCatching { Api.json.decodeFromString<SharedFavorites>(json) }.getOrNull() ?: return
        val prefs = prefs(context)
        if (shared.updatedAt <= prefs.getLong(favoritesAtKey(city), 0L)) return
        prefs.edit { putLong(favoritesAtKey(city), shared.updatedAt) }
        Services.favorites.replaceShared(city, shared.stops, shared.routes)
    }

    private suspend fun catchUpFavorites(context: Context) {
        runCatching {
            val local = Wearable.getNodeClient(context).localNode.await().id
            val items = Wearable.getDataClient(context).getDataItems("wear://*$PATH_FAVORITES".toUri(), DataClient.FILTER_PREFIX).await()
            val newest = items.use { buffer ->
                buffer.filter { it.uri.host != local }.mapNotNull { item ->
                    val city = favoritesCity(item.uri.path) ?: return@mapNotNull null
                    val json = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: return@mapNotNull null
                    runCatching { city to Api.json.decodeFromString<SharedFavorites>(json) }.getOrNull()
                }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.maxBy { it.updatedAt } }
            for ((city, shared) in newest) onFavorites(context, city, Api.json.encodeToString(shared))
        }
    }
}

class WearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WearLink.onMessage(applicationContext, event.path, event.data)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val local = runCatching { com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(this).localNode).id }.getOrNull()
        events.forEach { event ->
            val item = event.dataItem
            val city = WearLink.favoritesCity(item.uri.path)
            if (event.type != DataEvent.TYPE_CHANGED || city == null || item.uri.host == local) return@forEach
            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { WearLink.onFavorites(applicationContext, city, it) }
        }
    }
}
