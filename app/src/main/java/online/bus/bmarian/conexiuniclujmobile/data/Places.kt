package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.Request
import java.util.Locale

data class Place(val id: String, val lat: String, val lon: String, val label: String, val name: String = label, val far: Boolean = false) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()

    val detail: String get() = label.removePrefix(name).removePrefix(",").trim()
}

private fun fallbackLabel(lat: String, lon: String) = String.format(Locale.ROOT, "%.4f, %.4f", lat.toDouble(), lon.toDouble())

// JS String(number): no trailing ".0".
private fun jsNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

object Places {
    private const val CACHED = 64

    private val cache = RecentCache<List<Place>>(CACHED)

    fun place(raw: JsonElement): Place? {
        val o = raw as? JsonObject ?: return null
        val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val latS = jsNumber(lat)
        val lonS = jsNumber(lon)
        val label = (o["label"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "$latS:$lonS"
        val shown = label.ifEmpty { fallbackLabel(latS, lonS) }
        val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val far = (o["far"] as? JsonPrimitive)?.booleanOrNull == true
        return Place(id, latS, lonS, shown, name.takeIf { label.isNotEmpty() && it.isNotEmpty() } ?: shown, far)
    }

    private suspend fun request(url: String): List<Place> {
        cache[url]?.let { return it }
        val places = withContext(Dispatchers.IO) {
            runCatching {
                Api.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val data = Api.json.parseToJsonElement(response.body?.string() ?: return@use null)
                    (data as? JsonArray)?.mapNotNull(::place)
                }
            }.getOrNull()
        } ?: return emptyList()
        cache[url] = places
        return places
    }

    suspend fun search(query: String, limit: Int): List<Place> =
        request(Api.url("places?q=${Api.encode(query)}&limit=$limit"))

    suspend fun reverse(lat: Double, lon: Double): Place? =
        request(Api.url("places?lat=$lat&lon=$lon")).firstOrNull()
}
