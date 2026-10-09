package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
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
import java.net.URLEncoder

private const val MAX_SEEN_IDS = 500
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

/** Followed lines and their timetable or route changes. Port of stores/routeUpdates.ts. */
class RouteUpdates(context: Context) {
    private val prefs = context.getSharedPreferences("routeUpdates", Context.MODE_PRIVATE)

    private val _followed = MutableStateFlow(readFollowed())
    val followed: StateFlow<List<String>> = _followed
    private val _seen = MutableStateFlow(readSeen())
    val seen: StateFlow<Set<Long>> = _seen
    private val _changes = MutableStateFlow<List<RouteChange>>(emptyList())
    val changes: StateFlow<List<RouteChange>> = _changes
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow(false)
    private var lastFetchAt = 0L
    private val mutex = Mutex()

    private fun readFollowed(): List<String> = runCatching {
        (Api.json.parseToJsonElement(prefs.getString("followed", null) ?: "[]") as JsonArray)
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf(String::isNotEmpty) }
    }.getOrDefault(emptyList())

    private fun readSeen(): Set<Long> = runCatching {
        (Api.json.parseToJsonElement(prefs.getString("seen", null) ?: "[]") as JsonArray)
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.longOrNull }.toSet()
    }.getOrDefault(emptySet())

    private fun writeFollowed() = prefs.edit { putString("followed", Api.json.encodeToString(_followed.value)) }

    fun isFollowing(routeShortName: String) = routeShortName in _followed.value

    /** True when the line is now followed. */
    fun toggleFollow(routeShortName: String): Boolean {
        val following = routeShortName !in _followed.value
        _followed.value = if (following) _followed.value + routeShortName else _followed.value - routeShortName
        writeFollowed()
        return following
    }

    fun importFollowed(raw: JsonArray?) {
        _followed.value = raw.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf(String::isNotEmpty) }
            .distinct()
        writeFollowed()
    }

    fun unseenIds(changes: List<RouteChange> = _changes.value, seen: Set<Long> = _seen.value): Set<Long> =
        changes.filter { it.id !in seen }.map { it.id }.toSet()

    suspend fun fetchChanges() = mutex.withLock {
        val followed = _followed.value
        if (followed.isEmpty()) {
            _changes.value = emptyList()
            error.value = false
            return@withLock
        }
        loading.value = true
        error.value = false
        try {
            val routes = followed.sorted().joinToString(",") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            _changes.value = Api.getFresh<List<RouteChange>>("route-changes?routes=$routes")
            lastFetchAt = System.currentTimeMillis()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error.value = true
        } finally {
            loading.value = false
        }
    }

    suspend fun refreshIfStale() {
        if (System.currentTimeMillis() - lastFetchAt > REFRESH_AFTER_MS) fetchChanges()
    }

    fun markSeen(ids: Collection<Long>) {
        val next = _seen.value + ids
        if (next.size == _seen.value.size) return
        _seen.value = next.sortedDescending().take(MAX_SEEN_IDS).toSet()
        prefs.edit { putString("seen", Api.json.encodeToString(_seen.value.toList())) }
    }

    fun markAllSeen() = markSeen(_changes.value.map { it.id })

    suspend fun loadChange(id: Long): RouteChange? {
        _changes.value.find { it.id == id }?.let { return it }
        return runCatching { Api.getFresh<List<RouteChange>>("route-changes?id=$id").firstOrNull() }.getOrNull()
    }
}
