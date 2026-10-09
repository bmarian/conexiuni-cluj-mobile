package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.net.URLDecoder
import java.text.Collator
import java.util.Locale

// Port of frontend/src/utils/nominatim.ts.
data class Place(val id: String, val lat: String, val lon: String, val label: String) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()
}

object Nominatim {
    private const val BASE_URL = "https://nominatim.openstreetmap.org"

    private val GMAPS_SHORT_URL = Regex("""^https?://maps\.app\.goo\.gl/""")
    private val GMAPS_FULL_URL = Regex("""^https?://(www\.)?google\.com/maps""")
    private val GMAPS_PIN_COORD = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val GMAPS_AT_COORD = Regex("""/@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val GMAPS_QUERY_COORD = Regex("""[?&]q=(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val GMAPS_PLACE_NAME = Regex("""/maps/place/([^/@?#]+)""")

    fun isGoogleMapsUrl(query: String): Boolean {
        val s = query.trim()
        return GMAPS_SHORT_URL.containsMatchIn(s) || GMAPS_FULL_URL.containsMatchIn(s)
    }

    private fun fallbackLabel(lat: String, lon: String) =
        String.format(Locale.ROOT, "%.4f, %.4f", lat.toDouble(), lon.toDouble())

    private fun parseGoogleMapsUrlCoords(url: String): Place? {
        val m = GMAPS_PIN_COORD.find(url) ?: GMAPS_AT_COORD.find(url) ?: GMAPS_QUERY_COORD.find(url) ?: return null
        val lat = m.groupValues[1]
        val lon = m.groupValues[2]
        if (lat.isEmpty() || lon.isEmpty()) return null
        val rawName = GMAPS_PLACE_NAME.find(url)?.groupValues?.get(1)
        val label = rawName?.let { runCatching { URLDecoder.decode(it.replace("+", " "), "UTF-8") }.getOrNull() }
            ?: fallbackLabel(lat, lon)
        return Place("gmaps:$lat:$lon", lat, lon, label)
    }

    suspend fun resolveGoogleMapsLink(url: String): Place? {
        val s = url.trim()
        if (GMAPS_FULL_URL.containsMatchIn(s)) return parseGoogleMapsUrlCoords(s)
        return runCatching {
            val data = Api.json.parseToJsonElement(Api.getText("resolve-location?url=${Api.encode(s)}")).jsonObject
            val lat = (data["lat"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
            val lon = (data["lon"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
            val latS = jsNumber(lat)
            val lonS = jsNumber(lon)
            val label = (data["label"] as? JsonPrimitive)?.contentOrNull ?: fallbackLabel(latS, lonS)
            Place("gmaps:$latS:$lonS", latS, lonS, label)
        }.getOrNull()
    }

    // String(number) in JS: no trailing ".0".
    private fun jsNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

    private fun features(data: JsonElement): List<JsonObject> = when (val f = (data as? JsonObject)?.get("features")) {
        is JsonArray -> f.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(f)
        else -> emptyList()
    }

    private fun compact(value: JsonElement?): String = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private val baseCollator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

    private fun sameText(a: String, b: String) = baseCollator.compare(a, b) == 0

    private fun formatStreetLabel(geocoding: JsonObject?, fallback: String): String {
        val name = compact(geocoding?.get("name"))
        val street = compact(geocoding?.get("street"))
        val houseNumber = compact(geocoding?.get("housenumber"))
        val district = compact(geocoding?.get("district"))
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
        val geocoding = (feature["properties"] as? JsonObject)?.get("geocoding") as? JsonObject
        val fallback = compact(geocoding?.get("label")).ifEmpty { fallbackLabel(latS, lonS) }
        val label = formatStreetLabel(geocoding, fallback)
        val idKey = "${compact(geocoding?.get("osm_type"))}:${compact(geocoding?.get("osm_id"))}"
        val id = if (idKey != ":") idKey else "$latS:$lonS:$index"
        return Place(id, latS, lonS, label)
    }

    private suspend fun request(url: String): JsonElement? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        runCatching {
            Api.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                Api.json.parseToJsonElement(response.body?.string() ?: return@use null)
            }
        }.getOrNull()
    }

    suspend fun search(query: String, locale: String, limit: Int): List<Place> {
        val url = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "geocodejson")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("countrycodes", "ro")
            .addQueryParameter("viewbox", "22.75,47.50,24.27,46.38")
            .addQueryParameter("bounded", "1")
            .addQueryParameter("limit", "$limit")
            .addQueryParameter("accept-language", locale)
            .build().toString()
        val data = request(url) ?: return emptyList()
        return features(data).mapIndexedNotNull { i, f -> mapFeature(f, i) }
    }

    suspend fun reverse(lat: Double, lon: Double, locale: String): Place? {
        val url = "$BASE_URL/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", "$lat")
            .addQueryParameter("lon", "$lon")
            .addQueryParameter("format", "geocodejson")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("accept-language", locale)
            .build().toString()
        val data = request(url) ?: return null
        return features(data).firstOrNull()?.let { mapFeature(it, 0) }
    }
}
