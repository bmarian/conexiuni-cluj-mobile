package online.bus.bmarian.conexiuniclujmobile.ui

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.MAP_CREDITS
import online.bus.bmarian.conexiuniclujmobile.data.RegionSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderLocationDialog
import online.bus.bmarian.conexiuniclujmobile.ui.home.HomeChrome
import online.bus.bmarian.conexiuniclujmobile.ui.home.HomeRequests
import online.bus.bmarian.conexiuniclujmobile.ui.destinations.DestinationScreen
import online.bus.bmarian.conexiuniclujmobile.ui.destinations.DestinationsScreen
import online.bus.bmarian.conexiuniclujmobile.ui.home.HomeScreen
import online.bus.bmarian.conexiuniclujmobile.ui.home.MapFabs
import online.bus.bmarian.conexiuniclujmobile.ui.home.NewsPage
import online.bus.bmarian.conexiuniclujmobile.ui.home.SearchOverlay
import online.bus.bmarian.conexiuniclujmobile.ui.home.SearchState
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapCamera
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapController
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapInsets
import online.bus.bmarian.conexiuniclujmobile.ui.map.TransitMap
import online.bus.bmarian.conexiuniclujmobile.ui.nav.NavigationScreen
import online.bus.bmarian.conexiuniclujmobile.ui.plan.PlanScreen
import online.bus.bmarian.conexiuniclujmobile.ui.route.RouteScreen
import online.bus.bmarian.conexiuniclujmobile.ui.settings.SettingsScreen
import online.bus.bmarian.conexiuniclujmobile.ui.shell.GreenFridayBanner
import online.bus.bmarian.conexiuniclujmobile.ui.shell.OfflinePill
import online.bus.bmarian.conexiuniclujmobile.ui.shell.ToastHost
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.stop.StopScreen
import online.bus.bmarian.conexiuniclujmobile.ui.stop.StopsScreen
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager

val LocalMap = compositionLocalOf<MapController> { error("no map") }
val LocalSheet = compositionLocalOf<SheetState?> { null }

class MapHolder(app: Application) : AndroidViewModel(app) {
    val controller = MapController()
    val camera = MapCamera.restore(app, Cities.current.value)

    init {
        MapLibre.getInstance(app)
        // MapLibre caches tiles itself.
        HttpRequestUtil.setOkHttpClient(Api.client.newBuilder().cache(null).build())
        OfflineManager.getInstance(app).setMaximumAmbientCacheSize(200L * 1024 * 1024, null)
    }
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun rememberCredits(): String {
    val city by Cities.current.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    return cities.firstOrNull { it.slug == city }?.creditsText ?: MAP_CREDITS
}

@Composable
fun AppShell(dark: Boolean, links: MutableStateFlow<Uri?>) {
    val context = LocalContext.current
    val holder: MapHolder = viewModel()
    val map = holder.controller
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val sheet = rememberSheetState(SheetSnap.Half)
    val sidePanel = rememberSideSheetState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val stops by Services.repository.stops.collectAsStateWithLifecycle()
    val me by Services.location.collectAsStateWithLifecycle()
    val online by Services.online.collectAsStateWithLifecycle()
    val autoFit by Services.settings.autoFitMap.collectAsStateWithLifecycle()
    val autoCenter by Services.settings.autoCenterOnMe.collectAsStateWithLifecycle()
    val city by Cities.current.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    val cityInfo = cities.firstOrNull { it.slug == city }
    val credits = cityInfo?.creditsText ?: MAP_CREDITS
    val regions by Services.settings.regions.collectAsStateWithLifecycle()
    val greenFriday = Services.settings.regionSetting(city, RegionSettings.GREEN_FRIDAY, regions)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Services.locationPermissionChanged()
    }

    var stackCity by rememberSaveable { mutableStateOf(city) }
    LaunchedEffect(city) {
        if (city == stackCity) return@LaunchedEffect
        navigator.home()
        sheet.animateTo(SheetSnap.Half)
        navController.visibleEntries.first { it.size == 1 }
        stackCity = city
    }

