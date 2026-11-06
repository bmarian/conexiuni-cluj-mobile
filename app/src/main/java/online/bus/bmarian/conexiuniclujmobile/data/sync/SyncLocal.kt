package online.bus.bmarian.conexiuniclujmobile.data.sync

import android.os.Build
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.data.Recent
import online.bus.bmarian.conexiuniclujmobile.data.RecentGroup
import online.bus.bmarian.conexiuniclujmobile.data.RecentPlan
import online.bus.bmarian.conexiuniclujmobile.data.RecentRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentStop
import online.bus.bmarian.conexiuniclujmobile.data.RegionSettings
import online.bus.bmarian.conexiuniclujmobile.data.SavedGroup
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.City
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Group
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Place
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.RecentItem
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Spot
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.HomeSections
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

    private fun toSpot(name: String?, rawLat: Double, rawLon: Double): Spot? {
        val lat = SyncRecords.roundCoordinate(rawLat)
        val lon = SyncRecords.roundCoordinate(rawLon)
        if (lat !in SyncRecords.LAT || lon !in SyncRecords.LON) return null
        return Spot(cleanText(name, 100).ifEmpty { coordsText(lat, lon) }, lat, lon)
    }

    private fun toPlace(p: FavoritePlan): Place? {
        val spot = toSpot(p.name, p.lat, p.lon) ?: return null
        val origin = if (p.originLat != null && p.originLon != null) toSpot(p.originName, p.originLat, p.originLon) ?: return null else null
        return Place(spot.name, cleanText(p.label, 40), spot.lat, spot.lon, origin)
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

    fun newsFromLocal(slug: String) =
        SyncRecords.News(Services.routeUpdates.seenIn(slug).filter { it in validId }.distinct().let(SyncRecords::newestSeen))

    fun applyNews(slug: String, r: SyncRecords.News) = Services.routeUpdates.replaceSeen(r.seen, slug)

    private fun toRecentItem(r: Recent): RecentItem? = when (r) {
        is RecentStop -> RecentItem.Stop(r.stopId.toLong()).takeIf { it.id in validId }
        is RecentRoute -> RecentItem.Line(r.routeId.toLong()).takeIf { it.id in validId }
        is RecentPlan -> toPlace(r.plan)?.let(RecentItem::Spot)
        is RecentGroup -> toLines(r.lines).takeIf { it.isNotEmpty() }?.let(RecentItem::Group)
    }

    private fun toRecent(item: RecentItem): Recent = when (item) {
        is RecentItem.Stop -> RecentStop(item.id.toInt())
        is RecentItem.Line -> RecentRoute(item.id.toInt())
        is RecentItem.Spot -> RecentPlan(toPlan(item.place))
        is RecentItem.Group -> RecentGroup(fromLines(item.lines))
    }

    fun recentsFromLocal(slug: String) =
        SyncRecords.Recents(Services.favorites.saved(slug).recents.mapNotNull(::toRecentItem).distinctBy(SyncRecords::recentId))

    fun applyRecents(slug: String, r: SyncRecords.Recents) = Services.favorites.replaceRecents(slug, r.recent.map(::toRecent))

    private fun toLines(lines: List<GroupLine>) = lines.filter { it.routeId.toLong() in validId && (it.direction == "0" || it.direction == "1") }
        .map { SyncRecords.Line(it.routeId.toLong(), it.direction) }.distinctBy { it.route }.take(SyncRecords.GROUP_LINES_CAP)

    private fun fromLines(lines: List<SyncRecords.Line>) = lines.map { GroupLine(it.route.toInt(), it.dir) }

    private fun toGroup(g: SavedGroup): Group? {
        val lines = toLines(g.lines).takeIf { it.isNotEmpty() } ?: return null
        return Group(lines, cleanText(g.name, 40))
    }

    private fun fromGroup(g: Group) = SavedGroup(fromLines(g.lines), g.name.ifEmpty { null })

    fun groupsFromLocal(slug: String) = SyncRecords.Groups(Services.groups.savedIn(slug).mapNotNull(::toGroup).distinctBy(SyncRecords::groupId))

    fun applyGroups(slug: String, r: SyncRecords.Groups) = Services.groups.replaceSynced(slug, r.groups.map(::fromGroup))

    fun syncedCities(): List<String> = Cities.list.value.map { it.slug }

    data class OverCap(val city: String, val list: String, val count: Int)

    /** Lists already past their cap, which sync will not cut on its own. */
    fun overCaps(): List<OverCap> = syncedCities().flatMap { city ->
        val r = cityFromLocal(city)
        val groups = groupsFromLocal(city).groups.size
        listOf("stops" to r.stops.size, "lines" to r.lines.size, "places" to r.places.size, "followed" to r.followed.size, "groups" to groups)
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

    fun homeFromLocal(): SyncRecords.Home {
        val s = Services.settings
        return SyncRecords.Home(s.homeOrder.value, s.homeCollapsed.value.toList())
    }

    fun applyHome(r: SyncRecords.Home) {
        val s = Services.settings
        if (HomeSections.order(r.order) != s.homeOrder.value) s.setHomeOrder(r.order)
        if (r.collapsed.toSet() != s.homeCollapsed.value) s.setHomeCollapsed(r.collapsed.toSet())
    }

    fun optionsFromLocal(): SyncRecords.Options {
        val s = Services.settings
        return SyncRecords.Options(s.colorfulLines.value, Cities.choice.value.orEmpty(), s.journeyVoice.value, s.fullScreenAlarms.value, s.traffic.value)
    }

    fun applyOptions(r: SyncRecords.Options) {
        val s = Services.settings
        if (s.colorfulLines.value != r.colorfulLines) s.setColorfulLines(r.colorfulLines)
        r.journeyVoice?.takeIf { it != s.journeyVoice.value }?.let(s::setJourneyVoice)
        r.fullScreenAlarms?.takeIf { it != s.fullScreenAlarms.value }?.let(s::setFullScreenAlarms)
        r.traffic?.takeIf { it != s.traffic.value }?.let(s::setTraffic)
        if (Cities.choice.value.orEmpty() != r.city && (r.city.isEmpty() || Cities.known(r.city))) {
            val slug = r.city.ifEmpty { null }
            // A screen open on the old city keeps it until the app next opens.
            if (Services.atHome.value) Services.chooseCity(slug) else Cities.choose(slug)
        }
    }

    fun deviceLabel(): String {
        val model = Build.MODEL.orEmpty()
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
        val label = if (model.startsWith(maker, ignoreCase = true) || maker.isEmpty()) model else "$maker $model"
        return cleanText(label, 40).ifEmpty { "Android" }
    }
}
