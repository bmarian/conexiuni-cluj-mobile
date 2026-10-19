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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import online.bus.bmarian.conexiuniclujmobile.data.Bounds
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.roundToInt

private const val TOOLTIP_MS = 2_500L

/** In px. */
data class MapInsets(val top: Float = 0f, val bottom: Float = 0f, val right: Float = 0f, val left: Float = 0f)

private sealed interface Tooltip {
    val at: LatLon

    class StopName(override val at: LatLon, val name: String) : Tooltip
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
    autoFit: Boolean,
    autoCenter: Boolean,
    showVehicleExtras: Boolean,
    onStopTap: (Int) -> Unit,
    onNeedLocation: () -> Unit,
    modifier: Modifier = Modifier,
    compassTop: Float = 0f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentInsets by rememberUpdatedState(insets)
    val currentOnStopTap by rememberUpdatedState(onStopTap)
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
            map.addOnMapClickListener { latLng ->
                val point = map.projection.toScreenLocation(latLng)
                val vehicle = created.vehicleAt(point, with(density) { 18.dp.toPx() })
                if (vehicle != null) {
                    selectedVehicleId = if (selectedVehicleId == vehicle) null else vehicle
                    return@addOnMapClickListener true
                }
                val stop = created.stopAt(point, with(density) { 16.dp.toPx() })?.let { currentStopsById[it] }
                if (stop != null) {
                    tooltip = null
                    currentOnStopTap(stop.stopId)
                    return@addOnMapClickListener true
                }
                selectedVehicleId = null
                tooltip = null
                false
            }
            map.addOnMapLongClickListener { latLng ->
                val point = map.projection.toScreenLocation(latLng)
                val stop = created.stopAt(point, with(density) { 20.dp.toPx() })?.let { currentStopsById[it] }
                    ?: return@addOnMapLongClickListener false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                tooltip = Tooltip.StopName(LatLon(stop.lat, stop.lon), stop.name)
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

    fun padding(extra: Float): MapPadding {
        val i = currentInsets
        return MapPadding(i.left + extra, i.top + extra, i.right + extra, i.bottom + extra)
    }

    val fitPad = with(density) { 24.dp.toPx() }
    val buttonsPad = with(density) { 56.dp.toPx() }
    fun fit(points: List<LatLon>) = session?.fit(points, padding(fitPad).let { it.copy(right = it.right + buttonsPad) })

    fun content(): List<LatLon> =
        controller.shapes.flatMap { it.points } + controller.walkingPolylines.flatten() +
            listOfNotNull(controller.pinnedLocation, controller.customOrigin).map { LatLon(it.lat, it.lon) }
    fun flyToVisible(target: LatLon, zoom: Double) = session?.flyTo(target, zoom, padding(0f))

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
    val scene = remember(controller.shapes, controller.directionArrowAtStart, nav) {
        if (nav != null) buildScene(nav.shapes, terminals = false, arrows = false)
        else buildScene(controller.shapes, terminals = controller.highlightedStops.isEmpty(), arrows = controller.directionArrowAtStart)
    }
    LaunchedEffect(session, scene) { session?.setScene(scene) }
    LaunchedEffect(session, shownWalks) { session?.setWalks(shownWalks) }
    LaunchedEffect(session, shownVehicles) {
        session?.setVehicles(shownVehicles)
        if (shownVehicles.none { it.id == selectedVehicleId }) selectedVehicleId = null
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
        val fix = me ?: return@LaunchedEffect
        if (session == null) return@LaunchedEffect
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
            tooltip?.let { tip -> MapTooltip(s, tip, dark) }
            selectedVehicleId?.let { id -> VehicleBubble(s, id, dark, showVehicleExtras) }
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
private fun Anchored(session: MapSession, at: () -> LatLon?, lift: Float, alignStart: Float? = null, content: @Composable () -> Unit) {
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            layout(placeable.width, placeable.height) {
                session.frame.intValue
                val target = at() ?: return@layout
                val p = session.screen(target)
                val x = if (alignStart != null) p.x + alignStart
                else (p.x - placeable.width / 2f).coerceIn(margin, maxOf(margin, constraints.maxWidth - placeable.width - margin))
                val y = if (alignStart != null) p.y - placeable.height / 2f else p.y - lift - placeable.height
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        },
    ) { content() }
}

@Composable
private fun MapTooltip(session: MapSession, tip: Tooltip, dark: Boolean) {
    val bg = if (dark) Color(0xFF1E293B) else Color.White
    val fg = if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val sub = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val lift = with(LocalDensity.current) { (if (tip is Tooltip.Pin) 30.dp else 18.dp).toPx() }
    Anchored(session, { tip.at }, lift) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(bg)
                    .widthIn(max = 320.dp).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                when (tip) {
                    is Tooltip.StopName -> Text(tip.name, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    is Tooltip.Pin -> {
                        val parts = tip.label.split(',').map { it.trim() }
                        Text(parts.firstOrNull() ?: tip.label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        parts.drop(1).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, color = sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Canvas(Modifier.size(12.dp, 6.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, bg)
            }
        }
    }
}

@Composable
private fun VehicleBubble(session: MapSession, id: String, dark: Boolean, extras: Boolean) {
    val shown = session.shownVehicle(id) ?: return
    val vehicle = shown.vehicle
    val offset = with(LocalDensity.current) { 22.dp.toPx() }
    val reported = vehicle.vehicle.speed
    Anchored(session, { session.shownVehicle(id)?.at }, 0f, alignStart = offset) {
        Column(
            Modifier.shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                .background(if (dark) Color(0xE61D293D) else Color(0xE60F172B))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(vehicle.shortName, color = Color(0xFFF1F5F9), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (extras) {
                    val gray = Color(0xFF94A3B8)
                    Text("·", color = gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Canvas(Modifier.size(12.dp)) {
                        accessibilityIcon(Offset.Zero, size.width, wheelchair = true, ok = vehicle.vehicle.wheelchairAccessible == "WHEELCHAIR_ACCESSIBLE", color = gray)
                    }
                    Canvas(Modifier.size(12.dp)) {
                        accessibilityIcon(Offset.Zero, size.width, wheelchair = false, ok = vehicle.vehicle.bikeAccessible == "BIKE_ACCESSIBLE", color = gray)
                    }
                }
            }
            val number = vehicle.vehicle.label.takeIf { it.isNotBlank() }?.let { "#$it · " }.orEmpty()
            Text("$number${Math.round(reported)} km/h", color = Color(0xFF90A1B9), fontSize = 12.sp)
        }
    }
}
