package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.view.Choreographer
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.EaseIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.animation.doOnEnd
import com.google.gson.JsonArray
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.RoutePath
import online.bus.bmarian.conexiuniclujmobile.logic.VehicleTrack
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.logic.roundedHull
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconTranslate
import org.maplibre.android.style.layers.PropertyFactory.iconTranslateAnchor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SRC_LINES = "ct-lines"
private const val SRC_WALKS = "ct-walks"
private const val SRC_STOPS = "ct-stops"
private const val SRC_HIGHLIGHTS = "ct-highlights"
private const val SRC_SELECTED = "ct-selected"
private const val SRC_ENDS = "ct-ends"
private const val SRC_BADGES = "ct-badges"
private const val SRC_ARROWS = "ct-arrows"
private const val SRC_VEHICLES = "ct-vehicles"
private const val SRC_PINS = "ct-pins"
private const val SRC_AREA = "ct-area"
private const val SRC_SPIDER = "ct-spider"
private const val SRC_SWEEP = "ct-sweep"

private const val LAYER_STOPS = "ct-stops"
private const val LAYER_HIGHLIGHTS = "ct-highlights"
private const val LAYER_SPIDER = "ct-spider"
private const val LAYER_SELECTED = "ct-selected"
private const val LAYER_VEHICLES = "ct-vehicles"
private const val LAYER_PINS = "ct-pins"

private val HEADING_OFFSET = 12.0 to 12.0

private fun isPuckLayer(layer: Layer) = layer.id.contains("location", ignoreCase = true)

private const val FADED_VEHICLE_OPACITY = 0.5f
private const val FADED_VEHICLE_SIZE = 0.7f

private const val GLIDE_MAX_METERS = 600.0
private const val POP_METERS = 50.0
private const val MIN_STEP_PX = 0.3
private const val MAX_IDLE_MS = 250L

// The selected stop leads its own group; highlighted stops stay out of the groups.
private data class ClusterKey(val anchor: Int?, val skip: Set<Int>)

private val PLAIN = ClusterKey(null, emptySet())
private const val CITY_FLIGHT_MS = 2_500
private const val GLIDE_MS = 1400L

