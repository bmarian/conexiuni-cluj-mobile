package online.bus.bmarian.conexiuniclujmobile.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonDefaults
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import online.bus.bmarian.conexiuniclujmobile.ui.rememberCredits
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyProgress
import online.bus.bmarian.conexiuniclujmobile.journey.JourneySync
import online.bus.bmarian.conexiuniclujmobile.journey.StepType
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.findClosestShapeIdx
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.Rail
import online.bus.bmarian.conexiuniclujmobile.ui.SheetSnap
import online.bus.bmarian.conexiuniclujmobile.ui.SheetState
import online.bus.bmarian.conexiuniclujmobile.ui.rememberSheetGestures
import online.bus.bmarian.conexiuniclujmobile.ui.TimelineDot
import online.bus.bmarian.conexiuniclujmobile.ui.TimelineRow
import online.bus.bmarian.conexiuniclujmobile.ui.liveColor
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapColors
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapPin
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.NavCamera
import online.bus.bmarian.conexiuniclujmobile.ui.map.NavScene
import online.bus.bmarian.conexiuniclujmobile.ui.map.RideAs
import online.bus.bmarian.conexiuniclujmobile.journey.NavFollow
import online.bus.bmarian.conexiuniclujmobile.journey.Progress
import online.bus.bmarian.conexiuniclujmobile.journey.RouteLine
import online.bus.bmarian.conexiuniclujmobile.journey.rideRoute
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor

class NavigationViewModel : ViewModel() {
    private val _vehicles = MutableStateFlow<List<MapVehicle>>(emptyList())
    val vehicles: StateFlow<List<MapVehicle>> = _vehicles

    init {
        viewModelScope.launch {
            combine(JourneyManager.journey, JourneyManager.state.map { it?.legIndex ?: 0 }.distinctUntilChanged()) { j, leg -> j to leg }
                .collectLatest { (journey, legIndex) ->
                    _vehicles.value = emptyList()
                    val leg = journey?.legs?.getOrNull(legIndex) ?: return@collectLatest
                    val shapes = runCatching { Services.repository.shapes(leg.tripIds, journey.city) }.getOrDefault(emptyMap())
                    LiveStream.live(emptyList(), leg.tripIds, journey.city).collect { live ->
                        _vehicles.value = withContext(Dispatchers.Default) { busesFor(leg, shapes, live.vehiclesByTrip) }
                    }
                }
        }
    }
}

private fun busesFor(leg: JourneyLeg, shapes: Map<String, List<ShapePoint>>, byTrip: Map<String, List<Vehicle>>): List<MapVehicle> {
    val shown = mutableListOf<MapVehicle>()
    leg.tripIds.forEachIndexed { i, tripId ->
        val pts = shapes[tripId].orEmpty()
        if (pts.isEmpty()) return@forEachIndexed
        val line = leg.lines.getOrNull(i) ?: leg.lines.first()
        val color = leg.colors.getOrNull(i) ?: leg.colors.first()
        val board = findClosestShapeIdx(leg.boarding.lat, leg.boarding.lon, pts)
        val alight = findClosestShapeIdx(leg.alighting.lat, leg.alighting.lon, pts)
        val range = (minOf(board, alight) - 60).coerceAtLeast(0)..maxOf(board, alight) + 1
        byTrip[tripId].orEmpty()
            .filter { findClosestShapeIdx(it.latitude, it.longitude, pts) in range }
            .forEach { shown += MapVehicle("${it.id}", it, line, color, it.heading) }
    }
    return shown.distinctBy { it.id }
}

