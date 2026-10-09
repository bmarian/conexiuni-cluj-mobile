package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class FavoriteRoute(val routeId: Int, val direction: String)

/** A destination for the planner, optionally from a fixed origin. Same shape as FavoritePlan on the web. */
@Serializable
data class FavoritePlan(
    val name: String,
    val label: String? = null,
    val lat: Double,
    val lon: Double,
    val originName: String? = null,
    val originLat: Double? = null,
    val originLon: Double? = null,
) {
    fun samePlace(other: FavoritePlan) = sameAs(other.lat, other.lon, other.originLat, other.originLon)

    fun sameAs(lat: Double, lon: Double, originLat: Double?, originLon: Double?) =
        this.lat == lat && this.lon == lon && this.originLat == originLat && this.originLon == originLon
}

private const val MAX_RECENT_PLANS = 10

// Same shapes as favorites:routes, favorites:stops, favorites:plans and recents:plans on the
// web, so an export reads the same.
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _routes = MutableStateFlow(parseFavoriteRoutes(read("routes")))
    val routes: StateFlow<List<FavoriteRoute>> = _routes
    private val _stops = MutableStateFlow(parseStopIds(read("stops")))
    val stops: StateFlow<List<Int>> = _stops
    private val _plans = MutableStateFlow(parsePlans(read("plans")))
    val plans: StateFlow<List<FavoritePlan>> = _plans
    private val _recentPlans = MutableStateFlow(parsePlans(read("recentPlans")))
    val recentPlans: StateFlow<List<FavoritePlan>> = _recentPlans

    // Told when the user changes the stops or lines, the part the watch keeps too.
    var onSharedChange: (() -> Unit)? = null

    private fun read(key: String): JsonElement? =
        prefs.getString(key, null)?.let { runCatching { Api.json.parseToJsonElement(it) }.getOrNull() }

    private fun persist() {
        prefs.edit {
            putString("routes", Api.json.encodeToString(_routes.value))
            putString("stops", Api.json.encodeToString(_stops.value))
            putString("plans", Api.json.encodeToString(_plans.value))
            putString("recentPlans", Api.json.encodeToString(_recentPlans.value))
        }
    }

    fun isStopFavorite(stopId: Int) = stopId in _stops.value

    fun isRouteFavorite(routeId: Int, direction: String? = null) =
        _routes.value.any { it.routeId == routeId && (direction == null || it.direction == direction) }

    fun isPlanFavorite(lat: Double, lon: Double, originLat: Double?, originLon: Double?) =
        _plans.value.any { it.sameAs(lat, lon, originLat, originLon) }

    fun toggleStop(stopId: Int) {
        _stops.value = if (stopId in _stops.value) _stops.value - stopId else _stops.value + stopId
        persist()
        onSharedChange?.invoke()
    }

    fun toggleRoute(routeId: Int, direction: String) {
        val fav = FavoriteRoute(routeId, direction)
        _routes.value = if (fav in _routes.value) _routes.value - fav else _routes.value + fav
        persist()
        onSharedChange?.invoke()
    }

    fun togglePlan(plan: FavoritePlan) {
        val existing = _plans.value.indexOfFirst { it.samePlace(plan) }
        _plans.value = if (existing == -1) _plans.value + plan else _plans.value.filterIndexed { i, _ -> i != existing }
        persist()
    }

    fun renamePlan(lat: Double, lon: Double, originLat: Double?, originLon: Double?, label: String?) {
        _plans.value = _plans.value.map {
            if (it.sameAs(lat, lon, originLat, originLon)) it.copy(label = label?.takeIf(String::isNotEmpty)) else it
        }
        persist()
    }

    fun reorderRoutes(list: List<FavoriteRoute>) {
        _routes.value = list
        persist()
        onSharedChange?.invoke()
    }

    fun reorderStops(list: List<Int>) {
        _stops.value = list
        persist()
        onSharedChange?.invoke()
    }

    /** The watch's stops and lines, after a change there. Not sent back. */
    fun replaceShared(stops: List<Int>, routes: List<FavoriteRoute>) {
        _stops.value = stops
        _routes.value = routes
        persist()
    }

    fun reorderPlans(list: List<FavoritePlan>) {
        _plans.value = list
        persist()
    }

    fun addRecentPlan(plan: FavoritePlan) {
        _recentPlans.value = (listOf(plan) + _recentPlans.value.filterNot { it.samePlace(plan) }).take(MAX_RECENT_PLANS)
        persist()
    }

    fun removeRecentPlan(plan: FavoritePlan) {
        _recentPlans.value = _recentPlans.value.filterNot { it.samePlace(plan) }
        persist()
    }

    /** Replaces every list with what another device sent, like importAll on the web. */
    fun importAll(favorites: JsonObject) {
        _routes.value = parseFavoriteRoutes(favorites["routes"])
        _stops.value = parseStopIds(favorites["stops"])
        _plans.value = parsePlans(favorites["plans"])
        _recentPlans.value = parsePlans(favorites["recentPlans"])
        persist()
        onSharedChange?.invoke()
    }

    companion object {
        fun parseFavoriteRoutes(raw: JsonElement?): List<FavoriteRoute> {
            val parsed = mutableListOf<FavoriteRoute>()
            for (item in raw as? JsonArray ?: return emptyList()) {
                val fav = when (item) {
                    // Favorites saved before directions existed are bare route ids.
                    is JsonPrimitive -> item.intOrNull?.takeIf { !item.isString }?.let { FavoriteRoute(it, "0") }
                    is JsonObject -> {
                        val id = (item["routeId"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                        val direction = (item["direction"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                        if (id != null && (direction == "0" || direction == "1")) FavoriteRoute(id, direction) else null
                    }
                    else -> null
                }
                if (fav != null && fav !in parsed) parsed += fav
            }
            return parsed
        }

        fun parseStopIds(raw: JsonElement?): List<Int> {
            val list = raw as? JsonArray ?: return emptyList()
            return list.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull }
        }

        private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.number(key: String) =
            (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

        fun parsePlans(raw: JsonElement?): List<FavoritePlan> {
            val list = raw as? JsonArray ?: return emptyList()
            return list.mapNotNull { item ->
                val p = item as? JsonObject ?: return@mapNotNull null
                val name = p.string("name") ?: return@mapNotNull null
                val lat = p.number("lat") ?: return@mapNotNull null
                val lon = p.number("lon") ?: return@mapNotNull null
                FavoritePlan(
                    name = name,
                    label = p.string("label"),
                    lat = lat,
                    lon = lon,
                    originName = p.string("originName"),
                    originLat = p.number("originLat"),
                    originLon = p.number("originLon"),
                )
            }
        }
    }
}
