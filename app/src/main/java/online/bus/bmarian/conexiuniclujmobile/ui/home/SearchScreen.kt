package online.bus.bmarian.conexiuniclujmobile.ui.home

import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.logic.sortedRoutes
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.stop.RouteRow
import java.util.Locale

fun planQueryFor(plan: FavoritePlan): PlanQuery {
    val withOrigin = plan.originLat != null && plan.originLon != null && !plan.originName.isNullOrEmpty()
    return PlanQuery.to(
        plan.lat, plan.lon, plan.name,
        if (withOrigin) plan.originLat else null,
        if (withOrigin) plan.originLon else null,
        if (withOrigin) plan.originName else null,
    )
}

private const val DOCKED_HEIGHT_RATIO = 2f / 3f
private val DOCKED_MIN_HEIGHT = 240.dp

@Composable
fun SearchOverlay(visible: Boolean, state: SearchState, docked: Boolean, modifier: Modifier = Modifier, onClose: (clear: Boolean) -> Unit) {
    if (docked && visible) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose(false) } })
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(140)) + slideInVertically(tween(190)) { -it / 16 },
        exit = fadeOut(tween(105)) + slideOutVertically(tween(140)) { -it / 16 },
    ) {
        SearchContent(state, docked, onClose)
    }
}

@Composable
private fun SearchSurface(docked: Boolean, content: @Composable () -> Unit) {
    if (!docked) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, content = content)
        return
    }
    BoxWithConstraints(Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = maxOf(DOCKED_MIN_HEIGHT, maxHeight * DOCKED_HEIGHT_RATIO)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            content = content,
        )
    }
}

@Composable
private fun SearchContent(state: SearchState, docked: Boolean, onClose: (clear: Boolean) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val nav = LocalNavigator.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val routes by Services.repository.routes.collectAsStateWithLifecycle()
    val stops by Services.repository.stops.collectAsStateWithLifecycle()
    val online by Services.online.collectAsStateWithLifecycle()
    val me by Services.location.collectAsStateWithLifecycle()
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    val recentPlans by Services.favorites.recentPlans.collectAsStateWithLifecycle()
    val favoritePlans by Services.favorites.plans.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.query, online) { state.searchPlaces(state.query, online, me) }
    BackHandler { onClose(true) }

    val prefs = remember { context.getSharedPreferences("home", android.content.Context.MODE_PRIVATE) }
    var showAllRoutes by remember { mutableStateOf(prefs.getBoolean("showAllRoutes", false)) }
    var showAllStops by remember { mutableStateOf(prefs.getBoolean("showAllStops", false)) }
    val sortedAll = remember(routes) { sortedRoutes(routes) }
    val collator = remember(locale) {
        (Collator.getInstance(Locale.forLanguageTag(locale)) as RuleBasedCollator).apply { numericCollation = true }
    }
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val sortedStops = remember(stops, collator, index) {
        stops.sortedWith { a, b -> collator.compare(a.name, b.name) }.let { sorted -> index?.oneStopPerStation(sorted) ?: sorted }
    }
    val recents = remember(recentPlans, favoritePlans) {
        recentPlans.filterNot { p -> favoritePlans.any { it.samePlace(p) } }.take(8)
    }

    fun go(action: () -> Unit) {
        focusManager.clearFocus()
        onClose(false)
        action()
    }
    fun openRoute(route: Route) = go { nav.route(route.routeId, "auto") }

    SearchSurface(docked) {
        Column(if (docked) Modifier.fillMaxWidth() else Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onClose(true) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.w_back))
                }
                TextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.w_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { IconButton(onClick = { state.query = "" }) { Icon(Icons.Rounded.Close, contentDescription = null) } }
                    } else null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(if (docked) Modifier.weight(1f, fill = false) else Modifier.weight(1f).navigationBarsPadding()) {
                if (state.active) {
                    searchResults(
                        state = state,
                        routes = sortedAll,
                        stops = stops,
                        me = me,
                        online = online,
                        navigatingRouteId = null,
                        onPlace = { place ->
                            go {
                                val lat = place.latitude
                                val lon = place.longitude
                                if (lat != null && lon != null) Services.favorites.addRecentPlan(FavoritePlan(place.label, lat = lat, lon = lon))
                                nav.plan(PlanQuery(lat = place.lat, lon = place.lon, name = place.label))
                            }
                        },
                        onRoute = ::openRoute,
                        onStop = { stop -> go { nav.stop(stop.stopId) } },
                        onDropPin = {
                            go { nav.plan(PlanQuery(lat = "46.7712", lon = "23.6236", name = resources.getString(R.string.w_dropped_pin))) }
                        },
                    )
                    item { Spacer(Modifier.height(24.dp)) }
                    return@LazyColumn
                }

                if (online && recents.isNotEmpty()) {
                    item { SubLabel(stringResource(R.string.w_recent_destinations), Modifier.padding(top = 8.dp)) }
                    items(recents, key = { "recent-${it.lat}-${it.lon}-${it.originLat}" }) { plan ->
                        ResultRow(
                            icon = { RowIcon(Icons.Rounded.Schedule, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant) },
                            title = plan.name,
                            distance = null,
                            subtitle = plan.originName?.let { "${stringResource(R.string.w_plan_from)}: $it" },
                            onClick = { go { nav.plan(planQueryFor(plan)) } },
                        )
                    }
                }
                if (online && favoritePlans.isNotEmpty()) {
                    item { SubLabel(stringResource(R.string.w_favorite_plans), Modifier.padding(top = 8.dp)) }
                    items(favoritePlans, key = { "saved-${it.lat}-${it.lon}-${it.originLat}" }) { plan ->
                        ResultRow(
                            icon = { RowIcon(Icons.Rounded.Place, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                            title = plan.label ?: plan.name,
                            distance = null,
                            onClick = { go { nav.plan(planQueryFor(plan)) } },
                        )
                    }
                }

                item {
                    CollapseRow(
                        icon = { RowIcon(Icons.Rounded.Map, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, size = 32.dp) },
                        title = stringResource(R.string.w_all_routes),
                        count = sortedAll.size,
                        open = showAllRoutes,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        showAllRoutes = !showAllRoutes
                        prefs.edit { putBoolean("showAllRoutes", showAllRoutes) }
                    }
                }
                if (showAllRoutes) {
                    if (routes.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                    items(sortedAll, key = { "all-r-${it.routeId}" }, contentType = { "route" }) { route ->
                        RouteRow(route.shortName, route.color, route.longName) { openRoute(route) }
                    }
                }
                item {
                    CollapseRow(
                        icon = { RowIcon(Icons.Rounded.DirectionsBus, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 32.dp) },
                        title = stringResource(R.string.w_all_stops),
                        count = sortedStops.size,
                        open = showAllStops,
                    ) {
                        showAllStops = !showAllStops
                        prefs.edit { putBoolean("showAllStops", showAllStops) }
                    }
                }
                if (showAllStops) {
                    if (stops.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                    items(sortedStops, key = { "all-s-${it.stopId}" }, contentType = { "stop" }) { stop ->
                        StationRow(stop, index?.look(stop.stopId), distance = null, iconSize = 32.dp) { go { nav.stop(stop.stopId) } }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CollapseRow(
    icon: @Composable () -> Unit,
    title: String,
    count: Int,
    open: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Row(
        modifier.fillMaxWidth().squishClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (count > 0) {
            Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (count == 0 && open) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp).rotate(rotation), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
