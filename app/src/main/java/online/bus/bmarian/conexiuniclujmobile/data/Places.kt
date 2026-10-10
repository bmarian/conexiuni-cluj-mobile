package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.Collator
import java.util.Locale

data class Place(val id: String, val lat: String, val lon: String, val label: String) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()
}

private fun fallbackLabel(lat: String, lon: String) = String.format(Locale.ROOT, "%.4f, %.4f", lat.toDouble(), lon.toDouble())

// JS String(number): no trailing ".0".
private fun jsNumber(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

/**
 * Place search on Komoot's public Photon (OpenStreetMap data, open source), which is made for search as you
 * type. It's shared and free, so requests are spaced out and cached.
 */
object Photon {
    private const val BASE_URL = "https://photon.komoot.io"
    // Cluj county, and its centre for ranking, so the user's own position isn't sent.
    private const val BBOX = "22.75,46.38,24.27,47.50"
    private const val CENTER_LAT = "46.77"
    private const val CENTER_LON = "23.59"
    private const val MIN_GAP_MS = 1_000L
    private const val CACHED = 64

    private val gate = Mutex()
    private var lastRequestAt = 0L
    private val cache = object : LinkedHashMap<String, JsonElement>(CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonElement>?) = size > CACHED
    }

    private fun features(data: JsonElement): List<JsonObject> = when (val f = (data as? JsonObject)?.get("features")) {
        is JsonArray -> f.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(f)
        else -> emptyList()
    }

    private fun compact(value: JsonElement?): String = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private val baseCollator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

    private fun sameText(a: String, b: String) = baseCollator.compare(a, b) == 0

    private fun formatStreetLabel(properties: JsonObject?, fallback: String): String {
        val name = compact(properties?.get("name"))
        val street = compact(properties?.get("street"))
        val houseNumber = compact(properties?.get("housenumber"))
        val district = compact(properties?.get("district"))
        val base = when {
            street.isNotEmpty() && houseNumber.isNotEmpty() && name.isNotEmpty() && !sameText(name, street) ->
                "$name, $street, $houseNumber"
            street.isNotEmpty() && houseNumber.isNotEmpty() -> "$street, $houseNumber"
            street.isNotEmpty() -> street
            name.isNotEmpty() -> name
            else -> fallback
        }
        return if (district.isEmpty()) base else "$base, $district"
    }

    private fun mapFeature(feature: JsonObject, index: Int): Place? {
        val coords = (feature["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: return null
        if (coords.size < 2) return null
        val lon = (coords[0] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (coords[1] as? JsonPrimitive)?.doubleOrNull ?: return null
        val latS = jsNumber(lat)
        val lonS = jsNumber(lon)
        val properties = feature["properties"] as? JsonObject
        val label = formatStreetLabel(properties, fallbackLabel(latS, lonS))
        val idKey = "${compact(properties?.get("osm_type"))}:${compact(properties?.get("osm_id"))}"
        val id = if (idKey != ":") idKey else "$latS:$lonS:$index"
        return Place(id, latS, lonS, label)
    }

    private suspend fun request(url: String): JsonElement? {
        synchronized(cache) { cache[url] }?.let { return it }
        return gate.withLock {
            synchronized(cache) { cache[url] }?.let { return@withLock it }
            val wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            val data = withContext(Dispatchers.IO) {
                runCatching {
                    Api.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        Api.json.parseToJsonElement(response.body?.string() ?: return@use null)
                    }
                }.getOrNull()
            }
            data?.also { synchronized(cache) { cache[url] = it } }
        }
    }

    // Names are Romanian by default; Photon has no Romanian option.
    private fun language(locale: String) = if (locale.startsWith("en")) "en" else "default"

    suspend fun search(query: String, locale: String, limit: Int): List<Place> {
        val url = "$BASE_URL/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("bbox", BBOX)
            .addQueryParameter("lat", CENTER_LAT)
            .addQueryParameter("lon", CENTER_LON)
            .addQueryParameter("limit", "$limit")
            .addQueryParameter("lang", language(locale))
            .build().toString()
        val data = request(url) ?: return emptyList()
        return features(data).mapIndexedNotNull { i, f -> mapFeature(f, i) }
    }

    suspend fun reverse(lat: Double, lon: Double, locale: String): Place? {
        val url = "$BASE_URL/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", "$lat")
            .addQueryParameter("lon", "$lon")
            .addQueryParameter("lang", language(locale))
            .build().toString()
        val data = request(url) ?: return null
        return features(data).firstOrNull()?.let { mapFeature(it, 0) }
    }
}