/** In px. */
data class MapPadding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun ints() = intArrayOf(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

enum class PinKind { Origin, Destination }

class PinMark(val kind: PinKind, val at: LatLon, val label: String, val lifted: Boolean)

class ShownVehicle(val vehicle: MapVehicle, val at: LatLon, val heading: Double, val opacity: Float = 1f, val scale: Float = 1f, val leaving: Boolean = false)

class SpiderHit(val stopId: Int, val at: LatLon, val shift: PointF)

internal class MapSession(private val context: Context, val map: MapLibreMap) {
    private val images = MapImages(context)
    private var style: Style? = null
    private var dark: Boolean? = null
    private val addedImages = HashSet<String>()

    val frame = mutableIntStateOf(0)

    private var stops: List<Stop> = emptyList()
    private var stopsById: Map<Int, Stop> = emptyMap()
    private var looks: NetworkIndex? = null
    private var favorites: Set<Int> = emptySet()
    private var areas: List<Area> = emptyList()
    // The plain map's stays for coming back to it.
    private val clusterCache = HashMap<ClusterKey, Clusters>()
    private var drawn: Clusters? = null
    private var building: Pair<Clusters, Job>? = null
    private val scope = MainScope()
    private val clustering = Dispatchers.Default.limitedParallelism(1)
    private var shownKeys: Set<String>? = null
    private var shownGroups: Map<String, StopGroup> = emptyMap()
    private var shownFeatures: Map<String, List<Feature>> = emptyMap()
    private val groupImages = HashSet<String>()
    private val fades = MarkerFades()
    private var spider: MapSpider? = null
    private var around: MapSpider? = null
    private var aroundFor: Pair<List<StopGroup>, StopGroup>? = null
    private var aroundGroup: String? = null
    private val folding = ArrayList<MapSpider>()
    private var stopsDirty = false
    private var spiderDirty = false
    private var lastFrame = 0L
    private val motion = FrameLoop(::frame)
    private var hasVehicles = false
    private var selected: Stop? = null
    private var highlights: List<Pair<Stop, StopHighlight>> = emptyList()
    private var scene = MapScene.Empty
    private var walks: List<List<LatLon>> = emptyList()
    private var pins: List<PinMark> = emptyList()
    private var me: Fix? = null
    private var rideAs: RideAs? = null
    private var puck: RideAs? = null
    private var puckStyled = false
    private var lastBearing: Float? = null
    private var selectedVehicleId: String? = null
    private val glide = VehicleGlide({ map.projection.getMetersPerPixelAtLatitude(map.cameraPosition.target?.latitude ?: 0.0) }) { pushVehicles() }
    private var drop: ValueAnimator? = null
    private var recolor: ValueAnimator? = null
    private var drawnFavorites = emptySet<String>()
    private var tracking: NavCamera? = null
    private var tracked: NavCamera? = null
    private var trackingMove = 0
    private var settling = false

    var onTrackingLost: (() -> Unit)? = null

    private var bearing = map.cameraPosition.bearing

    init {
        map.addOnCameraMoveListener {
            frame.intValue++
            refreshStops()
            val now = map.cameraPosition.bearing
            if (abs(now - bearing) > 0.5) {
                bearing = now
                pushVehicles()
            }
        }
        map.addOnCameraIdleListener {
            MapCamera.save(context, map.cameraPosition)
            refreshStops()
        }
    }

    fun setDark(dark: Boolean) {
        if (this.dark == dark) return
        this.dark = dark
        style = null
        map.setStyle(Style.Builder().fromJson(MapStyles.json(context, dark))) { loaded ->
            style = loaded
            addedImages.clear()
            fades.clearLeaving()
            spider = null
            around = null
            aroundFor = null
            folding.clear()
            puckStyled = false
            addLayers(loaded, dark)
            applyAll()
        }
    }

    fun setStops(list: List<Stop>, index: NetworkIndex?) {
        if (list === stops && index === looks) return
        stops = list
        stopsById = list.associateBy { it.stopId }
        looks = index
        clusterCache.clear()
        refreshStops(force = true)
        applySelected()
    }

    fun setFavorites(ids: Set<Int>) {
        if (ids == favorites) return
        favorites = ids
        clusterCache.clear()
        refreshStops(force = true)
    }

    fun setAreas(list: List<Area>) {
        if (list === areas) return
        areas = list
        clusterCache.clear()
        refreshStops(force = true)
    }

    fun setSelectedStop(stop: Stop?) {
        if (stop?.stopId == selected?.stopId) return
        selected = stop
        aroundGroup = null
        applySelected()
        refreshStops()
        drop?.cancel()
        if (stop != null) {
            drop = ValueAnimator.ofFloat(-18f, 0f).apply {
                duration = 450
                interpolator = OvershootInterpolator(2.5f)
                addUpdateListener { a ->
                    style?.getLayerAs<SymbolLayer>(LAYER_SELECTED)?.setProperties(iconTranslate(arrayOf(0f, a.animatedValue as Float)))
                }
                doOnEnd { drop = null }
                start()
            }
        }
    }

    fun setHighlights(list: List<Pair<Stop, StopHighlight>>) {
        highlights = list
        applyHighlights()
        refreshStops()
    }

    /** [favorites] by line name, so a line unsaved sweeps back from its last stop. */
    fun setScene(value: MapScene, favorites: Set<String> = emptySet()) {
        val before = scene
        val saved = drawnFavorites
        scene = value
        drawnFavorites = favorites
        recolor?.cancel()
        applyScene()
        recolorLines(before, value, saved)
        refreshStops()
    }

    private fun recolorLines(before: MapScene, after: MapScene, saved: Set<String>) {
        val same = before.lines.size == after.lines.size &&
            before.lines.zip(after.lines).all { (a, b) -> a.points === b.points && a.dash == b.dash }
        if (!same) return
        val changed = after.lines.indices.filter { before.lines[it].color != after.lines[it].color && after.lines[it].dash == 0 }
        if (changed.isEmpty()) return
        val sweeps = changed.map { i -> Triple(after.lines[i], after.lines[i].name in saved && after.lines[i].name !in drawnFavorites, lineArgb(after.lines[i].color)) }
        fun paint(done: Float) = source(SRC_SWEEP)?.setGeoJson(
            FeatureCollection.fromFeatures(
                sweeps.mapNotNull { (line, backwards, color) ->
                    sweptPart(line.points, done, backwards).takeIf { it.size >= 2 }?.let { part ->
                        Feature.fromGeometry(LineString.fromLngLats(part.map(::point))).apply {
                            addStringProperty("color", "#%06X".format(0xFFFFFF and color))
                        }
                    }
                },
            ),
        )
        applyLines(after.lines.indices.map { lineArgb((if (it in changed) before else after).lines[it].color) })
        recolor = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a -> paint(a.animatedValue as Float) }
            doOnEnd {
                applyLines()
                paint(0f)
                recolor = null
            }
            start()
        }
    }

    fun setWalks(list: List<List<LatLon>>) {
        if (list == walks) return
        walks = list
        applyWalks()
        refreshStops()
    }

    fun setPins(list: List<PinMark>) {
        pins = list
        applyPins()
    }

    fun setVehiclePaths(paths: Map<String, RoutePath>) = glide.setPaths(paths)

    fun setVehicles(list: List<MapVehicle>) {
        glide.update(list)
        if (list.isNotEmpty() == hasVehicles) return
        hasVehicles = list.isNotEmpty()
        refreshStops()
    }

    fun selectVehicle(id: String?) {
        if (id == selectedVehicleId) return
        selectedVehicleId = id
        pushVehicles()
    }

    fun setMe(fix: Fix?) {
        me = fix
        fix?.bearing?.let { lastBearing = it }
        applyMe()
    }

    /** On board, the location arrow takes the line's colour. */
    fun setRideAs(ride: RideAs?) {
        if (ride == rideAs) return
        rideAs = ride
        applyPuck()
    }

    private fun puckOptions(ride: RideAs?): LocationComponentOptions {
        // Untinted: tinting flattens the GPS arrow.
        val builder = LocationComponentOptions.builder(context)
            .accuracyColor(MapColors.MeAccuracy.toArgb())
            .accuracyAlpha(if (ride == null) 0.15f else 0f)
            .pulseEnabled(false)
            .enableStaleState(false)
        if (ride != null) {
            val name = "nav-arrow-${ride.color}"
            image(name) { images.navArrow(ride.color) }
            builder.gpsName(name)
        }
        return builder.build()
    }

    private fun applyPuck() {
        val component = map.locationComponent
        if (style == null || !component.isLocationComponentActivated) return
        if (puckStyled && puck == rideAs) return
        puck = rideAs
        puckStyled = true
        component.applyStyle(puckOptions(rideAs))
    }

    fun setTracking(camera: NavCamera?) {
        if (camera == tracking) return
        tracking = camera
        applyTracking()
    }

    fun screen(at: LatLon): PointF = map.projection.toScreenLocation(LatLng(at.lat, at.lon))

    fun latLonAt(point: PointF): LatLon = map.projection.fromScreenLocation(point).let { LatLon(it.latitude, it.longitude) }

    fun shownVehicle(id: String): ShownVehicle? = glide.shown(id)

    fun vehicleAt(point: PointF, reach: Float): String? =
        map.queryRenderedFeatures(RectF(point.x - reach, point.y - reach, point.x + reach, point.y + reach), LAYER_VEHICLES)
            .firstOrNull { !it.hasProperty("gone") }?.getStringProperty("id")

    fun spiderAt(point: PointF, reach: Float): SpiderHit? {
        if (spider == null && around == null) return null
        val id = map.queryRenderedFeatures(RectF(point.x - reach, point.y - reach, point.x + reach, point.y + reach), LAYER_SPIDER)
            .firstNotNullOfOrNull { f -> if (f.hasProperty("closing")) null else f.getNumberProperty("id")?.toInt() } ?: return null
        val open = listOfNotNull(spider, around).firstOrNull { it.item(id) != null } ?: return null
        val leg = open.item(id)?.leg ?: return null
        val px = context.resources.displayMetrics.density
        return SpiderHit(id, open.at, PointF(leg.x * px, leg.y * px))
    }

    fun stopAt(point: PointF, reach: Float): Int? {
        val box = RectF(point.x - reach, point.y - reach, point.x + reach, point.y + reach)
        return map.queryRenderedFeatures(box, LAYER_SELECTED, LAYER_HIGHLIGHTS, LAYER_STOPS)
            .mapNotNull { f ->
                if (f.hasProperty("gone")) return@mapNotNull null
                val id = f.getNumberProperty("id")?.toInt() ?: return@mapNotNull null
                val p = f.geometry() as? Point ?: return@mapNotNull null
                val s = screen(LatLon(p.latitude(), p.longitude()))
                id to hypot(s.x - point.x, s.y - point.y)
            }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first
    }

    fun groupAt(point: PointF, reach: Float): StopGroup? =
        map.queryRenderedFeatures(RectF(point.x - reach, point.y - reach, point.x + reach, point.y + reach), LAYER_STOPS)
            .firstNotNullOfOrNull { f ->
                val key = if (f.hasProperty("group") && !f.hasProperty("gone")) f.getStringProperty("group") else null
                key?.takeUnless(fades::hidden)?.let(shownGroups::get)
            }

    fun leaderOf(group: StopGroup): LatLon? = drawn?.index?.at(group)

    fun groupCard(group: StopGroup): List<Pair<String, StationLook?>> =
        groupStations(group).map { (id, _) -> stopsById[id]?.name.orEmpty() to looks?.look(id) }

    fun groupStops(group: StopGroup): List<Int> = groupStations(group).map { it.first }

    /** Zooms in until the group breaks up, no further than where its stations all stand alone, or spreads out stations that never part. */
    suspend fun expand(group: StopGroup, padding: suspend () -> MapPadding) {
        val index = drawn?.index ?: return
        fades.pulse(group.key, System.nanoTime())
        stopsDirty = true
        motion.start()
        val members = index.members(group)
        val north = members.maxOf { it.lat }
        val south = members.minOf { it.lat }
        val east = members.maxOf { it.lon }
        val west = members.minOf { it.lon }
        val center = LatLon(latOf((mercatorY(north) + mercatorY(south)) / 2), lonOf((mercatorX(west) + mercatorX(east)) / 2))
        val bounds = LatLngBounds.from(north, east, south, west)
        if (group.area != null) {
            unspiderfy()
            val pad = padding()
            val fitted = map.getCameraForLatLngBounds(bounds, pad.ints())?.zoom ?: map.cameraPosition.zoom
            return flyTo(center, max(fitted, index.lowestStationZoom() ?: fitted), pad)
        }
        val e = index.expansion(group, map.cameraPosition.zoom) ?: return spiderfy(index, group)
        unspiderfy()
        val pad = padding()
        val fitted = map.getCameraForLatLngBounds(bounds, pad.ints())?.zoom ?: e.apart
        flyTo(center, min(e.apart, max(e.split, fitted)), pad)
    }

    private fun spiderfy(index: MapClusters, group: StopGroup) {
        val stations = groupStations(group)
        val spots = stations.mapNotNull { stopsById[it.first] }
        val centre = LatLon(spots.sumOf { it.lat } / spots.size, spots.sumOf { it.lon } / spots.size)
        val legs = spiderLegsToward(stations.map { (id, _) -> stopsById[id]?.let { angle(centre, LatLon(it.lat, it.lon)) } ?: 0.0 })
        unspiderfy()
        spider = spiderOf(group.key, index.at(group), stations, legs, motionOn()) ?: return
        fades.hide(group.key, true, System.nanoTime())
        stopsDirty = true
        spiderDirty = true
        motion.start()
    }

    private fun angle(from: LatLon, to: LatLon) = atan2(mercatorY(to.lat) - mercatorY(from.lat), mercatorX(to.lon) - mercatorX(from.lon))

    private fun spiderOf(key: String, at: LatLon, stations: List<Pair<Int, Boolean>>, legs: List<SpiderLeg>, animate: Boolean): MapSpider? {
        val dark = dark ?: return null
        val legsImg = "spider-$dark-" + legs.joinToString(",") { "${it.x}:${it.y}" }
        image(legsImg) { images.spider(legs, dark) }
        val items = stations.mapIndexed { i, (id, favorite) ->
            val name = stopsById[id]?.name.orEmpty()
            val icon = if (favorite) GroupIcon.Heart else stationIcon(id)
            val img = "spi-$dark-${legs[i].left}-$icon-$name"
            image(img) { images.spiderItem(icon, name, legs[i].left, dark) }
            SpiderItem(id, img, legs[i], images.spiderItemShift(name))
        }
        val now = System.nanoTime()
        return MapSpider(key, at, legsImg, items, if (animate) now else now - (SPIDER_OPEN_MS + SPIDER_STEP_MS * items.size) * 1_000_000)
    }

    // The stations the selected stop's marker covers fan out around it, clear of the other markers.
    private fun aroundSpider(index: MapClusters, group: StopGroup, zoom: Double): MapSpider? {
        val stations = groupStations(group).drop(1)
        if (stations.isEmpty()) return null
        val (obstacles, offset) = index.surroundings(group, zoom)
        val places = stations.map { (id, _) ->
            val stop = stopsById.getValue(id)
            val (label, height) = images.spiderLabel(stop.name)
            val (x, y) = offset(LatLon(stop.lat, stop.lon))
            SpiderPlace(x, y, SPIDER_ITEM_BOX, label.toDouble(), height.toDouble())
        }
        return spiderOf(group.key, index.at(group), stations, spiderLegsClear(places, obstacles), motionOn() && aroundGroup != group.key)
    }

    private fun showAround(next: MapSpider?) {
        val gone = around
        around = next
        aroundGroup = next?.key
        if (gone != null) dropSpiderImages(gone)
        spiderDirty = true
        motion.start()
    }

    fun unspiderfy() {
        val open = spider ?: return
        spider = null
        val now = System.nanoTime()
        fades.hide(open.key, false, now)
        if (motionOn()) {
            open.close(now)
            folding += open
        } else {
            dropSpiderImages(open)
        }
        stopsDirty = true
        spiderDirty = true
        motion.start()
    }

    private fun dropSpiderImages(gone: MapSpider) {
        val kept = listOfNotNull(spider, around).filter { it !== gone }.flatMap { it.images }.toSet() + folding.filter { it !== gone }.flatMap { it.images }
        for (name in gone.images.toSet() - kept) {
            style?.removeImage(name)
            addedImages.remove(name)
        }
    }

    /** The ground a group stands for, widened by half a station icon so its stops sit inside it. */
    fun setArea(group: StopGroup?) {
        val src = source(SRC_AREA) ?: return
        val index = drawn?.index
        if (group == null || index == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val radius = STATION_BOX.width / 2 * lonPerDp(map.cameraPosition.zoom)
        val ring = roundedHull(index.members(group).map { it.lon to it.lat }, radius)
        src.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring.map { (lon, lat) -> Point.fromLngLat(lon, lat) }))))
    }

    fun fit(points: List<LatLon>, padding: MapPadding, maxZoom: Double = FIT_MAX_ZOOM) {
        if (points.isEmpty()) return
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }
        if (maxLat - minLat < 1e-6 && maxLon - minLon < 1e-6) {
            flyTo(points.first(), maxZoom, padding)
            return
        }
        val bounds = LatLngBounds.from(maxLat, maxLon, minLat, minLon)
        val bearing = map.cameraPosition.bearing
        val fitted = map.getCameraForLatLngBounds(bounds, padding.ints(), bearing, 0.0) ?: return
        val center = LatLng(latOf((mercatorY(maxLat) + mercatorY(minLat)) / 2), lonOf((mercatorX(minLon) + mercatorX(maxLon)) / 2))
        val camera = CameraPosition.Builder()
            .target(center)
            .zoom(min(fitted.zoom, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM))
            .bearing(bearing)
            .tilt(0.0)
            .padding(padding.left.toDouble(), padding.top.toDouble(), padding.right.toDouble(), padding.bottom.toDouble())
            .build()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(camera), 650)
    }

    fun flyTo(target: LatLon, zoom: Double, padding: MapPadding) {
        val to = LatLng(target.lat, target.lon)
        val camera = CameraPosition.Builder()
            .target(to)
            .zoom(zoom)
            .padding(padding.left.toDouble(), padding.top.toDouble(), padding.right.toDouble(), padding.bottom.toDouble())
            .build()
        val here = map.cameraPosition
        val from = here.target?.let(map.projection::toScreenLocation)
        val there = map.projection.toScreenLocation(to)
        val distance = if (from == null) 0.0 else hypot(there.x - from.x, there.y - from.y) / context.resources.displayMetrics.density.toDouble()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(camera), flightMs(distance, zoom - here.zoom))
    }

    fun cancelCamera() = map.cancelTransitions()

    fun view(): CameraView? {
        val camera = map.cameraPosition
        val target = camera.target ?: return null
        return CameraView(LatLon(target.latitude, target.longitude), camera.zoom, camera.bearing, camera.padding)
    }

    fun ease(view: CameraView) {
        val camera = CameraPosition.Builder()
            .target(LatLng(view.center.lat, view.center.lon))
            .zoom(view.zoom)
            .bearing(view.bearing)
        view.padding?.let { camera.padding(it) }
        map.easeCamera(CameraUpdateFactory.newCameraPosition(camera.build()), 650)
    }

    private var cityFlight = 0
    var flying by mutableStateOf(false)
        private set
    private var cityBounds: LatLngBounds? = null

    /** The camera stays over the city; lifted while flying in from another one. */
    fun setCityBounds(bounds: LatLngBounds?) {
        cityBounds = bounds
        if (!flying) map.setLatLngBoundsForCameraTarget(bounds)
    }

    fun flyToCity(camera: MapCamera, bounds: LatLngBounds?) {
        val flight = ++cityFlight
        cityBounds = bounds
        flying = true
        map.cancelTransitions()
        map.setLatLngBoundsForCameraTarget(null)
        val landed = {
            if (flight == cityFlight) {
                flying = false
                map.setLatLngBoundsForCameraTarget(cityBounds)
            }
        }
        val target = CameraPosition.Builder()
            .target(LatLng(camera.center.lat, camera.center.lon))
            .zoom(camera.zoom)
            .bearing(0.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(target), CITY_FLIGHT_MS, object : MapLibreMap.CancelableCallback {
            override fun onFinish() = landed()

            override fun onCancel() = landed()
        })
    }

    private fun addLayers(style: Style, dark: Boolean) {
        listOf(SRC_LINES, SRC_SWEEP, SRC_WALKS, SRC_STOPS, SRC_HIGHLIGHTS, SRC_SELECTED, SRC_ENDS, SRC_BADGES, SRC_ARROWS, SRC_VEHICLES, SRC_PINS, SRC_AREA, SRC_SPIDER)
            .forEach { style.addSource(GeoJsonSource(it)) }
        addBaseImages(style, dark)

        // Under the basemap's labels.
        val layers = style.layers.filterNot(::isPuckLayer)
        val lastGround = layers.indexOfLast { it !is SymbolLayer && !it.id.startsWith("boundary") }
        val firstLabel = layers.drop(lastGround + 1).firstOrNull { it is SymbolLayer }?.id
        fun under(layer: Layer) = if (firstLabel != null) style.addLayerBelow(layer, firstLabel) else style.addLayer(layer)
        under(
            LineLayer("ct-lines-casing", SRC_LINES).withProperties(
                lineColor(if (dark) MapColors.InkDark.toArgb() else android.graphics.Color.WHITE),
                lineWidth(8f),
                lineOpacity(0.9f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(eq(get("dash"), literal(0))),
        )
        DUPLICATE_DASH_PATTERNS.forEachIndexed { i, pattern ->
            val layer = LineLayer("ct-lines-$i", SRC_LINES).withProperties(
                lineColor(toColor(get("color"))),
                lineWidth(5f),
                lineOpacity(0.9f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(eq(get("dash"), literal(i)))
            // Dash arrays are in line widths.
            if (pattern.isNotEmpty()) layer.setProperties(lineDasharray(pattern.map { it / 5f }.toTypedArray()))
            under(layer)
        }
        under(
            LineLayer("ct-sweep", SRC_SWEEP).withProperties(
                lineColor(toColor(get("color"))),
                lineWidth(5f),
                lineOpacity(0.9f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        under(
            LineLayer("ct-walks", SRC_WALKS).withProperties(
                lineColor(MapColors.Walk.toArgb()),
                lineWidth(4.5f),
                lineDasharray(arrayOf(0f, 2f)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        val areaInk = if (dark) MapColors.Paper.toArgb() else MapColors.InkDark.toArgb()
        under(FillLayer("ct-area-fill", SRC_AREA).withProperties(fillColor(areaInk), fillOpacity(0.18f)))
        under(LineLayer("ct-area-line", SRC_AREA).withProperties(lineColor(areaInk), lineWidth(2f), lineOpacity(0.85f)))

        val puck = style.layers.firstOrNull(::isPuckLayer)?.id
        fun over(layer: SymbolLayer) {
            layer.setProperties(iconAllowOverlap(true), iconIgnorePlacement(true))
            if (puck != null) style.addLayerBelow(layer, puck) else style.addLayer(layer)
        }
        fun animated(id: String, source: String) = SymbolLayer(id, source).withProperties(
            iconImage(get("img")),
            iconOffset(get("off")),
            iconOpacity(get("opacity")),
            iconSize(get("size")),
            symbolSortKey(get("z")),
        )
        over(animated(LAYER_STOPS, SRC_STOPS))
        over(SymbolLayer(LAYER_HIGHLIGHTS, SRC_HIGHLIGHTS).withProperties(iconImage(get("img")), symbolSortKey(get("z"))))
        over(animated(LAYER_SPIDER, SRC_SPIDER))
        over(SymbolLayer("ct-ends", SRC_ENDS).withProperties(iconImage(get("img"))))
        over(SymbolLayer("ct-badges", SRC_BADGES).withProperties(iconImage(get("img"))))
        over(
            SymbolLayer("ct-arrow-bubble", SRC_ARROWS).withProperties(
                iconImage("arrow-bubble"),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconTranslate(arrayOf(0f, -16f)),
                iconTranslateAnchor(Property.ICON_TRANSLATE_ANCHOR_VIEWPORT),
            ),
        )
        over(
            SymbolLayer("ct-arrow-glyph", SRC_ARROWS).withProperties(
                iconImage("arrow-glyph"),
                iconRotate(get("bearing")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconTranslate(arrayOf(0f, -33f)),
                iconTranslateAnchor(Property.ICON_TRANSLATE_ANCHOR_VIEWPORT),
            ),
        )
        over(SymbolLayer(LAYER_SELECTED, SRC_SELECTED).withProperties(iconImage(get("img"))))
        over(
            SymbolLayer(LAYER_VEHICLES, SRC_VEHICLES).withProperties(
                iconImage(get("img")),
                iconRotate(get("rot")),
                iconOffset(get("off")),
                iconOpacity(get("opacity")),
                iconSize(get("size")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                symbolSortKey(get("z")),
            ),
        )
        over(SymbolLayer(LAYER_PINS, SRC_PINS).withProperties(iconImage(get("img")), iconAnchor(Property.ICON_ANCHOR_BOTTOM)))
    }

    private fun addBaseImages(style: Style, dark: Boolean) {
        style.addImage("stop", images.stop(dark))
        style.addImage("stop-selected", images.selectedStop())
        StopHighlight.entries.forEach { style.addImage("hl-${it.name}", images.highlight(it)) }
        style.addImage("arrow-bubble", images.arrowBubble(dark))
        style.addImage("arrow-glyph", images.arrowGlyph(dark))
        style.addImage("heading", images.heading())
        style.addImage("pin-Origin", images.pin(MapColors.Origin))
        style.addImage("pin-Destination", images.pin(MapColors.Destination))
        style.addImage("pin-Origin-lifted", images.liftedPin(MapColors.Origin))
        style.addImage("pin-Destination-lifted", images.liftedPin(MapColors.Destination))
    }

    private fun image(name: String, make: () -> android.graphics.Bitmap) {
        val style = style ?: return
        if (addedImages.add(name)) style.addImage(name, make())
    }

    private fun source(id: String): GeoJsonSource? = style?.getSourceAs(id)

    private fun point(at: LatLon) = Point.fromLngLat(at.lon, at.lat)

    private fun applyAll() {
        groupImages.clear()
        refreshStops(force = true)
        applySelected()
        applyHighlights()
        applyScene()
        applyWalks()
        applyPins()
        pushVehicles()
        applyMe()
    }

    private fun stationImage(stopId: Int, selected: Boolean): String {
        val look = looks?.look(stopId) ?: return if (selected) "stop-selected" else "stop"
        val name = "${if (selected) "sel" else "st"}-${look.mode}-${look.color}"
        image(name) { if (selected) images.selectedStation(look.mode, look.color) else images.station(look.mode, look.color) }
        return name
    }

    private class GroupLabel(val name: String, val more: Int)

    /** Created and built on the clustering thread, a step at a time. */
    private class Clusters(val stopsById: Map<Int, Stop>, private val create: (Clusters) -> MapClusters) {
        @Volatile
        var index: MapClusters? = null
            private set
        val labels = ConcurrentHashMap<String, GroupLabel>()

        fun ready(zoom: Double) = index?.ready(zoom) == true

        fun advance() {
            val built = index
            if (built == null) index = create(this) else built.advance()
        }
    }

    private fun stopClusters(): Clusters? {
        val anchor = selected?.stopId
        val key = ClusterKey(anchor, highlights.mapTo(HashSet()) { it.first.stopId } - setOfNotNull(anchor))
        clusterCache[key]?.let { return it }
        if (stops.isEmpty()) return null
        clusterCache.keys.retainAll { it == PLAIN }
        val list = stops
        val net = looks
        val starred = favorites
        val known = areas.takeIf { key == PLAIN }.orEmpty()
        val clusters = Clusters(stopsById) { built ->
            val points = list.filter { it.stopId !in key.skip && (net == null || net.look(it.stopId) != null) }.map { s ->
                val types = net?.look(s.stopId)?.routes?.map { it.type }.orEmpty()
                val rank = if (s.stopId == anchor) Int.MAX_VALUE else stationRank(types)
                StopPoint(s.stopId, s.lat, s.lon, s.station, modeMask(types).takeIf { it != 0 } ?: modeMask(listOf(3)), rank, s.stopId in starred)
            }
            lateinit var index: MapClusters
            val boxOf = { group: StopGroup ->
                when {
                    anchor != null && points[group.leader].id == anchor -> SELECTED_BOX
                    group.stations.size == 1 && group.area == null -> STATION_BOX
                    else -> groupLabel(built, index, group).let { images.groupBox(iconCount(group), it.name, it.more) }
                }
            }
            val stations = StopClusters(points, boxOf, MAX_ZOOM, CLUSTER_ZOOM_STEP)
            val towns = if (known.isEmpty()) null else AreaClusters(stations, known, boxOf)
            index = MapClusters(stations, towns, CLUSTER_STATIONS_MAX)
            index
        }
        clusterCache[key] = clusters
        return clusters
    }

    // Levels are built off the main thread, so a zoom never waits for them.
    private fun build(clusters: Clusters) {
        if (building?.first === clusters) return
        building?.second?.cancel()
        building = clusters to scope.launch {
            while (!clusters.ready(if (showsContent()) STOP_ZOOM else MIN_ZOOM)) {
                val waiting = !clusters.ready(map.cameraPosition.zoom)
                withContext(clustering) { clusters.advance() }
                if (waiting && clusters.ready(map.cameraPosition.zoom)) refreshStops()
            }
            if (building?.first === clusters) building = null
        }
    }

    private fun groupLabel(clusters: Clusters, index: MapClusters, group: StopGroup): GroupLabel = clusters.labels.getOrPut(group.key) {
        index.areaName(group)?.let { return@getOrPut GroupLabel(it, 0) }
        val leader = index.points[group.leader]
        val others = LinkedHashMap<Int, String>()
        for (i in group.points) {
            val p = index.points[i]
            if (p.station != leader.station && p.station !in others) others[p.station] = clusters.stopsById[p.id]?.name.orEmpty()
        }
        val named = groupName(clusters.stopsById[leader.id]?.name.orEmpty(), others.values.toList())
        GroupLabel(named.name, others.size - named.covered)
    }

    private fun iconCount(group: StopGroup) =
        if (group.stations.size <= CLUSTER_STATIONS_MAX) group.stations.size else modesIn(group.modes).size + if (group.favorite) 1 else 0

    /** One stop per station with whether any of its platforms is a favourite, the group's leader first. */
    private fun groupStations(group: StopGroup): List<Pair<Int, Boolean>> {
        val index = drawn?.index ?: return emptyList()
        val byStation = LinkedHashMap<Int, Triple<Int, Int, Boolean>>()
        for (i in listOf(group.leader) + group.points) {
            val p = index.points[i]
            val seen = byStation[p.station]
            byStation[p.station] = if (seen == null) Triple(p.id, p.rank, p.favorite) else seen.copy(third = seen.third || p.favorite)
        }
        val all = byStation.values.toList()
        return (listOf(all.first()) + all.drop(1).sortedByDescending { it.second }).map { it.first to it.third }
    }

    private fun stationIcon(id: Int): GroupIcon = looks?.look(id)?.let { GroupIcon.Station(it.mode, it.color) } ?: GroupIcon.Station(Mode.BUS, "")

    private fun groupIcons(group: StopGroup): List<GroupIcon> =
        if (group.stations.size <= CLUSTER_STATIONS_MAX) {
            groupStations(group).map { (id, favorite) -> if (favorite) GroupIcon.Heart else stationIcon(id) }
        } else {
            listOfNotNull(GroupIcon.Heart.takeIf { group.favorite }) + modesIn(group.modes).map { GroupIcon.Station(it, looks?.modeColor(it).orEmpty()) }
        }

    // While the map shows a stop's, line's or trip's own content, other stops only appear from street zoom.
    private fun showsContent() = scene.lines.isNotEmpty() || hasVehicles || walks.isNotEmpty() || highlights.isNotEmpty() || selected != null

    // Only the stops around the view are on the map, so a pan only redraws when the groups in reach change.
    // Until the latest groups are built for this zoom, the ones on the map stay.
    private fun refreshStops(force: Boolean = false) {
        val src = source(SRC_STOPS) ?: return
        val dark = dark ?: return
        val zoom = map.cameraPosition.zoom
        var clusters: Clusters? = null
        var redraw = force
        if (!showsContent() || zoom >= STOP_ZOOM) {
            stopClusters()?.let { latest ->
                build(latest)
                clusters = if (latest.ready(zoom)) latest else drawn?.takeIf { it.ready(zoom) } ?: return
            }
        }
        if (clusters != null && clusters !== drawn) {
            unspiderfy()
            drawn = clusters
            redraw = true
        }
        val bounds = map.projection.visibleRegion.latLngBounds
        val padLat = (bounds.latitudeNorth - bounds.latitudeSouth) / 2
        val padLon = (bounds.longitudeEast - bounds.longitudeWest) / 2
        val index = clusters?.index
        val inReach = if (index == null) emptyList() else index.at(zoom).filter { group ->
            val p = index.at(group)
            p.lat in bounds.latitudeSouth - padLat..bounds.latitudeNorth + padLat && p.lon in bounds.longitudeWest - padLon..bounds.longitudeEast + padLon
        }
        val anchored = if (index == null) null else inReach.firstOrNull { index.points[it.leader].id == selected?.stopId }
        val visible = inReach.filter { it !== anchored }
        if (index != null && anchored != null && anchored.stations.size > 1) {
            val level = index.at(zoom)
            if (aroundFor?.first !== level || aroundFor?.second !== anchored) {
                aroundFor = level to anchored
                showAround(aroundSpider(index, anchored, zoom))
            }
        } else if (around != null) {
            aroundFor = null
            showAround(null)
        }
        val keys = visible.mapTo(HashSet()) { it.key }
        if (!redraw && keys == shownKeys) return
        shownKeys = keys
        shownGroups = visible.filter { it.stations.size > 1 || it.area != null }.associateBy { it.key }
        spider?.takeIf { it.key !in keys }?.let { unspiderfy() }
        val now = System.nanoTime()
        val used = HashSet<String>()
        val built = LinkedHashMap<String, List<Feature>>()
        clusters?.let { c -> for (group in visible) built[group.key] = stopFeatures(c, group, dark, used) }
        for ((key, features) in shownFeatures) if (key !in built) fades.leave(key, features, now)
        for (key in built.keys) if (key !in shownFeatures) fades.enter(key, now)
        fades.forget(built.keys)
        shownFeatures = built
        val kept = used + fades.leavingImages().filter { it in groupImages }
        for (name in groupImages - kept) {
            style?.removeImage(name)
            addedImages.remove(name)
        }
        groupImages.clear()
        groupImages += kept
        stopsDirty = true
        motion.start()
    }

    private fun stopFeatures(clusters: Clusters, group: StopGroup, dark: Boolean, used: MutableSet<String>): List<Feature> {
        val index = clusters.index ?: return emptyList()
        val leader = index.points[group.leader]
        val single = group.stations.size == 1 && group.area == null
        fun feature(img: String, offsetY: Float, block: Feature.() -> Unit = {}) = Feature.fromGeometry(point(index.at(group))).apply {
            addStringProperty("img", img)
            addNumberProperty("z", if (single) 0 else 1)
            addProperty("off", JsonArray().apply { add(0); add(offsetY) })
            addNumberProperty("opacity", 1)
            addNumberProperty("size", 1)
            block()
        }
        if (single) {
            return listOf(feature(if (group.favorite) "hl-${StopHighlight.Favorite.name}" else stationImage(leader.id, false), 0f) { addNumberProperty("id", leader.id) })
        }
        val label = groupLabel(clusters, index, group)
        val icons = groupIcons(group)
        val pill = "grp-$dark-${icons.joinToString(",")}"
        val text = "grl-$dark-${label.name}-${label.more}"
        image(pill) { images.groupPill(icons, dark) }
        image(text) { images.groupLabel(label.name, label.more, dark) }
        used += pill
        used += text
        return listOf(
            feature(pill, 0f) { addStringProperty("group", group.key) },
            feature(text, images.labelOffset(icons.size, label.name, label.more)) {
                addStringProperty("group", group.key)
                addBooleanProperty("label", true)
            },
        )
    }

    private fun frame(now: Long): Boolean {
        val previous = lastFrame
        lastFrame = now
        val stopsMoving = fades.moving(previous)
        if (stopsDirty || stopsMoving) pushStops(now)
        stopsDirty = false
        val spiderMoving = spider?.moving(previous) == true || around?.moving(previous) == true
        val folded = folding.filter { !it.moving(previous) }
        folding.removeAll(folded)
        folded.forEach(::dropSpiderImages)
        if (spiderDirty || spiderMoving || folding.isNotEmpty() || folded.isNotEmpty()) pushSpider(now)
        spiderDirty = false
        return stopsMoving || spiderMoving || folding.isNotEmpty()
    }

    private fun pushStops(now: Long) {
        val src = source(SRC_STOPS) ?: return
        val live = shownFeatures.flatMap { (key, features) ->
            val look = fades.look(key, now)
            features.map { f ->
                copy(f).apply {
                    addNumberProperty("opacity", look.opacity)
                    addNumberProperty("size", if (f.hasProperty("label")) look.bodyScale else look.scale)
                }
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(fades.leaving(now) + live))    }

    private fun pushSpider(now: Long) {
        source(SRC_SPIDER)?.setGeoJson(FeatureCollection.fromFeatures(folding.flatMap { it.features(now) } + listOfNotNull(around, spider).flatMap { it.features(now) }))
    }

    private fun applySelected() {
        val src = source(SRC_SELECTED) ?: return
        val stop = selected
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    stop?.let {
                        Feature.fromGeometry(point(LatLon(it.lat, it.lon))).apply {
                            addNumberProperty("id", it.stopId)
                            addStringProperty("img", stationImage(it.stopId, true))
                        }
                    },
                ),
            ),
        )
    }

    private fun applyHighlights() {
        val src = source(SRC_HIGHLIGHTS) ?: return
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                highlights.filter { it.first.stopId != selected?.stopId }.map { (stop, h) ->
                    Feature.fromGeometry(point(LatLon(stop.lat, stop.lon))).apply {
                        addNumberProperty("id", stop.stopId)
                        addStringProperty("img", "hl-${h.name}")
                        // Green over purple over amber over red over gray; further south on top.
                        addNumberProperty("z", MapColors.zIndex(h) - stop.lat)
                    }
                },
            ),
        )
    }

    private fun lineArgb(color: String): Int = (if (color.isEmpty()) MapColors.ShapeFallback else routeColor(color)).toArgb()

    private fun applyLines(colors: List<Int> = scene.lines.map { lineArgb(it.color) }): Boolean {
        val src = source(SRC_LINES) ?: return false
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.lines.zip(colors).filter { (line, _) -> line.points.size >= 2 }.map { (line, color) ->
                    Feature.fromGeometry(LineString.fromLngLats(line.points.map(::point))).apply {
                        addStringProperty("color", "#%06X".format(0xFFFFFF and color))
                        addNumberProperty("dash", line.dash)
                    }
                },
            ),
        )
        return true
    }

    private fun applyScene() {
        val dark = dark ?: return
        if (!applyLines()) return
        source(SRC_ENDS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.ends.map { end ->
                    val name = "end-${end.color}"
                    image(name) { images.end(end.color, dark) }
                    Feature.fromGeometry(point(end.at)).apply { addStringProperty("img", name) }
                },
            ),
        )
        source(SRC_BADGES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.badges.map { badge ->
                    val name = "badge-" + badge.routes.joinToString(",") { "${it.first}:${it.second}" }
                    image(name) { images.badge(badge.routes, dark) }
                    Feature.fromGeometry(point(badge.at)).apply { addStringProperty("img", name) }
                },
            ),
        )
        source(SRC_ARROWS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.arrows.map { arrow -> Feature.fromGeometry(point(arrow.at)).apply { addNumberProperty("bearing", arrow.bearing) } },
            ),
        )
    }

    private fun applyWalks() {
        source(SRC_WALKS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                walks.filter { it.size >= 2 }.map { Feature.fromGeometry(LineString.fromLngLats(it.map(::point))) },
            ),
        )
    }

    private fun applyPins() {
        source(SRC_PINS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                pins.map { pin ->
                    Feature.fromGeometry(point(pin.at)).apply {
                        addStringProperty("img", "pin-${pin.kind.name}" + if (pin.lifted) "-lifted" else "")
                        addStringProperty("kind", pin.kind.name)
                    }
                },
            ),
        )
    }

    private fun pushVehicles() {
        val src = source(SRC_VEHICLES)
        if (src != null) {
            val ordered = glide.all().sortedWith(compareBy<ShownVehicle>({ it.vehicle.id == selectedVehicleId }, { !it.vehicle.faded }, { -it.at.lat }))
            src.setGeoJson(
                FeatureCollection.fromFeatures(
                    ordered.flatMapIndexed { i, shown ->
                        val v = shown.vehicle
                        val name = "veh-${v.color}-${v.shortName}"
                        image(name) { images.vehicle(v.color, v.shortName) }
                        val rot = shown.heading - bearing
                        val r = Math.toRadians(rot)
                        val (ox, oy) = HEADING_OFFSET
                        listOf(
                            vehicleFeature(shown, name, z = 2 * i, rot = 0.0, off = 0.0 to 0.0),
                            vehicleFeature(shown, "heading", z = 2 * i + 1, rot = rot, off = (ox * cos(r) + oy * sin(r)) to (oy * cos(r) - ox * sin(r))),
                        )
                    },
                ),
            )
        }
        frame.intValue++
    }

    private fun vehicleFeature(shown: ShownVehicle, img: String, z: Int, rot: Double, off: Pair<Double, Double>): Feature {
        val faded = shown.vehicle.faded && shown.vehicle.id != selectedVehicleId
        return Feature.fromGeometry(point(shown.at)).apply {
            addStringProperty("id", shown.vehicle.id)
            addStringProperty("img", img)
            addNumberProperty("z", z)
            addNumberProperty("rot", rot)
            addNumberProperty("opacity", (if (faded) FADED_VEHICLE_OPACITY else 1f) * shown.opacity)
            addNumberProperty("size", (if (faded) FADED_VEHICLE_SIZE else 1f) * shown.scale)
            if (shown.leaving) addBooleanProperty("gone", true)
            addProperty("off", JsonArray().apply { add(off.first); add(off.second) })
        }
    }

    @SuppressLint("MissingPermission")
    private fun applyMe() {
        val style = style ?: return
        val component = map.locationComponent
        val fix = me
        if (fix == null || !UserLocation.permitted(context)) {
            if (component.isLocationComponentActivated) component.isLocationComponentEnabled = false
            return
        }
        if (!component.isLocationComponentActivated) {
            val options = puckOptions(rideAs)
            puck = rideAs
            puckStyled = true
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(options)
                    .useDefaultLocationEngine(false)
                    .build(),
            )
            component.renderMode = RenderMode.NORMAL
            component.cameraMode = CameraMode.NONE
            component.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    if (tracking != null) onTrackingLost?.invoke()
                }

                override fun onCameraTrackingChanged(currentMode: Int) {}
            })
        }
        applyPuck()
        val first = !component.isLocationComponentEnabled
        component.isLocationComponentEnabled = true
        component.forceLocationUpdate(
            Location("app").apply {
                latitude = fix.lat
                longitude = fix.lon
                accuracy = fix.accuracyMeters
                // A fix without a heading keeps the last one instead of turning the map north.
                (fix.bearing ?: lastBearing)?.let { bearing = it }
                fix.speed?.let { speed = it }
                time = System.currentTimeMillis()
            },
        )
        if (first) applyTracking()
    }

    @SuppressLint("MissingPermission")
    private fun applyTracking() {
        val camera = tracking
        map.uiSettings.isTiltGesturesEnabled = camera != null
        map.uiSettings.isRotateGesturesEnabled = camera != null
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (camera == null) {
            trackingMove++
            settling = false
            tracked = null
            val following = component.cameraMode != CameraMode.NONE || component.renderMode != RenderMode.NORMAL
            if (following) {
                // Otherwise its own animations keep moving the camera.
                component.cancelZoomWhileTrackingAnimation()
                component.cancelTiltWhileTrackingAnimation()
                component.cancelPaddingWhileTrackingAnimation()
                component.cameraMode = CameraMode.NONE
                component.renderMode = RenderMode.NORMAL
            }
            val position = map.cameraPosition
            if (!following && position.bearing == 0.0 && position.tilt == 0.0) return
            val flat = CameraPosition.Builder(position).tilt(0.0).bearing(0.0)
            if (following) flat.padding(0.0, 0.0, 0.0, 0.0)
            map.easeCamera(CameraUpdateFactory.newCameraPosition(flat.build()), 600)
            return
        }
        // The bus badge keeps its number upright; the camera still turns with the ride.
        component.renderMode = RenderMode.GPS
        if (settling) return
        if (component.cameraMode == CameraMode.TRACKING_GPS) {
            if (camera == tracked) return
            tracked = camera
            component.zoomWhileTracking(camera.zoom, 800L)
            component.tiltWhileTracking(camera.tilt, 800L)
            component.paddingWhileTracking(doubleArrayOf(0.0, camera.top.toDouble(), 0.0, camera.bottom.toDouble()), 800L)
            return
        }
        val fix = component.lastKnownLocation ?: return
        val move = ++trackingMove
        settling = true
        // MapLibre's own tracking transition drops padding changes made while it runs.
        val target = CameraPosition.Builder()
            .target(LatLng(fix.latitude, fix.longitude))
            .zoom(camera.zoom)
            .tilt(camera.tilt)
            .bearing(fix.bearing.toDouble())
            .padding(0.0, camera.top.toDouble(), 0.0, camera.bottom.toDouble())
            .build()
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(target),
            800,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    if (move != trackingMove) return
                    component.setCameraMode(
                        CameraMode.TRACKING_GPS,
                        object : OnLocationCameraTransitionListener {
                            override fun onLocationCameraTransitionFinished(cameraMode: Int) = settled(move, camera)

                            override fun onLocationCameraTransitionCanceled(cameraMode: Int) = settled(move, null)
                        },
                    )
                }

                override fun onCancel() {
                    if (move != trackingMove) return
                    settling = false
                    onTrackingLost?.invoke()
                }
            },
        )
    }

    private fun settled(move: Int, camera: NavCamera?) {
        if (move != trackingMove) return
        settling = false
        tracked = camera
        applyTracking()
    }

    fun destroy() {
        scope.cancel()
        glide.stop()
        motion.stop()
        drop?.cancel()
        recolor?.cancel()
    }
}

