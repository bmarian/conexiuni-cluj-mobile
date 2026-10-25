package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.Services
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Bounds
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.RoutePath
import online.bus.bmarian.conexiuniclujmobile.logic.bikeAccess
import online.bus.bmarian.conexiuniclujmobile.logic.pathOf
import online.bus.bmarian.conexiuniclujmobile.logic.wheelchairAccess
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadges
import online.bus.bmarian.conexiuniclujmobile.ui.ModeBadge
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LineColors
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks
import online.bus.bmarian.conexiuniclujmobile.ui.theme.manyLines
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

private const val TOOLTIP_MS = 2_500L

/** In px. */
data class MapInsets(val top: Float = 0f, val bottom: Float = 0f, val right: Float = 0f, val left: Float = 0f)

private sealed interface Tooltip {
    val at: LatLon
    val shift: Offset get() = Offset.Zero

    class StopName(override val at: LatLon, val name: String, val look: StationLook?, override val shift: Offset = Offset.Zero) : Tooltip
    class Group(override val at: LatLon, val stations: List<Pair<String, StationLook?>>, val group: StopGroup) : Tooltip
    class Pin(override val at: LatLon, val label: String) : Tooltip
}

private class PinDrag(val kind: PinKind, val grab: Offset, val down: Offset) {
    var moved = false
    var at: LatLon? = null
}