    suspend fun reveal() {
        if (sheet.anchored.targetValue == SheetSnap.Peek) sheet.animateTo(SheetSnap.Half)
        if (!sidePanel.isOpen) sidePanel.toggle()
    }

    // Undispatched: the sheet must be moving before the map centres.
    fun revealForPick() = scope.launch(start = CoroutineStart.UNDISPATCHED) { reveal() }

    val link by links.collectAsStateWithLifecycle()
    LaunchedEffect(link, stackCity) {
        val uri = link ?: return@LaunchedEffect
        // A city this server doesn't have (any more).
        if (uri.pathSegments.firstOrNull().let { it != null && it !in SECTIONS && !Cities.known(it) }) {
            links.value = null
            return@LaunchedEffect navigator.home()
        }
        val target = linkCity(uri)
        // Opened once the stack has moved to the link's city.
        if (target != null && target != Cities.current.value) return@LaunchedEffect Services.showCity(target)
        if (target != null && target != stackCity) return@LaunchedEffect
        links.value = null
        openLink(navigator, uri)
    }

    val entry by navController.currentBackStackEntryAsState()
    val atRoot = entry != null && navController.previousBackStackEntry == null
    val route = entry?.destination?.route
    val atHome = route == "home"
    LaunchedEffect(atHome) { Services.atHome.value = atHome }

    val search = remember { SearchState() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchRequest by HomeRequests.search.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequest) {
        val text = searchRequest ?: return@LaunchedEffect
        HomeRequests.search.value = null
        navigator.home()
        search.query = text
        searchOpen = true
    }

    var chromeHeight by remember { mutableIntStateOf(0) }