private object MapStyles {
    private val cache = HashMap<Boolean, String>()

    fun json(context: Context, dark: Boolean): String = cache.getOrPut(dark) {
        context.assets.open(if (dark) "styles/dark.json" else "styles/light.json").bufferedReader().use { it.readText() }
    }
}

private class VehicleGlide(private val metersPerPixel: () -> Double, private val onFrame: () -> Unit) : Choreographer.FrameCallback {
    private class Track(var vehicle: MapVehicle, var from: LatLon, var to: LatLon, var fromHeading: Double, var toHeading: Double, var at: LatLon, var heading: Double) {
        var born: Long? = null
        var gone: Long? = null
        var follow: VehicleTrack? = null
        var untracked: String? = null
        var drawn: LatLon? = null
        var drawnHeading = 0.0
    }

    private val tracks = LinkedHashMap<String, Track>()
    private var paths: Map<String, RoutePath> = emptyMap()
    private var startNanos = 0L
    private var lastFrame = 0L

    fun update(list: List<MapVehicle>) {
        val now = System.nanoTime()
        val animate = motionOn()
        val ids = HashSet<String>()
        for (v in list) {
            if (v.vehicle.latitude <= 0 || v.vehicle.longitude <= 0) continue
            ids += v.id
            val target = LatLon(v.vehicle.latitude, v.vehicle.longitude)
            val track = tracks[v.id]
            if (track == null || track.gone != null) {
                tracks[v.id] = Track(v, target, target, v.heading, v.heading, target, v.heading).apply {
                    if (animate) born = now
                    follow(this, now)
                }
                continue
            }
            track.vehicle = v
            val far = haversineMeters(track.at.lat, track.at.lon, target.lat, target.lon) > GLIDE_MAX_METERS
            track.from = if (far) target else track.at
            track.to = target
            track.fromHeading = if (far) v.heading else track.heading
            track.toHeading = v.heading
            follow(track, now)
        }
        if (animate) {
            for ((id, track) in tracks) if (id !in ids && track.gone == null) track.gone = now
        } else {
            tracks.keys.retainAll(ids)
        }
        startNanos = now
        push()
        run()
    }

