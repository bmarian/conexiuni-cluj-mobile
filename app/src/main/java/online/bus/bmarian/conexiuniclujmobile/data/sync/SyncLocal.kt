package online.bus.bmarian.conexiuniclujmobile.data.sync

import android.os.Build
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.data.RegionSettings
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.City
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Origin
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Place
import java.text.Normalizer
import java.util.Locale

/** This device's own data as sync records, and records written back into it. Port of utils/sync/local.ts. */
object SyncLocal {
    /** Drops what the text rule refuses, trims the ends, cuts to [max] code points. */
    fun cleanText(value: String?, max: Int): String {
        if (value == null) return ""
        val points = Normalizer.normalize(value, Normalizer.Form.NFC).codePoints().toArray().filter { !SyncRecords.forbidden(it) }.toMutableList()
        while (points.isNotEmpty() && SyncRecords.blankEnd(points.first())) points.removeAt(0)
        val cut = points.take(max).toMutableList()
        while (cut.isNotEmpty() && SyncRecords.blankEnd(cut.last())) cut.removeAt(cut.size - 1)
        return String(cut.toIntArray(), 0, cut.size)
    }

    private fun coordsText(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.4f, %.4f", lat, lon)

    private fun toPlace(p: FavoritePlan): Place? {
        val lat = SyncRecords.roundCoordinate(p.lat)
        val lon = SyncRecords.roundCoordinate(p.lon)
        if (lat !in SyncRecords.LAT || lon !in SyncRecords.LON) return null
        val origin = if (p.originLat != null && p.originLon != null) {
            val oLat = SyncRecords.roundCoordinate(p.originLat)
            val oLon = SyncRecords.roundCoordinate(p.originLon)
            if (oLat !in SyncRecords.LAT || oLon !in SyncRecords.LON) return null
            Origin(cleanText(p.originName, 100).ifEmpty { coordsText(oLat, oLon) }, oLat, oLon)
        } else null
        return Place(cleanText(p.name, 100).ifEmpty { coordsText(lat, lon) }, cleanText(p.label, 40), lat, lon, origin)
    }

    private fun toPlan(p: Place) = FavoritePlan(
        name = p.name,
        label = p.label.ifEmpty { null },
        lat = p.lat,
        lon = p.lon,
        originName = p.origin?.name,
        originLat = p.origin?.lat,
        originLon = p.origin?.lon,
    )

    private val validId = 1L..Int.MAX_VALUE.toLong()

    fun cityFromLocal(slug: String): City {
        val saved = Services.favorites.saved(slug)
        return City(
            stops = saved.stops.map(Int::toLong).filter { it in validId }.distinct(),
            lines = saved.routes.filter { it.routeId.toLong() in validId && (it.direction == "0" || it.direction == "1") }
                .map { SyncRecords.Line(it.routeId.toLong(), it.direction) }.distinctBy(SyncRecords::lineId),
            places = saved.plans.mapNotNull(::toPlace).distinctBy(SyncRecords::placeId),
            followed = Services.routeUpdates.followedIn(slug).map { cleanText(it, 16) }.filter(String::isNotEmpty).distinct(),
        )
    }

    fun applyCity(slug: String, r: City) {
        Services.favorites.replaceSynced(
            slug,
            r.lines.map { FavoriteRoute(it.route.toInt(), it.dir) },
            r.stops.map(Long::toInt),
            r.places.map(::toPlan),
        )
        Services.routeUpdates.replaceFollowed(r.followed, slug)
    }

    fun syncedCities(): List<String> = Cities.list.value.map { it.slug }

    data class OverCap(val city: String, val list: String, val count: Int)

    /** Lists already past their cap, which sync will not cut on its own. */
    fun overCaps(): List<OverCap> = syncedCities().flatMap { city ->
        val r = cityFromLocal(city)
        listOf("stops" to r.stops.size, "lines" to r.lines.size, "places" to r.places.size, "followed" to r.followed.size)
            .filter { it.second > SyncRecords.CITY_CAP }.map { OverCap(city, it.first, it.second) }
    }

    fun settingsFromLocal(): SyncRecords.Settings {
        val s = Services.settings
        return SyncRecords.Settings(
            theme = s.theme.value.key,
            locale = s.locale.value,
            autoCenterOnMe = s.autoCenterOnMe.value,
            autoFitMap = s.autoFitMap.value,
            showVehicleExtras = true,
            greenFriday = s.regionSetting(Cities.DEFAULT, RegionSettings.GREEN_FRIDAY),
        )
    }

    fun applySettings(r: SyncRecords.Settings) {
        val s = Services.settings
        if (s.theme.value.key != r.theme) ThemeMode.of(r.theme)?.let(s::setTheme)
        if (s.locale.value != r.locale) s.setLocale(r.locale)
        if (s.autoCenterOnMe.value != r.autoCenterOnMe) s.setAutoCenterOnMe(r.autoCenterOnMe)
        if (s.autoFitMap.value != r.autoFitMap) s.setAutoFitMap(r.autoFitMap)
        if (s.regionSetting(Cities.DEFAULT, RegionSettings.GREEN_FRIDAY) != r.greenFriday) {
            s.setRegionSetting(Cities.DEFAULT, RegionSettings.GREEN_FRIDAY, r.greenFriday)
        }
    }

    fun deviceLabel(): String {
        val model = Build.MODEL.orEmpty()
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
        val label = if (model.startsWith(maker, ignoreCase = true) || maker.isEmpty()) model else "$maker $model"
        return cleanText(label, 40).ifEmpty { "Android" }
    }
}