// What is behind the traveller is not drawn: the step's line starts where they are.
internal fun navScene(journey: Journey, vehicles: List<MapVehicle>, progress: Progress?): NavScene {
    val step = progress?.stepType
    val at = progress?.leg ?: 0
    fun points(line: RouteLine, current: Boolean) = (if (current) line.after(progress!!.along) else line.points).map { LatLon(it.lat, it.lon) }
    val shapes = journey.legs.mapIndexedNotNull { i, leg ->
        val passed = step == StepType.WALK_END || step == StepType.ARRIVED || i < at
        if (passed) return@mapIndexedNotNull null
        MapShape(leg.tripIds.firstOrNull() ?: "leg$i", leg.lines.first(), leg.colors.first(), points(rideRoute(leg), step == StepType.RIDE && i == at))
    }
    val ends = journey.legs.map { it.boarding } + journey.destination
    val walks = journey.walks.filter { it.size >= 2 }.mapNotNull { walk ->
        val to = ends.indices.minBy { haversineMeters(walk.last().lat, walk.last().lon, ends[it].lat, ends[it].lon) }
        val final = to == journey.legs.size
        val passed = when (step) {
            StepType.WALK -> to < at
            StepType.WAIT, StepType.RIDE -> to <= at
            StepType.WALK_END, StepType.ARRIVED -> !final
            else -> false
        }
        val current = (step == StepType.WALK && to == at) || (step == StepType.WALK_END && final)
        if (passed) null else points(RouteLine(walk), current)
    }
    val highlights = LinkedHashMap<Int, StopHighlight>()
    journey.legs.forEachIndexed { i, leg ->
        highlights[leg.boarding.id] = if (i == 0) StopHighlight.Green else StopHighlight.Purple
        highlights[leg.alighting.id] = if (i == journey.legs.lastIndex) StopHighlight.Red else StopHighlight.Amber
    }
    val destination = if (journey.endsAtStop) null else MapPin(journey.destination.lat, journey.destination.lon, journey.destination.name)
    return NavScene(shapes, walks, highlights, vehicles, destination)
}

@Composable
private fun thenText(journey: Journey, sync: JourneySync): String? {
    val leg = journey.legs.getOrNull(sync.legIndex) ?: return null
    val next = journey.legs.getOrNull(sync.legIndex + 1)
    return when (sync.stepType) {
        StepType.WALK -> stringResource(R.string.nav_then_take, leg.lineLabel, leg.alighting.name)
        StepType.WAIT -> stringResource(R.string.nav_then_get_off, leg.alighting.name)
        StepType.RIDE -> when {
            next != null -> stringResource(R.string.nav_then_change, next.lineLabel, next.boarding.name)
            !journey.endsAtStop -> stringResource(R.string.nav_then_walk, journey.destination.name)
            else -> null
        }
        else -> null
    }
}

private fun stepIcon(type: String?): ImageVector = when (type) {
    StepType.WAIT -> Icons.Rounded.HourglassTop
    StepType.RIDE -> Icons.Rounded.DirectionsBus
    StepType.ARRIVED -> Icons.Rounded.Flag
    else -> Icons.AutoMirrored.Rounded.DirectionsWalk
}

