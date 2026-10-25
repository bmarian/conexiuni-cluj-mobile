package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords
import java.net.URLEncoder

private const val REFRESH_AFTER_MS = 30 * 60_000L

@Serializable
data class TimeShift(val from: String = "", val to: String = "")

@Serializable
data class RouteChangeItem(
    val kind: String,
    val direction: String = "0",
    val toward: String? = null,
    val days: List<String>? = null,
    val since: String? = null,
    val all: Boolean? = null,
    val times: List<String>? = null,
    val shifts: List<TimeShift>? = null,
    val stops: List<String>? = null,
)

@Serializable
data class RouteChange(
    val id: Long,
    @SerialName("route_short_name") val routeShortName: String = "",
    @SerialName("route_id") val routeId: Int = 0,
    @SerialName("route_color") val routeColor: String = "",
    val source: String = "timetable",
    @SerialName("detected_at") val detectedAt: Long = 0,
    val changes: List<RouteChangeItem> = emptyList(),
)

@Serializable
data class NewsItem(val title: String = "", val url: String = "")

/** A line change or an operator's news, newest first from /updates; [at] in epoch ms. */
@Serializable
data class Update(val kind: String, val at: Long = 0, val news: NewsItem? = null, val change: RouteChange? = null)

/** Port of stores/routeUpdates.ts: followed lines and what was seen are kept per city. */
class RouteUpdates(private val context: Context) {
    private var city = Cities.current.value
    private var prefs = cityPrefs(context, "routeUpdates", city)

    private val _followed = MutableStateFlow(readFollowed(prefs))
    val followed: StateFlow<List<String>> = _followed
    private val _seen = MutableStateFlow(readSeen(prefs))
    val seen: StateFlow<Set<Long>> = _seen
    private val _updates = MutableStateFlow<List<Update>>(emptyList())
    val updates: StateFlow<List<Update>> = _updates
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow(false)
    private var lastFetchAt = 0L
    private val mutex = Mutex()

    val changes: List<RouteChange> get() = _updates.value.mapNotNull { it.change }

    private fun readFollowed(prefs: SharedPreferences): List<String> = runCatching {
        (Api.json.parseToJsonElement(prefs.getString("followed", null) ?: "[]") as JsonArray)
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf(String::isNotEmpty) }
    }.getOrDefault(emptyList())

    private fun readSeen(prefs: SharedPreferences): Set<Long> = runCatching {
        (Api.json.parseToJsonElement(prefs.getString("seen", null) ?: "[]") as JsonArray)
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.longOrNull }.toSet()
    }.getOrDefault(emptySet())

    fun use(slug: String) {
        if (slug == city) return
        city = slug
        prefs = cityPrefs(context, "routeUpdates", slug)
        _followed.value = readFollowed(prefs)
        _seen.value = readSeen(prefs)
        _updates.value = emptyList()
        error.value = false
        lastFetchAt = 0
    }

    /** The news button: the city's news, or changes to lines followed here. */
    fun hasFeed(followed: List<String> = _followed.value) = followed.isNotEmpty() || Cities.info(city)?.news == true

    fun followedIn(city: String): List<String> = if (city == this.city) _followed.value else readFollowed(cityPrefs(context, "routeUpdates", city))

    private fun writeFollowed(prefs: SharedPreferences, list: List<String>) = prefs.edit { putString("followed", Api.json.encodeToString(list)) }

    private fun writeSeen(prefs: SharedPreferences, ids: Collection<Long>) = prefs.edit { putString("seen", Api.json.encodeToString(ids.toList())) }

    fun isFollowing(routeShortName: String) = routeShortName in _followed.value

    /** Null when the cap is reached. */
    fun toggleFollow(routeShortName: String): Boolean? {
        val following = routeShortName !in _followed.value
        if (following && _followed.value.size >= SyncRecords.CITY_CAP) return null
        _followed.value = if (following) _followed.value + routeShortName else _followed.value - routeShortName
        writeFollowed(prefs, _followed.value)
        return following
    }

    fun replaceFollowed(list: List<String>, city: String) {
        if (city != this.city) return writeFollowed(cityPrefs(context, "routeUpdates", city), list)
        _followed.value = list
        writeFollowed(prefs, list)
    }

    fun unseenIds(updates: List<Update> = _updates.value, seen: Set<Long> = _seen.value): Set<Long> =
        updates.mapNotNull { it.change }.filter { it.id !in seen }.map { it.id }.toSet()

    suspend fun fetchUpdates() = mutex.withLock {
        val city = this.city
        val followed = _followed.value
        if (!hasFeed(followed)) {
            _updates.value = emptyList()
            error.value = false
            return@withLock
        }
        loading.value = true
        error.value = false
        try {
            val routes = followed.sorted().joinToString(",") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val fetched = Api.getFresh<List<Update>>(if (routes.isEmpty()) "updates" else "updates?routes=$routes", city)
            if (city != this.city) return@withLock
            _updates.value = fetched.filter { it.news != null || it.change != null }
            lastFetchAt = System.currentTimeMillis()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (city == this.city) error.value = true
        } finally {
            loading.value = false
        }
    }

    suspend fun refreshIfStale() {
        if (System.currentTimeMillis() - lastFetchAt > REFRESH_AFTER_MS) fetchUpdates()
    }

    fun markSeen(ids: Collection<Long>) {
        val next = _seen.value + ids
        if (next.size == _seen.value.size) return
        _seen.value = next.sortedDescending().take(SyncRecords.NEWS_CAP).toSet()
        writeSeen(prefs, _seen.value)
    }

    fun seenIn(city: String): List<Long> = (if (city == this.city) _seen.value else readSeen(cityPrefs(context, "routeUpdates", city))).toList()

    fun replaceSeen(ids: List<Long>, city: String) {
        if (city != this.city) return writeSeen(cityPrefs(context, "routeUpdates", city), ids)
        _seen.value = ids.toSet()
        writeSeen(prefs, ids)
    }

    fun markAllSeen() = markSeen(changes.map { it.id })

    suspend fun loadChange(id: Long): RouteChange? {
        changes.find { it.id == id }?.let { return it }
        return runCatching { Api.getFresh<List<RouteChange>>("route-changes?id=$id").firstOrNull() }.getOrNull()
    }
}