@Composable
fun TransitMap(
    controller: MapController,
    camera: MapCamera,
    city: String,
    cityBounds: Bounds?,
    stops: List<Stop>,
    me: Fix?,
    dark: Boolean,
    insets: MapInsets,
    insetsMoving: () -> Boolean,
    autoFit: Boolean,
    autoCenter: Boolean,
    onStopTap: (Int) -> Unit,
    onStationsTap: (List<Int>) -> Unit,
    onNeedLocation: () -> Unit,
    modifier: Modifier = Modifier,
    compassTop: Float = 0f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val currentInsets by rememberUpdatedState(insets)
    val currentInsetsMoving by rememberUpdatedState(insetsMoving)

    suspend fun restingPadding(extra: Float = 0f): MapPadding {
        snapshotFlow { currentInsetsMoving() }.first { !it }
        val i = currentInsets
        return MapPadding(i.left + extra, i.top + extra, i.right + extra, i.bottom + extra)
    }

    val currentOnStopTap by rememberUpdatedState(onStopTap)
    val currentOnStationsTap by rememberUpdatedState(onStationsTap)
    val currentMe by rememberUpdatedState(me)
    val stopsById = remember(stops) { stops.associateBy { it.stopId } }
    val currentStopsById by rememberUpdatedState(stopsById)

    var session by remember { mutableStateOf<MapSession?>(null) }
    var tooltip by remember { mutableStateOf<Tooltip?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(tooltip) {
        if (tooltip != null) {
            delay(TOOLTIP_MS)
            tooltip = null
        }
    }
    var selectedVehicleId by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<PinDrag?>(null) }
    var dragAt by remember { mutableStateOf<LatLon?>(null) }

    val host = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(camera.center.lat, camera.center.lon))
                    .zoom(camera.zoom)
                    .bearing(camera.bearing)
                    .build(),
            )
            .minZoomPreference(MIN_ZOOM)
            .maxZoomPreference(MAX_ZOOM)
            .attributionEnabled(false)
            .logoEnabled(false)
            .tiltGesturesEnabled(false)
            .compassEnabled(true)
            .compassFadesWhenFacingNorth(true)
            .compassGravity(Gravity.TOP or Gravity.END)
            .foregroundLoadColor(if (dark) 0xFF0C0C0C.toInt() else 0xFFF2F3F0.toInt())
        MapHost(context, MapView(context, options).also { it.onCreate(null) })
    }

    DisposableEffect(lifecycle, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> host.map.onStart()
                Lifecycle.Event.ON_RESUME -> host.map.onResume()
                Lifecycle.Event.ON_PAUSE -> host.map.onPause()
                Lifecycle.Event.ON_STOP -> host.map.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host.map.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.map.onStop()
            session?.destroy()
            host.map.onDestroy()
        }
    }

    DisposableEffect(host) {
        host.map.getMapAsync { map ->
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isFlingVelocityAnimationEnabled = true
            val created = MapSession(context, map)
            map.addOnCameraIdleListener { controller.resting = created.view() }
            map.addOnCameraMoveListener { controller.bearing = map.cameraPosition.bearing }
            map.addOnMapClickListener { latLng ->
                val point = map.projection.toScreenLocation(latLng)
                val vehicle = created.vehicleAt(point, with(density) { 18.dp.toPx() })
                if (vehicle != null) {
                    selectedVehicleId = if (selectedVehicleId == vehicle) null else vehicle
                    return@addOnMapClickListener true
                }
                val spidered = created.spiderAt(point, with(density) { 8.dp.toPx() })
                if (spidered != null) {
                    tooltip = null
                    created.unspiderfy()
                    currentOnStopTap(spidered.stopId)
                    return@addOnMapClickListener true
                }
                val stop = created.stopAt(point, with(density) { 16.dp.toPx() })?.let { currentStopsById[it] }
                if (stop != null) {
                    tooltip = null
                    currentOnStopTap(stop.stopId)
                    return@addOnMapClickListener true
                }
                val group = created.groupAt(point, with(density) { 8.dp.toPx() })
                if (group != null) {
                    tooltip = null
                    if (group.stations.size <= CLUSTER_STATIONS_MAX) currentOnStationsTap(created.groupStops(group))
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { created.expand(group) { restingPadding() } }
                    return@addOnMapClickListener true
                }
                selectedVehicleId = null
                tooltip = null
                created.unspiderfy()
                false
            }
            map.addOnMapLongClickListener { latLng ->
                val point = map.projection.toScreenLocation(latLng)
                val spidered = created.spiderAt(point, with(density) { 8.dp.toPx() })
                val spideredStop = spidered?.let { currentStopsById[it.stopId] }
                if (spidered != null && spideredStop != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val look = Services.repository.network.index.value?.look(spideredStop.stopId)
                    tooltip = Tooltip.StopName(spidered.at, spideredStop.name, look, Offset(spidered.shift.x, spidered.shift.y))
                    return@addOnMapLongClickListener true
                }
                val stop = created.stopAt(point, with(density) { 20.dp.toPx() })?.let { currentStopsById[it] }
                if (stop != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    tooltip = Tooltip.StopName(LatLon(stop.lat, stop.lon), stop.name, Services.repository.network.index.value?.look(stop.stopId))
                    return@addOnMapLongClickListener true
                }
                val group = created.groupAt(point, with(density) { 8.dp.toPx() }) ?: return@addOnMapLongClickListener false
                val at = created.leaderOf(group) ?: return@addOnMapLongClickListener false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                tooltip = Tooltip.Group(at, created.groupCard(group), group)
                true
            }
            session = created
        }
        onDispose {}
    }

    host.touch = touch@{ ev ->
        val s = session ?: return@touch false
        val point = Offset(ev.x, ev.y)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val grabbed = listOfNotNull(
                    controller.customOrigin?.let { PinKind.Origin to it },
                    controller.pinnedLocation?.let { PinKind.Destination to it },
                ).firstOrNull { (_, pin) ->
                    val tip = s.screen(LatLon(pin.lat, pin.lon))
                    with(density) {
                        point.x in tip.x - 14.dp.toPx()..tip.x + 14.dp.toPx() && point.y in tip.y - 34.dp.toPx()..tip.y + 4.dp.toPx()
                    }
                } ?: return@touch false
                val tip = s.screen(LatLon(grabbed.second.lat, grabbed.second.lon))
                s.cancelCamera()
                drag = PinDrag(grabbed.first, Offset(tip.x, tip.y) - point, point)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val d = drag ?: return@touch true
                if (!d.moved && (point - d.down).getDistance() > with(density) { 3.dp.toPx() }) {
                    d.moved = true
                    tooltip = null
                }
                if (d.moved) {
                    val at = point + d.grab
                    dragAt = s.latLonAt(PointF(at.x, at.y))
                    d.at = dragAt
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val d = drag
                drag = null
                dragAt = null
                if (d != null) {
                    val pin = if (d.kind == PinKind.Origin) controller.customOrigin else controller.pinnedLocation
                    val dropped = d.at
                    when {
                        d.moved && dropped != null ->
                            if (d.kind == PinKind.Destination) controller.onPinDragged?.invoke(dropped) else controller.onOriginDragged?.invoke(dropped)
                        pin != null -> tooltip = Tooltip.Pin(LatLon(pin.lat, pin.lon), pin.label)
                    }
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                drag = null
                dragAt = null
                true
            }
            else -> true
        }
    }

    val fitPad = with(density) { 24.dp.toPx() }
    val buttonsPad = with(density) { 56.dp.toPx() }
    suspend fun fit(points: List<LatLon>) {
        val pad = restingPadding(fitPad)
        session?.fit(points, pad.copy(right = pad.right + buttonsPad))
    }

    fun content(): List<LatLon> =
        controller.shapes.flatMap { it.points } + controller.walkingPolylines.flatten() +
            listOfNotNull(controller.pinnedLocation, controller.customOrigin).map { LatLon(it.lat, it.lon) }
    suspend fun flyToVisible(target: LatLon, zoom: Double) {
        val pad = restingPadding()
        session?.flyTo(target, zoom, pad)
    }

    LaunchedEffect(session, dark) { session?.setDark(dark) }
    val bounds = cityBounds?.let { LatLngBounds.from(it.north, it.east, it.south, it.west) }
    var shownCity by remember { mutableStateOf(camera.city) }
    LaunchedEffect(session, city, bounds) {
        val s = session ?: return@LaunchedEffect
        if (city == shownCity) return@LaunchedEffect s.setCityBounds(bounds)
        shownCity = city
        s.flyToCity(MapCamera.restore(context, city), bounds)
    }
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    LaunchedEffect(session, stops, index) { session?.setStops(stops, index) }
    val favoriteStops by Services.favorites.stops.collectAsStateWithLifecycle()
    LaunchedEffect(session, favoriteStops) { session?.setFavorites(favoriteStops.toSet()) }
    val areas by produceState(emptyList<Area>(), city) { value = runCatching { Api.get<List<Area>>("areas", city) }.getOrDefault(emptyList()) }
    LaunchedEffect(session, areas) { session?.setAreas(areas) }
    LaunchedEffect(session, tooltip) { session?.setArea((tooltip as? Tooltip.Group)?.group) }
    val nav = controller.navigation
    val shownHighlights = nav?.highlights ?: controller.highlightedStops
    val shownWalks = nav?.walks ?: controller.walkingPolylines
    val shownVehicles = nav?.vehicles ?: controller.vehicles
    LaunchedEffect(session, controller.selectedStopId, stopsById, nav != null) {
        session?.setSelectedStop(controller.selectedStopId?.takeIf { nav == null }?.let { stopsById[it] })
    }
    LaunchedEffect(session, shownHighlights, stopsById) {
        session?.setHighlights(shownHighlights.mapNotNull { (id, h) -> stopsById[id]?.let { it to h } })
    }
    val shownShapes = nav?.shapes ?: controller.shapes
    val drawnLines = remember(shownShapes) { shownShapes.filter { it.points.isNotEmpty() }.map { it.shortName to it.color } }
    LaunchedEffect(drawnLines) { LineColors.draw(drawnLines) }
    val base = LocalLineLooks.current
    val looks = remember(base, drawnLines) { base.drawing(drawnLines) }
    val scene = remember(shownShapes, controller.directionArrowAtStart, nav, looks) {
        val colored = shownShapes.map { MapShape(it.tripId, it.shortName, looks.hex(it.shortName, it.color) ?: it.color, it.points) }
        if (nav != null) buildScene(colored, terminals = false, arrows = false)
        else buildScene(colored, terminals = controller.highlightedStops.isEmpty(), arrows = controller.directionArrowAtStart)
    }
    val coloredVehicles = remember(shownVehicles, looks) {
        val apart = manyLines(shownVehicles.map { it.shortName })
        shownVehicles.map { MapVehicle(it.id, it.vehicle, it.shortName, looks.hex(it.shortName, it.color, apart) ?: it.color, it.heading, it.faded) }
    }
    LaunchedEffect(session, scene) { session?.setScene(scene, looks.favoriteNames) }
    LaunchedEffect(session, shownWalks) { session?.setWalks(shownWalks) }
    val vehicleTrips = remember(shownVehicles) { shownVehicles.mapNotNull { it.vehicle.tripId }.distinct().sorted() }
    val vehiclePaths by produceState(emptyMap<String, RoutePath>(), vehicleTrips, city) {
        value = runCatching { Services.repository.shapes(vehicleTrips, city) }.getOrDefault(emptyMap())
            .filterValues { it.size > 1 }.mapValues { pathOf(it.value) }
    }
    LaunchedEffect(session, vehiclePaths) { session?.setVehiclePaths(vehiclePaths) }
    LaunchedEffect(session, coloredVehicles) {
        session?.setVehicles(coloredVehicles)
        if (coloredVehicles.none { it.id == selectedVehicleId }) selectedVehicleId = null
    }
    LaunchedEffect(session) { session?.onTrackingLost = { controller.trackingLost = true } }
    var hadNav by remember { mutableStateOf(false) }
    LaunchedEffect(session, nav == null) {
        if (nav != null) {
            hadNav = true
            return@LaunchedEffect
        }
        val s = session ?: return@LaunchedEffect
        if (!hadNav) return@LaunchedEffect
        hadNav = false
        s.setTracking(null)
        withFrameNanos {}
        val points = content()
        val fix = currentMe
        when {
            points.isNotEmpty() -> fit(points)
            fix != null -> flyToVisible(LatLon(fix.lat, fix.lon), DEFAULT_ZOOM)
        }
    }
    LaunchedEffect(session, controller.navCamera, me != null) { session?.setTracking(controller.navCamera) }
    LaunchedEffect(session, controller.overviewRequest) {
        val trip = controller.navigation ?: return@LaunchedEffect
        if (session == null || controller.overviewRequest == controller.handledOverview) return@LaunchedEffect
        controller.handledOverview = controller.overviewRequest
        // Release tracking first or it undoes the fit.
        session?.setTracking(null)
        withFrameNanos {}
        fit(trip.shapes.flatMap { it.points } + trip.walks.flatten() + listOfNotNull(trip.destination).map { LatLon(it.lat, it.lon) })
    }
    LaunchedEffect(session, selectedVehicleId) { session?.selectVehicle(selectedVehicleId) }
    val shownMe = if (nav != null) controller.navMe ?: me else me
    LaunchedEffect(session, shownMe) { session?.setMe(shownMe) }
    LaunchedEffect(session, controller.rideAs) { session?.setRideAs(controller.rideAs) }
    LaunchedEffect(session, controller.pinnedLocation, controller.customOrigin, drag, dragAt, nav) {
        val s = session ?: return@LaunchedEffect
        val d = drag
        if (nav != null) {
            s.setPins(listOfNotNull(nav.destination?.let { PinMark(PinKind.Destination, LatLon(it.lat, it.lon), it.label, false) }))
            return@LaunchedEffect
        }
        s.setPins(
            listOfNotNull(
                controller.customOrigin?.let { PinKind.Origin to it },
                controller.pinnedLocation?.let { PinKind.Destination to it },
            ).map { (kind, pin) ->
                val lifted = d != null && d.kind == kind && d.moved
                PinMark(kind, if (lifted) dragAt ?: LatLon(pin.lat, pin.lon) else LatLon(pin.lat, pin.lon), pin.label, lifted)
            },
        )
    }
    LaunchedEffect(session, compassTop, insets.left) {
        val margin = with(density) { 16.dp.toPx() }.roundToInt()
        session?.map?.uiSettings?.setCompassMargins(insets.left.roundToInt() + margin, compassTop.roundToInt(), margin, 0)
    }

    LaunchedEffect(session, controller.shapesVersion) {
        if (session == null || controller.shapesVersion == controller.handledShapes) return@LaunchedEffect
        controller.handledShapes = controller.shapesVersion
        if (!autoFit || !controller.fitNewShapes || controller.shapes.isEmpty() || controller.fitWalking) return@LaunchedEffect
        controller.hasFittedForContent = true
        fit(content())
    }
    LaunchedEffect(session, controller.walkingPolylines, controller.shapesVersion, controller.fitWalking) {
        if (session == null || !controller.fitWalking || !autoFit) return@LaunchedEffect
        if (controller.walkingPolylines.isEmpty() && controller.shapes.isEmpty()) return@LaunchedEffect
        controller.fitWalking = false
        controller.hasFittedForContent = true
        fit(content())
    }
    LaunchedEffect(session, controller.fitRequest) {
        if (session == null || controller.fitRequest == controller.handledFit) return@LaunchedEffect
        controller.handledFit = controller.fitRequest
        if (controller.shapes.isNotEmpty() || controller.walkingPolylines.isNotEmpty()) fit(content())
    }
    LaunchedEffect(session, controller.flyTo) {
        val (target, n) = controller.flyTo ?: return@LaunchedEffect
        if (session == null || n == controller.handledFly) return@LaunchedEffect
        controller.handledFly = n
        flyToVisible(target, DEFAULT_ZOOM)
    }
    LaunchedEffect(session, controller.cameraBack) {
        val (view, n) = controller.cameraBack ?: return@LaunchedEffect
        if (session == null || n == controller.handledCameraBack) return@LaunchedEffect
        controller.handledCameraBack = n
        session?.ease(view)
    }
    LaunchedEffect(session, controller.centerRequest) {
        if (session == null || controller.centerRequest == controller.handledCenter) return@LaunchedEffect
        controller.handledCenter = controller.centerRequest
        val fix = currentMe
        if (fix == null) {
            controller.pendingCenter = true
            onNeedLocation()
        } else {
            flyToVisible(LatLon(fix.lat, fix.lon), DEFAULT_ZOOM)
        }
    }
    LaunchedEffect(session, me != null) {
        val s = session ?: return@LaunchedEffect
        if (me == null) return@LaunchedEffect
        // A fix in a city just switched to waits for the flight there.
        snapshotFlow { s.flying }.first { !it }
        val fix = currentMe ?: return@LaunchedEffect
        if (!controller.firstFixHandled) {
            controller.firstFixHandled = true
            if (autoCenter && !controller.hasFittedForContent) flyToVisible(LatLon(fix.lat, fix.lon), DEFAULT_ZOOM)
        }
        if (controller.pendingCenter) {
            controller.pendingCenter = false
            flyToVisible(LatLon(fix.lat, fix.lon), DEFAULT_ZOOM)
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { host }, modifier = Modifier.fillMaxSize())
        val s = session
        if (s != null) {
            tooltip?.let { tip -> MapTooltip(s, tip) }
            selectedVehicleId?.let { id -> VehicleBubble(s, id) }
        }
    }
}

