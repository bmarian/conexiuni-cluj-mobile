package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.view.Choreographer
import android.view.animation.OvershootInterpolator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.core.animation.doOnEnd
import com.google.gson.JsonArray
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.neq
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
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

private const val LAYER_STOPS = "ct-stops"
private const val LAYER_HIGHLIGHTS = "ct-highlights"
private const val LAYER_SELECTED = "ct-selected"
private const val LAYER_VEHICLES = "ct-vehicles"
private const val LAYER_PINS = "ct-pins"

private val HEADING_OFFSET = 12.0 to 12.0

private fun isPuckLayer(layer: Layer) = layer.id.contains("location", ignoreCase = true)

private const val GLIDE_MAX_METERS = 600.0
private const val GLIDE_MS = 1400L

/** In px. */
data class MapPadding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun ints() = intArrayOf(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

enum class PinKind { Origin, Destination }

class PinMark(val kind: PinKind, val at: LatLon, val label: String, val lifted: Boolean)

class ShownVehicle(val vehicle: MapVehicle, val at: LatLon, val heading: Double)

internal class MapSession(private val context: Context, val map: MapLibreMap) {
    private val images = MapImages(context)
    private var style: Style? = null
    private var dark: Boolean? = null
    private val addedImages = HashSet<String>()

    val frame = mutableIntStateOf(0)

    private var stops: List<Stop> = emptyList()
    private var selected: Stop? = null
    private var highlights: List<Pair<Stop, StopHighlight>> = emptyList()
    private var scene = MapScene.Empty
    private var walks: List<List<LatLon>> = emptyList()
    private var pins: List<PinMark> = emptyList()
    private var me: Fix? = null
    private var selectedVehicleId: String? = null
    private val glide = VehicleGlide { pushVehicles() }
    private var drop: ValueAnimator? = null
    private var tracking: NavCamera? = null

    var onTrackingLost: (() -> Unit)? = null

    private var bearing = map.cameraPosition.bearing

    init {
        map.addOnCameraMoveListener {
            frame.intValue++
            val now = map.cameraPosition.bearing
            if (abs(now - bearing) > 0.5) {
                bearing = now
                pushVehicles()
            }
        }
        map.addOnCameraIdleListener { MapCamera.save(context, map.cameraPosition) }
    }

    fun setDark(dark: Boolean) {
        if (this.dark == dark) return
        this.dark = dark
        style = null
        map.setStyle(Style.Builder().fromJson(MapStyles.json(context, dark))) { loaded ->
            style = loaded
            addedImages.clear()
            addLayers(loaded, dark)
            applyAll()
        }
    }

    fun setStops(list: List<Stop>) {
        if (list === stops) return
        stops = list
        applyStops()
    }

    fun setSelectedStop(stop: Stop?) {
        if (stop?.stopId == selected?.stopId) return
        selected = stop
        applySelected()
        applyStopFilter()
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
    }

    fun setScene(value: MapScene) {
        scene = value
        applyScene()
    }

    fun setWalks(list: List<List<LatLon>>) {
        if (list == walks) return
        walks = list
        applyWalks()
    }

    fun setPins(list: List<PinMark>) {
        pins = list
        applyPins()
    }

    fun setVehicles(list: List<MapVehicle>) = glide.update(list)

    fun selectVehicle(id: String?) {
        if (id == selectedVehicleId) return
        selectedVehicleId = id
        pushVehicles()
    }

    fun setMe(fix: Fix?) {
        me = fix
        applyMe()
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
            .firstOrNull()?.getStringProperty("id")

    fun stopAt(point: PointF, reach: Float): Int? {
        val box = RectF(point.x - reach, point.y - reach, point.x + reach, point.y + reach)
        return map.queryRenderedFeatures(box, LAYER_SELECTED, LAYER_HIGHLIGHTS, LAYER_STOPS)
            .mapNotNull { f ->
                val id = f.getNumberProperty("id")?.toInt() ?: return@mapNotNull null
                val p = f.geometry() as? Point ?: return@mapNotNull null
                val s = screen(LatLon(p.latitude(), p.longitude()))
                id to hypot(s.x - point.x, s.y - point.y)
            }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first
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
        val camera = CameraPosition.Builder()
            .target(LatLng(target.lat, target.lon))
            .zoom(zoom)
            .padding(padding.left.toDouble(), padding.top.toDouble(), padding.right.toDouble(), padding.bottom.toDouble())
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 1000)
    }

    fun cancelCamera() = map.cancelTransitions()

    private fun addLayers(style: Style, dark: Boolean) {
        listOf(SRC_LINES, SRC_WALKS, SRC_STOPS, SRC_HIGHLIGHTS, SRC_SELECTED, SRC_ENDS, SRC_BADGES, SRC_ARROWS, SRC_VEHICLES, SRC_PINS)
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
            LineLayer("ct-walks", SRC_WALKS).withProperties(
                lineColor(MapColors.Walk.toArgb()),
                lineWidth(4.5f),
                lineDasharray(arrayOf(0f, 2f)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        val puck = style.layers.firstOrNull(::isPuckLayer)?.id
        fun over(layer: SymbolLayer) {
            layer.setProperties(iconAllowOverlap(true), iconIgnorePlacement(true))
            if (puck != null) style.addLayerBelow(layer, puck) else style.addLayer(layer)
        }
        over(SymbolLayer(LAYER_STOPS, SRC_STOPS).withProperties(iconImage("stop")).also { it.minZoom = STOP_ZOOM })
        over(SymbolLayer(LAYER_HIGHLIGHTS, SRC_HIGHLIGHTS).withProperties(iconImage(get("img")), symbolSortKey(get("z"))))
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
        over(SymbolLayer(LAYER_SELECTED, SRC_SELECTED).withProperties(iconImage("stop-selected")))
        over(
            SymbolLayer(LAYER_VEHICLES, SRC_VEHICLES).withProperties(
                iconImage(get("img")),
                iconRotate(get("rot")),
                iconOffset(get("off")),
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
        applyStops()
        applyStopFilter()
        applySelected()
        applyHighlights()
        applyScene()
        applyWalks()
        applyPins()
        pushVehicles()
        applyMe()
    }

    private fun applyStops() {
        val src = source(SRC_STOPS) ?: return
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                stops.map { s ->
                    Feature.fromGeometry(point(LatLon(s.lat, s.lon))).apply { addNumberProperty("id", s.stopId) }
                },
            ),
        )
    }

    private fun applyStopFilter() {
        style?.getLayerAs<SymbolLayer>(LAYER_STOPS)?.setFilter(neq(get("id"), literal(selected?.stopId ?: -1)))
    }

    private fun applySelected() {
        val src = source(SRC_SELECTED) ?: return
        val stop = selected
        src.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(stop?.let { Feature.fromGeometry(point(LatLon(it.lat, it.lon))).apply { addNumberProperty("id", it.stopId) } }),
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

    private fun hex(color: String): String =
        "#%06X".format(0xFFFFFF and (if (color.isEmpty()) MapColors.ShapeFallback else routeColor(color)).toArgb())

    private fun applyScene() {
        val dark = dark ?: return
        source(SRC_LINES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.lines.filter { it.points.size >= 2 }.map { line ->
                    Feature.fromGeometry(LineString.fromLngLats(line.points.map(::point))).apply {
                        addStringProperty("color", hex(line.color))
                        addNumberProperty("dash", line.dash)
                    }
                },
            ),
        ) ?: return
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
            val ordered = glide.all().sortedWith(compareBy<ShownVehicle>({ it.vehicle.id == selectedVehicleId }, { -it.at.lat }))
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

    private fun vehicleFeature(shown: ShownVehicle, img: String, z: Int, rot: Double, off: Pair<Double, Double>) =
        Feature.fromGeometry(point(shown.at)).apply {
            addStringProperty("id", shown.vehicle.id)
            addStringProperty("img", img)
            addNumberProperty("z", z)
            addNumberProperty("rot", rot)
            addProperty("off", JsonArray().apply { add(off.first); add(off.second) })
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
            // Untinted: tinting flattens the GPS arrow.
            val options = LocationComponentOptions.builder(context)
                .accuracyColor(MapColors.MeAccuracy.toArgb())
                .accuracyAlpha(0.15f)
                .pulseEnabled(false)
                .enableStaleState(false)
                .build()
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
        val first = !component.isLocationComponentEnabled
        component.isLocationComponentEnabled = true
        component.forceLocationUpdate(
            Location("app").apply {
                latitude = fix.lat
                longitude = fix.lon
                accuracy = fix.accuracyMeters
                fix.bearing?.let { bearing = it }
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
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (camera == null) {
            if (component.cameraMode == CameraMode.NONE && component.renderMode == RenderMode.NORMAL) return
            // Otherwise its own animations keep moving the camera.
            component.cancelZoomWhileTrackingAnimation()
            component.cancelTiltWhileTrackingAnimation()
            component.cancelPaddingWhileTrackingAnimation()
            component.cameraMode = CameraMode.NONE
            component.renderMode = RenderMode.NORMAL
            map.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(map.cameraPosition).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0).build()), 600)
            return
        }
        component.renderMode = RenderMode.GPS
        component.setCameraMode(CameraMode.TRACKING_GPS, 800L, camera.zoom, null, camera.tilt, null)
        component.paddingWhileTracking(doubleArrayOf(0.0, camera.top.toDouble(), 0.0, camera.bottom.toDouble()), 800L)
    }

    fun destroy() {
        glide.stop()
        drop?.cancel()
    }
}

private object MapStyles {
    private val cache = HashMap<Boolean, String>()

    fun json(context: Context, dark: Boolean): String = cache.getOrPut(dark) {
        context.assets.open(if (dark) "styles/dark.json" else "styles/light.json").bufferedReader().use { it.readText() }
    }
}

private class VehicleGlide(private val onFrame: () -> Unit) : Choreographer.FrameCallback {
    private class Track(var vehicle: MapVehicle, var from: LatLon, var to: LatLon, var fromHeading: Double, var toHeading: Double, var at: LatLon, var heading: Double)

    private val tracks = LinkedHashMap<String, Track>()
    private var startNanos = 0L
    private var running = false

    fun update(list: List<MapVehicle>) {
        val ids = HashSet<String>()
        for (v in list) {
            if (v.vehicle.latitude <= 0 || v.vehicle.longitude <= 0) continue
            ids += v.id
            val target = LatLon(v.vehicle.latitude, v.vehicle.longitude)
            val track = tracks[v.id]
            if (track == null) {
                tracks[v.id] = Track(v, target, target, v.heading, v.heading, target, v.heading)
                continue
            }
            track.vehicle = v
            val far = haversineMeters(track.at.lat, track.at.lon, target.lat, target.lon) > GLIDE_MAX_METERS
            track.from = if (far) target else track.at
            track.to = target
            track.fromHeading = if (far) v.heading else track.heading
            track.toHeading = v.heading
        }
        tracks.keys.retainAll(ids)
        startNanos = System.nanoTime()
        onFrame()
        if (!running && tracks.values.any { it.from != it.to || it.fromHeading != it.toHeading }) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        val t = ((System.nanoTime() - startNanos) / 1_000_000.0 / GLIDE_MS).coerceIn(0.0, 1.0)
        val e = if (t < 0.5) 4 * t * t * t else 1 - Math.pow(-2 * t + 2, 3.0) / 2
        for (track in tracks.values) {
            track.at = LatLon(track.from.lat + (track.to.lat - track.from.lat) * e, track.from.lon + (track.to.lon - track.from.lon) * e)
            track.heading = headingBetween(track.fromHeading, track.toHeading, e)
        }
        onFrame()
        if (t < 1.0) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            running = false
            for (track in tracks.values) {
                track.from = track.to
                track.fromHeading = track.toHeading
            }
        }
    }

    fun all(): List<ShownVehicle> = tracks.values.map { ShownVehicle(it.vehicle, it.at, it.heading) }

    fun shown(id: String): ShownVehicle? = tracks[id]?.let { ShownVehicle(it.vehicle, it.at, it.heading) }

    fun stop() {
        Choreographer.getInstance().removeFrameCallback(this)
        running = false
    }
}
