package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Request
import java.util.Locale

data class Place(val id: String, val lat: String, val lon: String, val label: String) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()
}

private fun fallbackLabel(lat: String, lon: String) = String.format(Locale.ROOT, "%.4f, %.4f", lat.toDouble(), lon.toDouble())

// JS String(number): no trailing ".0".
private fun jsNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

/** Place search on the backend's own index of the area's addresses, named places and streets. */
object Places {
    private const val CACHED = 64

    private val cache = object : LinkedHashMap<String, List<Place>>(CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) = size > CACHED
    }

    private fun place(raw: JsonElement): Place? {
        val o = raw as? JsonObject ?: return null
        val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val latS = jsNumber(lat)
        val lonS = jsNumber(lon)
        val label = (o["label"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "$latS:$lonS"
        return Place(id, latS, lonS, label.ifEmpty { fallbackLabel(latS, lonS) })
    }

    private suspend fun request(url: String): List<Place> {
        synchronized(cache) { cache[url] }?.let { return it }
        val places = withContext(Dispatchers.IO) {
            runCatching {
                Api.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val data = Api.json.parseToJsonElement(response.body?.string() ?: return@use null)
                    (data as? JsonArray)?.mapNotNull(::place)
                }
            }.getOrNull()
        } ?: return emptyList()
        synchronized(cache) { cache[url] = places }
        return places
    }

    suspend fun search(query: String, limit: Int): List<Place> =
        request(Api.url("places?q=${Api.encode(query)}&limit=$limit"))

    suspend fun reverse(lat: Double, lon: Double): Place? =
        request(Api.url("places?lat=$lat&lon=$lon")).firstOrNull()
}