    val navigating by JourneyManager.navigating.collectAsStateWithLifecycle()
    val journeyState by JourneyManager.state.collectAsStateWithLifecycle()
    val showNav = navigating && journeyState != null
    val navShift by animateFloatAsState(if (showNav) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "nav")
    var navTop by remember { mutableFloatStateOf(0f) }
    var navBottom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showNav) { if (showNav) searchOpen = false }

    CompositionLocalProvider(LocalMap provides map, LocalNavigator provides navigator, LocalSheet provides sheet) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight && maxHeight < 600.dp
            val wide = maxWidth >= 840.dp
            val side = landscape || wide
            val panelWidth = if (wide) (maxWidth * 0.3f).coerceAtLeast(360.dp) else minOf(420.dp, maxWidth * 0.58f)
            val panelPx = with(density) { panelWidth.toPx() }
            val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
            val chromePx = if (atHome) chromeHeight.toFloat() else 0f
            val insets = when {
                showNav -> MapInsets(top = navTop, bottom = navBottom)
                side -> MapInsets(top = statusTop + chromePx, left = if (sidePanel.isOpen || wide) panelPx else 0f)
                else -> MapInsets(top = statusTop + chromePx, bottom = minOf(sheet.restingPx, sheet.heightFor(SheetSnap.Half)))
            }

            TransitMap(
                controller = map,
                camera = holder.camera,
                city = stackCity,
                cityBounds = cities.firstOrNull { it.slug == stackCity }?.bounds,
                stops = if (stackCity == city) stops else emptyList(),
                me = me,
                dark = dark,
                insets = insets,
                insetsMoving = { if (side) sidePanel.anchored.moving else sheet.anchored.moving },
                autoFit = autoFit,
                autoCenter = autoCenter,
                onStopTap = {
                    revealForPick()
                    navigator.mapStop(it)
                },
                onStationsTap = {
                    revealForPick()
                    navigator.mapStops(it)
                },
                onNeedLocation = {
                    if (!UserLocation.permitted(context)) permission.launch(LOCATION_PERMISSIONS)
                },
                compassTop = statusTop + chromePx + with(density) { 8.dp.toPx() },
            )

            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxSize()) {
                    if (greenFriday) GreenFridayBanner()
                    Box(Modifier.weight(1f)) { AppNavHost(navController) }
                }
            }
            val chrome: @Composable (Modifier) -> Unit = { modifier ->
                AnimatedVisibility(atHome && !showNav, modifier.statusBarsPadding(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    HomeChrome(
                        query = search.query,
                        onSearch = { searchOpen = true },
                        modifier = Modifier.padding(top = 8.dp).onSizeChanged { chromeHeight = it.height + with(density) { 8.dp.roundToPx() } },
                    )
                }
            }

            if (side) {
                val panelOffset = Modifier.offset { IntOffset(sidePanel.visiblePx.roundToInt(), 0) }
                chrome(Modifier.align(Alignment.TopStart).besidePanel(sidePanel, maxWidth = 520.dp))
                if (!showNav) MapFabs(map, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
                Text(
                    credits,
                    modifier = Modifier.align(Alignment.BottomStart).besidePanel(sidePanel, endReserve = 88.dp).navigationBarsPadding().padding(8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SideSheet(
                    sidePanel,
                    widthPx = panelPx,
                    draggable = !wide,
                    modifier = Modifier.align(Alignment.CenterStart).width(panelWidth).fillMaxHeight()
                        .graphicsLayer { translationX = -navShift * (size.width + panelPx) },
                ) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) { content() }
                }
                if (!wide && !showNav) {
                    val press = remember { MutableInteractionSource() }
                    SmallFloatingActionButton(
                        onClick = { scope.launch { sidePanel.toggle() } },
                        modifier = Modifier.align(Alignment.CenterStart).then(panelOffset).padding(start = 8.dp).squish(press, 0.85f),
                        interactionSource = press,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Icon(
                            if (sidePanel.isOpen) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.w_toggle_drawer),
                        )
                    }
                }
            } else {
                chrome(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().graphicsLayer {
                        val reach = chromeHeight + statusTop
                        alpha = if (reach <= 0f) 1f else ((sheet.offsetPx - reach * 0.25f) / reach).coerceIn(0f, 1f)
                    },
                )
                val fade = with(density) { 120.dp.toPx() }
                val gap = with(density) { 28.dp.roundToPx() }
                if (!showNav) MapFabs(
                    map,
                    Modifier.align(Alignment.TopEnd).padding(end = 16.dp).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            val half = sheet.anchored.anchors.positionOf(SheetSnap.Half).takeUnless { it.isNaN() } ?: sheet.offsetPx
                            val y = statusTop + sheet.offsetPx - placeable.height - gap
                            placeable.placeWithLayer(0, y.roundToInt()) {
                                alpha = ((sheet.offsetPx - (half - fade)) / fade).coerceIn(0f, 1f)
                            }
                        }
                    },
                )
                BottomSheet(
                    state = sheet,
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
                        .graphicsLayer { translationY = navShift * size.height },
                    above = {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                credits,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                            )
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
                }
            }

            AnimatedVisibility(
                showNav,
                enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { -it / 10 },
                exit = fadeOut(tween(150)),
            ) {
                NavigationScreen(
                    onClose = JourneyManager::closeNavigation,
                    onInsets = { top, bottom ->
                        navTop = top
                        navBottom = bottom
                    },
                )
            }
            OfflinePill(
                online,
                Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = with(density) { chromePx.toDp() } + 12.dp),
            )
            SearchOverlay(
                searchOpen,
                search,
                docked = side,
                modifier = if (side) Modifier.align(Alignment.TopStart).besidePanel(sidePanel, maxWidth = 520.dp) else Modifier,
            ) { clear ->
                searchOpen = false
                if (clear) search.query = ""
            }
            ToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
            val onboarded by Services.settings.onboarded.collectAsStateWithLifecycle()
            if (!onboarded) Onboarding(onDone = Services.settings::setOnboarded)
            ReminderLocationDialog()
        }
    }

    LaunchedEffect(route) {
        if (route == null || route == "home") return@LaunchedEffect
        reveal()
    }
    val lower = if (!atRoot || sheet.snap == SheetSnap.Half) null else SheetSnap.Half
    PredictiveBackHandler(enabled = lower != null) { events ->
        val from = sheet.snap
        val to = lower ?: return@PredictiveBackHandler
        val anchors = sheet.anchored.anchors
        try {
            events.collect { e ->
                val goal = androidx.compose.ui.util.lerp(anchors.positionOf(from), anchors.positionOf(to), 0.2f * e.progress)
                sheet.anchored.dispatchRawDelta(goal - sheet.offsetPx)
            }
            sheet.animateTo(to)
        } catch (c: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { sheet.animateTo(from) }
            throw c
        }
    }
}