@SuppressLint("ViewConstructor")
private class MapHost(context: Context, val map: MapView) : FrameLayout(context) {
    var touch: ((MotionEvent) -> Boolean)? = null
    private var ours = false

    init {
        addView(map, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) ours = touch?.invoke(ev) == true
        else if (ours) touch?.invoke(ev)
        if (ours) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) ours = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
private fun Anchored(
    session: MapSession,
    at: () -> LatLon?,
    lift: Float,
    alignStart: Float? = null,
    shift: Offset = Offset.Zero,
    pointer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    Layout({ Box { content() }; Box { pointer() } }) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val body = measurables[0].measure(loose)
        val tip = measurables[1].measure(loose)
        layout(body.width, body.height + tip.height) {
            session.frame.intValue
            val target = at() ?: return@layout
            val p = session.screen(target).apply { offset(shift.x, shift.y) }
            val x = if (alignStart != null) p.x + alignStart
            else (p.x - body.width / 2f).coerceIn(margin, maxOf(margin, constraints.maxWidth - body.width - margin))
            val y = if (alignStart != null) p.y - body.height / 2f else p.y - lift - tip.height - body.height
            body.place(x.roundToInt(), y.roundToInt())
            // The card may be pushed off-centre at the edges; the arrow stays on the target.
            tip.place((p.x - tip.width / 2f).roundToInt(), (y + body.height).roundToInt())
        }
    }
}