    fun setPaths(paths: Map<String, RoutePath>) {
        this.paths = paths
        val now = System.nanoTime()
        for (track in tracks.values) if (track.gone == null) follow(track, now)
        push()
        run()
    }

    private fun follow(track: Track, now: Long) {
        val v = track.vehicle.vehicle
        val tripId = v.tripId
        val path = tripId?.let(paths::get)
        val fixMs = runCatching { OffsetDateTime.parse(v.timestamp).toInstant().toEpochMilli() }.getOrNull()
        if (!motionOn() || path == null || fixMs == null || track.untracked == tripId) {
            track.follow = null
            return
        }
        val fresh = track.follow?.path !== path
        val follow = if (fresh) VehicleTrack(path) else track.follow!!
        if (!follow.fix(v.latitude, v.longitude, fixMs, v.speed, v.heading, System.currentTimeMillis())) {
            track.follow = null
            track.untracked = tripId
            return
        }
        if (fresh) {
            val (lat, lon) = follow.position
            if (track.born == null && haversineMeters(track.at.lat, track.at.lon, lat, lon) > POP_METERS) track.born = now
            track.at = LatLon(lat, lon)
            track.heading = follow.heading
        }
        track.follow = follow
    }

    private fun run() {
        if (tracks.values.none { it.from != it.to || it.fromHeading != it.toHeading || it.born != null || it.gone != null || it.follow != null }) return
        // Cancels any idle wait.
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun push() {
        for (track in tracks.values) {
            track.drawn = track.at
            track.drawnHeading = track.heading
        }
        onFrame()
    }

    private fun moved(): Boolean {
        val step = metersPerPixel() * MIN_STEP_PX
        return tracks.values.any { track ->
            val drawn = track.drawn ?: return@any true
            haversineMeters(drawn.lat, drawn.lon, track.at.lat, track.at.lon) >= step ||
                abs((track.heading - track.drawnHeading + 540) % 360 - 180) >= 1
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        val now = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        val previous = lastFrame
        lastFrame = now
        val t = ((now - startNanos) / 1_000_000.0 / GLIDE_MS).coerceIn(0.0, 1.0)
        val e = if (t < 0.5) 4 * t * t * t else 1 - Math.pow(-2 * t + 2, 3.0) / 2
        tracks.values.removeAll { track -> track.gone?.let { progress(it, previous, LEAVE_MS) >= 1f } == true }
        for (track in tracks.values) {
            val follow = track.follow
            if (follow == null) {
                track.at = LatLon(track.from.lat + (track.to.lat - track.from.lat) * e, track.from.lon + (track.to.lon - track.from.lon) * e)
                track.heading = headingBetween(track.fromHeading, track.toHeading, e)
            } else if (track.gone == null) {
                if (follow.step(nowMs)) track.born = now
                val (lat, lon) = follow.position
                track.at = LatLon(lat, lon)
                track.heading = follow.heading
            }
            if (track.born?.let { progress(it, previous, ENTER_MS) >= 1f } == true) track.born = null
        }
        val animating = t < 1.0 || tracks.values.any { it.born != null || it.gone != null }
        if (animating || moved()) push()
        if (animating) {
            Choreographer.getInstance().postFrameCallback(this)
        } else if (tracks.values.any { it.follow != null }) {
            // Until the fastest bus has moved far enough to see.
            val fastest = tracks.values.maxOf { it.follow?.v ?: 0.0 }
            val idle = if (fastest <= 0) MAX_IDLE_MS else (metersPerPixel() * MIN_STEP_PX / fastest * 1000).toLong().coerceAtMost(MAX_IDLE_MS)
            Choreographer.getInstance().postFrameCallbackDelayed(this, idle)
        } else {
            for (track in tracks.values) {
                track.from = track.to
                track.fromHeading = track.toHeading
            }
        }
    }

    private fun shown(track: Track, now: Long): ShownVehicle {
        val enter = track.born?.let { Snappy.transform(progress(it, now, ENTER_MS)) } ?: 1f
        val leave = track.gone?.let { 1 - EaseIn.transform(progress(it, now, LEAVE_MS)) } ?: 1f
        return ShownVehicle(track.vehicle, track.at, track.heading, enter * leave, ENTER_SCALE + (1 - ENTER_SCALE) * enter, track.gone != null)
    }

    fun all(): List<ShownVehicle> {
        val now = System.nanoTime()
        return tracks.values.map { shown(it, now) }
    }

    fun shown(id: String): ShownVehicle? = tracks[id]?.takeIf { it.gone == null }?.let { shown(it, System.nanoTime()) }

    fun stop() {
        Choreographer.getInstance().removeFrameCallback(this)
    }
}