private fun Modifier.besidePanel(panel: SideSheetState, endReserve: Dp = 0.dp, maxWidth: Dp = Dp.Infinity) = layout { measurable, constraints ->
    val left = panel.visiblePx.roundToInt().coerceAtLeast(0)
    val room = (constraints.maxWidth - left - endReserve.roundToPx()).coerceAtLeast(0)
    val width = if (maxWidth == Dp.Infinity) room else minOf(room, maxWidth.roundToPx())
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(left, 0) }
}

private val PAGE_IN = tween<Float>(210, delayMillis = 90, easing = LinearOutSlowInEasing)
private val PAGE_OUT = tween<Float>(90, easing = FastOutLinearInEasing)
private val PAGE_SLIDE = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val PAGE_SCALE = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

@Composable
private fun AnimatedContentScope.Page(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavHost(navController: NavHostController) {
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController,
        startDestination = "home",
        enterTransition = { fadeIn(PAGE_IN) + slideInHorizontally(PAGE_SLIDE) { it / 4 } + scaleIn(PAGE_SCALE, initialScale = 0.9f) },
        exitTransition = { fadeOut(PAGE_OUT) + slideOutHorizontally(PAGE_SLIDE) { -it / 8 } + scaleOut(PAGE_SCALE, targetScale = 0.95f) },
        popEnterTransition = { fadeIn(PAGE_IN) + slideInHorizontally(PAGE_SLIDE) { -it / 4 } + scaleIn(PAGE_SCALE, initialScale = 0.9f) },
        popExitTransition = { fadeOut(PAGE_OUT) + slideOutHorizontally(PAGE_SLIDE) { it / 8 } + scaleOut(PAGE_SCALE, targetScale = 0.95f) },
    ) {
        composable("home") { Page { HomeScreen() } }
        composable("news") { Page { NewsPage() } }
        composable("destinations") { Page { DestinationsScreen() } }
        composable("to/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
            Page { DestinationScreen(entry.arguments?.getString("slug").orEmpty()) }
        }
        composable("stop/{stopId}", arguments = listOf(navArgument("stopId") { type = NavType.IntType })) { entry ->
            Page { StopScreen(entry.arguments?.getInt("stopId") ?: 0) }
        }
        composable("stops/{stopIds}", arguments = listOf(navArgument("stopIds") { type = NavType.StringType })) { entry ->
            Page { StopsScreen(entry.arguments?.getString("stopIds").orEmpty().split(',').mapNotNull(String::toIntOrNull)) }
        }
        composable(
            "route/{routeId}/{direction}?from={from}&change={change}",
            arguments = listOf(
                navArgument("routeId") { type = NavType.IntType },
                navArgument("direction") { type = NavType.StringType },
                navArgument("from") { type = NavType.IntType; defaultValue = -1 },
                navArgument("change") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val args = entry.arguments
            Page {
                RouteScreen(
                    routeId = args?.getInt("routeId") ?: 0,
                    direction = args?.getString("direction") ?: "auto",
                    fromStopId = args?.getInt("from")?.takeIf { it >= 0 },
                    changeId = args?.getLong("change")?.takeIf { it > 0 },
                )
            }
        }
        composable(
            "plan?q={q}",
            arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            Page { PlanScreen(PlanQuery.decode(entry.arguments?.getString("q").orEmpty())) }
        }
        composable(
            "settings?pair={pair}&k={k}",
            arguments = listOf(
                navArgument("pair") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("k") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            Page { SettingsScreen(pairCode = entry.arguments?.getString("pair"), pairKey = entry.arguments?.getString("k")) }
        }
    }
    }
    }
}

private class AppNavigator(private val nav: NavHostController) : Navigator {
    override val canGoBack: Boolean get() = nav.previousBackStackEntry != null