@Composable
fun NavigationScreen(onClose: () -> Unit, onInsets: (top: Float, bottom: Float) -> Unit) {
    val context = LocalContext.current
    val map = LocalMap.current
    val view = LocalView.current
    val density = LocalDensity.current
    val state by JourneyManager.state.collectAsStateWithLifecycle()
    val journey by JourneyManager.journey.collectAsStateWithLifecycle()
    val clockMs by Services.clock.collectAsStateWithLifecycle()
    val vm: NavigationViewModel = viewModel()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val voice by Services.settings.journeyVoice.collectAsStateWithLifecycle()
    var bannerBottom by remember { mutableFloatStateOf(0f) }
    var panelHeight by remember { mutableFloatStateOf(0f) }
    var overview by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        Services.fastLocation(true)
        view.keepScreenOn = true
        onDispose {
            Services.fastLocation(false)
            view.keepScreenOn = false
            map.navigation = null
            map.navCamera = null
            map.navMe = null
            map.rideAs = null
            map.trackingLost = false
        }
    }
    val riding = state?.stepType == StepType.RIDE
    // On board, the traveller is the bus: the feed's copy of it trails behind.
    val here by Services.location.collectAsStateWithLifecycle()
    val follow = remember(journey) { journey?.let(::NavFollow) }
    var progress by remember(journey) { mutableStateOf<Progress?>(null) }
    LaunchedEffect(here, state?.stepType, state?.legIndex, follow) {
        val fix = here ?: return@LaunchedEffect
        val (shown, p) = follow?.update(fix, state?.stepType, state?.legIndex ?: 0) ?: return@LaunchedEffect
        map.navMe = shown
        progress = p
    }
    LaunchedEffect(journey, vehicles, riding, progress) { journey?.let { map.navigation = navScene(it, if (riding) emptyList() else vehicles, progress) } }
    LaunchedEffect(journey, riding, state?.legIndex, state?.line, state?.color) {
        val leg = journey?.legs?.getOrNull(state?.legIndex ?: -1)
        map.rideAs = if (riding && leg != null) RideAs(state?.color ?: leg.colors.first()) else null
    }
    LaunchedEffect(bannerBottom, panelHeight) { onInsets(bannerBottom, panelHeight) }
    BackHandler(onBack = onClose)

    val sync = state ?: return
    val trip = journey

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        LaunchedEffect(sync.stepType, overview, epoch, bannerBottom, panelHeight, height) {
            if (overview || bannerBottom <= 0f) {
                map.navCamera = null
                return@LaunchedEffect
            }
            val top = bannerBottom + (height - bannerBottom - panelHeight) * 0.35f
            map.navCamera = when (sync.stepType) {
                StepType.RIDE -> NavCamera(15.3, 50.0, top, panelHeight, epoch)
                StepType.WAIT -> NavCamera(17.0, 0.0, top, panelHeight, epoch)
                else -> NavCamera(17.2, 45.0, top, panelHeight, epoch)
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp)
                .onGloballyPositioned { bannerBottom = it.boundsInRoot().bottom },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
            ) {
                AnimatedContent(sync.step to sync.detail, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { (step, detail) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(stepIcon(sync.stepType), null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            detail?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        sync.line?.let { LineBadge(it, sync.color ?: "#64748b", height = 32.dp, minWidth = 44.dp, corner = 8.dp) }
                    }
                }
            }
            val then = trip?.let { thenText(it, sync) }
            AnimatedVisibility(then != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier.padding(top = 6.dp, start = 12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SubdirectoryArrowRight, null, Modifier.size(18.dp))
                        Text(then.orEmpty(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { Services.settings.setJourneyVoice(!voice) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = with(density) { bannerBottom.toDp() } + 12.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        ) {
            Icon(
                if (voice) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                contentDescription = stringResource(if (voice) R.string.voice_mute else R.string.voice_unmute),
            )
        }

        val panelDp = with(density) { panelHeight.toDp() }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = panelDp + 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(!overview, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                SmallFloatingActionButton(
                    onClick = {
                        overview = true
                        map.overview()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                ) { Icon(Icons.Rounded.Route, contentDescription = stringResource(R.string.nav_overview)) }
            }
            AnimatedVisibility(overview || map.trackingLost, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        overview = false
                        map.trackingLost = false
                        epoch++
                    },
                    icon = { Icon(Icons.Rounded.Navigation, null) },
                    text = { Text(stringResource(R.string.nav_recenter)) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            rememberCredits(),
            Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = panelDp + 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val drawerMax = height * 0.72f
        val navBar = WindowInsets.navigationBars.getBottom(density).toFloat()
        var headerPx by remember { mutableFloatStateOf(0f) }
        val drawer = remember { SheetState(SheetSnap.Peek) }
        val open = drawer.anchored.targetValue == SheetSnap.Full
        val scope = rememberCoroutineScope()
        LaunchedEffect(headerPx, drawerMax, navBar) {
            if (headerPx <= 0f) return@LaunchedEffect
            panelHeight = headerPx + navBar
            drawer.updateAnchors(drawerMax, headerPx + navBar, half = false)
        }
        BackHandler(enabled = open) { scope.launch { drawer.animateTo(SheetSnap.Peek) } }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(with(density) { drawerMax.toDp() })
                .offset { IntOffset(0, (drawer.anchored.offset.takeUnless { it.isNaN() } ?: (drawerMax - headerPx - navBar)).roundToInt()) }
                .then(rememberSheetGestures(drawer)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column {
                Column(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { headerPx = it.height.toFloat() }
                        .clickable(interactionSource = null, indication = null) {
                            scope.launch { drawer.animateTo(if (open) SheetSnap.Peek else SheetSnap.Full) }
                        }
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(36.dp, 4.dp)
                            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledIconButton(
                            onClick = { JourneyManager.stop(context) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.journey_end)) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val eta = sync.etaAt
                            val left = eta?.let { ((it - clockMs) / 60_000.0).let { m -> Math.round(m).toInt().coerceAtLeast(0) } }
                            Text(
                                left?.let { stringResource(R.string.eta_minutes, it) } ?: "—",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = liveColor(),
                            )
                            val clock = eta?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it).atZone(CLUJ_ZONE)) }
                            Text(
                                listOfNotNull(clock?.let { stringResource(R.string.nav_arrive_at, it) }, trip?.destination?.name).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { JourneyManager.next(context) }) { Text(stringResource(R.string.journey_next)) }
                    }
                    if (trip != null) TripProgress(trip, sync.progress ?: 0f)
                }
                if (trip != null) {
                    NavSteps(trip, sync, open = open, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class StepRow(
    val title: String,
    val subtitle: String?,
    val top: Rail?,
    val bottom: Rail?,
    val dot: TimelineDot,
    val color: Color,
    val compact: Boolean = false,
    val lines: List<Pair<String, String>> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavSteps(journey: Journey, sync: JourneySync, open: Boolean, modifier: Modifier) {
    val walkTo = stringResource(R.string.journey_walk_to, "%s")
    val getOn = stringResource(R.string.nav_get_on_at, "%s")
    val changeAt = stringResource(R.string.nav_change_at, "%s")
    val getOff = stringResource(R.string.journey_get_off_at, "%s")
    val start = MaterialTheme.colorScheme.tertiary
    val end = MaterialTheme.colorScheme.primary
    val (rows, current) = remember(journey, sync.stepType, sync.legIndex, sync.stopsLeft, sync.totalStops, walkTo, getOn, getOff, changeAt) {
        val rows = mutableListOf<StepRow>()
        var current = 0
        journey.legs.forEachIndexed { i, leg ->
            val color = routeColor(leg.colors.firstOrNull() ?: "#64748b")
            val walkRow = rows.size
            val previous = journey.legs.getOrNull(i - 1)?.alighting
            val sameStop = previous != null && haversineMeters(previous.lat, previous.lon, leg.boarding.lat, leg.boarding.lon) < 40
            rows += StepRow(
                (if (sameStop) changeAt else walkTo).format(leg.boarding.name), null,
                top = if (i == 0) null else Rail.Walk, bottom = Rail.Walk,
                dot = if (i == 0) TimelineDot.Station else TimelineDot.None,
                color = start,
            )
            val boardRow = rows.size
            leg.stops.forEachIndexed { j, stop ->
                rows += when (j) {
                    0 -> StepRow(
                        getOn.format(stop.name), null, Rail.Walk, Rail.Ride(color), TimelineDot.Station, color,
                        lines = leg.lines.zip(leg.colors),
                    )
                    leg.stops.lastIndex -> StepRow(
                        getOff.format(stop.name), null, Rail.Ride(color),
                        if (i == journey.legs.lastIndex && journey.endsAtStop) null else Rail.Walk,
                        TimelineDot.Station, color,
                    )
                    else -> StepRow(stop.name, null, Rail.Ride(color), Rail.Ride(color), TimelineDot.Stop, color, compact = true)
                }
            }
            if (i == sync.legIndex) {
                current = when (sync.stepType) {
                    StepType.WALK -> walkRow
                    StepType.WAIT -> boardRow
                    StepType.RIDE -> boardRow + ((sync.totalStops ?: 0) - (sync.stopsLeft ?: 0)).coerceIn(0, leg.stops.lastIndex)
                    else -> current
                }
            }
        }
        if (!journey.endsAtStop) {
            val walkEnd = rows.size
            rows += StepRow(walkTo.format(journey.destination.name), null, Rail.Walk, Rail.Walk, TimelineDot.None, end)
            rows += StepRow(journey.destination.name, null, Rail.Walk, null, TimelineDot.Station, end)
            if (sync.stepType == StepType.WALK_END) current = walkEnd
        }
        if (sync.stepType == StepType.ARRIVED) current = rows.lastIndex
        rows to current
    }
    val list = rememberLazyListState()
    LaunchedEffect(open, current) { if (open) list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(rows) { i, row ->
            val here = i == current
            TimelineRow(
                top = row.top,
                bottom = row.bottom,
                dot = row.dot,
                color = row.color,
                compact = row.compact,
                dimmed = i < current,
                modifier = if (here) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier,
            ) {
                Text(
                    row.title,
                    style = if (row.compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = if (here) FontWeight.Bold else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.lines.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.lines.forEach { (line, color) -> LineBadge(line, color, height = 22.dp, minWidth = 30.dp, corner = 6.dp) }
                    }
                }
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun TripProgress(journey: Journey, progress: Float) {
    val parts = remember(journey) { JourneyProgress.parts(journey) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val walk = MapColors.Walk
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val total = parts.sumOf { it.minutes }.coerceAtLeast(1.0)
        val gap = 3.dp.toPx()
        val usable = size.width - gap * (parts.size - 1).coerceAtLeast(0)
        val done = progress * total
        var x = 0f
        var start = 0.0
        val r = CornerRadius(size.height / 2)
        for (part in parts) {
            val w = (part.minutes / total * usable).toFloat()
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            val filled = ((done - start) / part.minutes).coerceIn(0.0, 1.0).toFloat()
            if (filled > 0f) {
                val color = if (part.ride) routeColor(part.color ?: "#64748b") else walk
                drawRoundRect(color, Offset(x, 0f), Size(w * filled, size.height), r)
            }
            x += w + gap
            start += part.minutes
        }
    }
}
