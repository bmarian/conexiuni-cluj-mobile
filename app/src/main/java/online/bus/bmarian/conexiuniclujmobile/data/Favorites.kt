package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import android.content.SharedPreferences
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
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords

@Serializable
data class FavoriteRoute(val routeId: Int, val direction: String)

/** Same shape as FavoritePlan on the web. */
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

    /** Saved places keep six decimals, as sync carries them, so a place compares equal on every device. */
    fun sameAs(lat: Double, lon: Double, originLat: Double?, originLon: Double?) =
        round6(this.lat) == round6(lat) && round6(this.lon) == round6(lon) &&
            this.originLat?.let(::round6) == originLat?.let(::round6) && this.originLon?.let(::round6) == originLon?.let(::round6)

    fun rounded() = copy(lat = round6(lat), lon = round6(lon), originLat = originLat?.let(::round6), originLon = originLon?.let(::round6))
}

private fun round6(x: Double) = SyncRecords.roundCoordinate(x)

private const val MAX_RECENT_PLANS = 10

/** Saved favorites of one city. */
class SavedFavorites(
    val routes: List<FavoriteRoute> = emptyList(),
    val stops: List<Int> = emptyList(),
    val plans: List<FavoritePlan> = emptyList(),
    val recentPlans: List<FavoritePlan> = emptyList(),
) {
    val isEmpty get() = routes.isEmpty() && stops.isEmpty() && plans.isEmpty() && recentPlans.isEmpty()
}

// Same keys as the website's localStorage, for exports; one file per city.
class Favorites(private val context: Context) {
    var city = Cities.current.value
        private set
    private var prefs = cityPrefs(context, "favorites", city)

    private val initial = read(prefs)
    private val _routes = MutableStateFlow(initial.routes)
    val routes: StateFlow<List<FavoriteRoute>> = _routes
    private val _stops = MutableStateFlow(initial.stops)
    val stops: StateFlow<List<Int>> = _stops
    private val _plans = MutableStateFlow(initial.plans)
    val plans: StateFlow<List<FavoritePlan>> = _plans
    private val _recentPlans = MutableStateFlow(initial.recentPlans)
    val recentPlans: StateFlow<List<FavoritePlan>> = _recentPlans

    /** The city whose stops or lines changed, for the watch. */
    var onSharedChange: ((String) -> Unit)? = null

    private fun read(prefs: SharedPreferences): SavedFavorites {
        fun get(key: String): JsonElement? =
            prefs.getString(key, null)?.let { runCatching { Api.json.parseToJsonElement(it) }.getOrNull() }
        return SavedFavorites(parseFavoriteRoutes(get("routes")), parseStopIds(get("stops")), parsePlans(get("plans")), parsePlans(get("recentPlans")))
    }

    private fun write(prefs: SharedPreferences, saved: SavedFavorites) {
        prefs.edit {
            putString("routes", Api.json.encodeToString(saved.routes))
            putString("stops", Api.json.encodeToString(saved.stops))
            putString("plans", Api.json.encodeToString(saved.plans))
            putString("recentPlans", Api.json.encodeToString(saved.recentPlans))
        }
    }

    private fun show(saved: SavedFavorites) {
        _routes.value = saved.routes
        _stops.value = saved.stops
        _plans.value = saved.plans
        _recentPlans.value = saved.recentPlans
    }

    fun use(slug: String) {
        if (slug == city) return
        city = slug
        prefs = cityPrefs(context, "favorites", slug)
        show(read(prefs))
    }

    fun saved(city: String): SavedFavorites = if (city == this.city) current() else read(cityPrefs(context, "favorites", city))

    private fun current() = SavedFavorites(_routes.value, _stops.value, _plans.value, _recentPlans.value)

    private fun persist() = write(prefs, current())

    private fun atCap(list: List<*>): Boolean {
        if (list.size < SyncRecords.CITY_CAP) return false
        Services.settings.showToast(context.getString(R.string.w_limit_reached, SyncRecords.CITY_CAP.toString()))
        return true
    }

    fun isStopFavorite(stopId: Int) = stopId in _stops.value

    fun isRouteFavorite(routeId: Int, direction: String? = null) =
        _routes.value.any { it.routeId == routeId && (direction == null || it.direction == direction) }

    fun isPlanFavorite(lat: Double, lon: Double, originLat: Double?, originLon: Double?) =
        _plans.value.any { it.sameAs(lat, lon, originLat, originLon) }

    fun toggleStop(stopId: Int) {
        if (stopId !in _stops.value && atCap(_stops.value)) return
        _stops.value = if (stopId in _stops.value) _stops.value - stopId else _stops.value + stopId
        persist()
        onSharedChange?.invoke(city)
    }

    fun toggleRoute(routeId: Int, direction: String) {
        val fav = FavoriteRoute(routeId, direction)
        if (fav !in _routes.value && atCap(_routes.value)) return
        _routes.value = if (fav in _routes.value) _routes.value - fav else _routes.value + fav
        persist()
        onSharedChange?.invoke(city)
    }

    fun togglePlan(plan: FavoritePlan) {
        val existing = _plans.value.indexOfFirst { it.samePlace(plan) }
        if (existing == -1 && atCap(_plans.value)) return
        _plans.value = if (existing == -1) _plans.value + plan.rounded() else _plans.value.filterIndexed { i, _ -> i != existing }
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
        onSharedChange?.invoke(city)
    }

    fun reorderStops(list: List<Int>) {
        _stops.value = list
        persist()
        onSharedChange?.invoke(city)
    }

    fun replaceShared(city: String, stops: List<Int>, routes: List<FavoriteRoute>) {
        if (city != this.city) {
            val prefs = cityPrefs(context, "favorites", city)
            val saved = read(prefs)
            write(prefs, SavedFavorites(routes, stops, saved.plans, saved.recentPlans))
            return
        }
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

    /** What sync agreed on; recent trips stay this device's own. */
    fun replaceSynced(city: String, routes: List<FavoriteRoute>, stops: List<Int>, plans: List<FavoritePlan>) {
        val saved = SavedFavorites(routes, stops, plans, saved(city).recentPlans)
        if (city != this.city) write(cityPrefs(context, "favorites", city), saved)
        else {
            show(saved)
            persist()
        }
        onSharedChange?.invoke(city)
    }

    companion object {
        fun parseFavoriteRoutes(raw: JsonElement?): List<FavoriteRoute> {
            val parsed = mutableListOf<FavoriteRoute>()
            for (item in raw as? JsonArray ?: return emptyList()) {
                val fav = when (item) {
                    // Legacy entries are bare route ids.
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

/** Port of favoritePlatform in stores/favorites.ts. */
fun NetworkIndex?.favoritePlatform(stopId: Int, favorites: Collection<Int>): Int? =
    (this?.platforms(stopId)?.map { it.stopId }?.ifEmpty { null } ?: listOf(stopId)).firstOrNull { it in favorites }