    override fun back() {
        nav.popBackStack()
    }

    override fun home() {
        nav.navigate("home") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }

    override fun stop(stopId: Int) {
        if (showing(stopId)) return
        nav.navigate("stop/$stopId")
    }

    override fun mapStop(stopId: Int) {
        if (!showing(stopId)) fromMap("stop/$stopId")
    }

    override fun mapStops(stopIds: List<Int>) = fromMap("stops/${stopIds.joinToString(",")}")

    private fun showing(stopId: Int) =
        nav.currentBackStackEntry?.destination?.route == "stop/{stopId}" && nav.currentBackStackEntry?.arguments?.getInt("stopId") == stopId

    private fun fromMap(target: String) {
        val current = nav.currentBackStackEntry?.destination
        nav.navigate(target) {
            if (current != null && current.route in MAP_PICKS) popUpTo(current.id) { inclusive = true }
        }
    }

    override fun route(routeId: Int, direction: String, fromStopId: Int?, change: Long?) {
        go("route/$routeId/$direction?from=${fromStopId ?: -1}&change=${change ?: -1}")
    }

    override fun plan(query: PlanQuery) {
        go("plan?q=${Uri.encode(query.encode())}")
    }

    override fun settings() {
        go("settings")
    }

    override fun news() {
        go("news")
    }

    override fun destinations() {
        go("destinations")
    }

    override fun destination(slug: String) {
        go("to/${Uri.encode(slug)}")
    }

    private var lastTarget: String? = null
    private var lastAt = 0L

    private fun go(target: String) {
        val now = SystemClock.uptimeMillis()
        if (target == lastTarget && now - lastAt < 700) return
        lastTarget = target
        lastAt = now
        nav.navigate(target)
    }

    fun settingsPair(code: String, key: String?) {
        nav.navigate("settings?pair=$code" + (key?.let { "&k=${Uri.encode(it)}" } ?: ""))
    }
}

private val MAP_PICKS = setOf("stop/{stopId}", "stops/{stopIds}")

// Pages a link can open; a first segment that is none of these is a city.
private val SECTIONS = setOf("stop", "route", "plan", "journey", "search", "settings", "nav")

// Links from before cities are Cluj's.
private val CITY_SECTIONS = setOf("stop", "route", "plan")

/** The city a link belongs to; null when it fits any. */
private fun linkCity(uri: Uri): String? {
    val first = uri.pathSegments.firstOrNull() ?: return null
    if (first !in SECTIONS) return first.takeIf(Cities::known)
    return Cities.DEFAULT.takeIf { first in CITY_SECTIONS }
}

private fun openLink(navigator: Navigator, uri: Uri) {
    val segments = uri.pathSegments.let { if (it.firstOrNull() in SECTIONS) it else it.drop(1) }
    when (segments.firstOrNull()) {
        "stop" -> segments.getOrNull(1)?.toIntOrNull()?.let(navigator::stop)
        "route" -> {
            val routeId = segments.getOrNull(1)?.toIntOrNull() ?: return
            val direction = segments.getOrNull(2)?.takeIf { it == "0" || it == "1" || it == "auto" } ?: "auto"
            navigator.route(routeId, direction, change = uri.getQueryParameter("change")?.toLongOrNull())
        }
        "plan" -> navigator.plan(PlanQuery.fromUri(uri))
        "to" -> segments.getOrNull(1)?.let(navigator::destination) ?: navigator.destinations()
        "destinations" -> navigator.destinations()
        "journey", "nav" -> JourneyManager.openNavigation()
        "search" -> {
            navigator.home()
            HomeRequests.search.value = uri.getQueryParameter("q").orEmpty()
        }
        "settings" -> {
            val code = uri.getQueryParameter("pair")
            // The key is in the fragment, never sent to the server.
            val key = uri.fragment?.split('&')?.firstOrNull { it.startsWith("k=") }?.removePrefix("k=")
            if (code != null && navigator is AppNavigator) navigator.settingsPair(code, key) else navigator.settings()
        }
        else -> navigator.home()
    }
}