@Composable
private fun MapTooltip(session: MapSession, tip: Tooltip) {
    val bg = MaterialTheme.colorScheme.surfaceContainer
    val fg = MaterialTheme.colorScheme.onSurface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val type = MaterialTheme.typography
    val lift = with(LocalDensity.current) { (if (tip is Tooltip.Pin) 30.dp else 18.dp).toPx() }
    val arrow = @Composable {
        Canvas(Modifier.size(12.dp, 6.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, bg)
        }
    }
    Anchored(session, { tip.at }, lift, shift = tip.shift, pointer = arrow) {
        Column(
            Modifier.shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(bg)
                .widthIn(max = 320.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val station = @Composable { name: String, look: StationLook? ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, color = fg, style = type.titleSmall, maxLines = 1)
                    look?.groups?.forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeBadge(group.mode, group.color, 24.dp)
                            LineBadges(group.routes, Modifier.widthIn(max = 224.dp))
                        }
                    }
                }
            }
            when (tip) {
                is Tooltip.StopName -> station(tip.name, tip.look)
                is Tooltip.Group -> if (tip.stations.size <= CLUSTER_STATIONS_MAX) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { tip.stations.forEach { (name, look) -> station(name, look) } }
                } else {
                    val names = tip.stations.map { it.first }.distinct().sortedWith(Collator.getInstance(Locale.getDefault()))
                    Column {
                        names.take(CLUSTER_NAMES_MAX).forEach { Text(it, color = fg, style = type.bodyMedium, maxLines = 1) }
                        if (names.size > CLUSTER_NAMES_MAX) Text(stringResource(R.string.map_more_stations, names.size - CLUSTER_NAMES_MAX), color = sub, style = type.bodySmall)
                    }
                }
                is Tooltip.Pin -> {
                    val parts = tip.label.split(',').map { it.trim() }
                    Text(parts.firstOrNull() ?: tip.label, color = fg, style = type.titleSmall)
                    parts.drop(1).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, color = sub, style = type.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleBubble(session: MapSession, id: String) {
    val shown = session.shownVehicle(id) ?: return
    val vehicle = shown.vehicle
    val offset = with(LocalDensity.current) { 22.dp.toPx() }
    val reported = vehicle.vehicle.speed
    Anchored(session, { session.shownVehicle(id)?.at }, 0f, alignStart = offset) {
        Column(
            Modifier.shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val sub = MaterialTheme.colorScheme.onSurfaceVariant
            val strike = MaterialTheme.colorScheme.error
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(vehicle.shortName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val wheelchair = wheelchairAccess(vehicle.vehicle)
                val bikes = bikeAccess(vehicle.vehicle)
                if (wheelchair != null || bikes != null) {
                    Text("·", color = sub, style = MaterialTheme.typography.bodySmall)
                    wheelchair?.let { ok ->
                        Canvas(Modifier.size(12.dp)) { accessibilityIcon(Offset.Zero, size.width, wheelchair = true, ok = ok, color = sub, strike = strike) }
                    }
                    bikes?.let { ok ->
                        Canvas(Modifier.size(12.dp)) { accessibilityIcon(Offset.Zero, size.width, wheelchair = false, ok = ok, color = sub, strike = strike) }
                    }
                }
            }
            val number = vehicle.vehicle.label.takeIf { it.isNotBlank() }?.let { "#$it · " }.orEmpty()
            Text("$number${Math.round(reported)} km/h", color = sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
