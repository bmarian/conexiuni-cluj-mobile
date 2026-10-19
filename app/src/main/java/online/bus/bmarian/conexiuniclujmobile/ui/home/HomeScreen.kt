package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.Box
import online.bus.bmarian.conexiuniclujmobile.ui.StationIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationLines
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.follow.FollowManager
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.follow.FollowingCard
import online.bus.bmarian.conexiuniclujmobile.ui.follow.JourneyCard
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderSheet
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderRepeat
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderWhen
import online.bus.bmarian.conexiuniclujmobile.ui.longPressDrag
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.moved
import online.bus.bmarian.conexiuniclujmobile.ui.rememberReorderState
import online.bus.bmarian.conexiuniclujmobile.ui.reorderItem
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable

/** One chip per line: both directions saved show as one and open the direction nearest the user. */
private class RouteChip(val route: Route, val label: String, val favorites: List<FavoriteRoute>) {
    val direction get() = favorites.singleOrNull()?.direction ?: "auto"
}

private fun planKey(p: FavoritePlan) = "${p.lat}_${p.lon}_${p.originLat ?: ""}_${p.originLon ?: ""}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val repository = Services.repository
    val routes by repository.routes.collectAsStateWithLifecycle()
    val stops by repository.stops.collectAsStateWithLifecycle()
    val favoriteRoutes by Services.favorites.routes.collectAsStateWithLifecycle()
    val favoriteStopIds by Services.favorites.stops.collectAsStateWithLifecycle()
    val favoritePlans by Services.favorites.plans.collectAsStateWithLifecycle()
    val recentPlans by Services.favorites.recentPlans.collectAsStateWithLifecycle()
    val online by Services.online.collectAsStateWithLifecycle()
    // Another city's keep running, out of sight here.
    val city by Cities.current.collectAsStateWithLifecycle()
    val follows = FollowManager.follows.collectAsStateWithLifecycle().value.filter { it.followed.city == city }
    val reminders = Reminders.all.collectAsStateWithLifecycle().value.filter { it.followed.city == city }
    val clock by Services.clock.collectAsStateWithLifecycle()
    var reminding by remember { mutableStateOf<Followed?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val journey by JourneyManager.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        onDispose { map.release(owner) }
    }
    LaunchedEffect(favoriteStopIds) { map.highlightedStops = favoriteStopIds.associateWith { StopHighlight.Favorite } }
    val index by Services.repository.network.index.collectAsStateWithLifecycle()

    val routesById = remember(routes) { routes.associateBy { it.routeId } }
    val stopsById = remember(stops) { stops.associateBy { it.stopId } }
    val chips = remember(routesById, favoriteRoutes) {
        favoriteRoutes.groupBy { it.routeId }.mapNotNull { (routeId, favorites) ->
            val route = routesById[routeId] ?: return@mapNotNull null
            val longName = route.longName
            val i = longName.lastIndexOf(" - ")
            val origin = if (i >= 0) longName.substring(0, i) else ""
            val end = if (i >= 0) longName.substring(i + 3) else longName
            val label = when {
                origin.isEmpty() -> if (favorites.size > 1) longName else "→ $end"
                favorites.size > 1 -> "$origin ⇄ $end"
                favorites[0].direction == "1" -> "→ $origin"
                else -> "→ $end"
            }
            RouteChip(route, label, favorites)
        }
    }
    val favoriteStops = remember(stopsById, favoriteStopIds) { favoriteStopIds.mapNotNull { stopsById[it] } }
    val hasFavorites = favoriteRoutes.isNotEmpty() || favoriteStopIds.isNotEmpty() || (online && favoritePlans.isNotEmpty())
    val recents = remember(recentPlans, favoritePlans) {
        recentPlans.filterNot { p -> favoritePlans.any { it.samePlace(p) } }.take(5)
    }

    fun openRoute(route: Route, direction: String) = nav.route(route.routeId, direction)

    fun openPlan(plan: FavoritePlan) {
        if (online) nav.plan(planQueryFor(plan))
    }

    val routeReorder = rememberReorderState(chips.map { it.route.routeId }) { from, to ->
        Services.favorites.reorderRoutes(chips.moved(from, to).flatMap { it.favorites })
    }
    val stopReorder = rememberReorderState(favoriteStopIds) { from, to -> Services.favorites.reorderStops(favoriteStopIds.moved(from, to)) }
    val planReorder = rememberReorderState(favoritePlans.map(::planKey)) { from, to -> Services.favorites.reorderPlans(favoritePlans.moved(from, to)) }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        journey?.let { trip ->
            item(key = "journey") {
                JourneyCard(
                    trip,
                    onNext = { JourneyManager.next(context) },
                    onEnd = { JourneyManager.stop(context) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateItem(),
                    onOpen = JourneyManager::openNavigation,
                )
            }
        }
        items(follows, key = { "following-${it.id}" }) { follow ->
            FollowingCard(
                follow,
                onOpen = { nav.stop(follow.followed.stopId) },
                onStop = { FollowManager.stop(context, follow.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateItem(),
            )
        }

        if (reminders.isNotEmpty()) {
            item(key = "reminders-label") { SubLabel(stringResource(R.string.reminders), Modifier.padding(top = 12.dp)) }
            item(key = "reminders") {
                Column {
                    for (reminder in reminders) key(reminder.id) {
                        val followed = reminder.followed
                        val journey = reminder.journey
                        val next = remember(reminder, clock) { reminder.next(clock) }
                        FavoriteRow(
                            modifier = Modifier.animateContentSize(),
                            icon = { LineBadge(followed.shortName, followed.color, height = 30.dp, minWidth = 38.dp, corner = 8.dp) },
                            title = listOfNotNull(
                                next?.let { reminderWhen(resources, it, locale) } ?: stringResource(R.string.remind_no_bus),
                                "→ ${followed.destination}",
                            ).joinToString(" "),
                            kicker = listOfNotNull(
                                journey?.legs?.joinToString(" › ") { it.lineLabel },
                                stringResource(R.string.remind_last_bus).takeIf { reminder.time == null },
                                reminderRepeat(resources, reminder.days, locale),
                                followed.stopName,
                            ).joinToString(" · "),
                            removeLabel = stringResource(R.string.remind_delete),
                            onClick = {
                                val path = journey?.planPath
                                if (path != null) nav.plan(PlanQuery.decode(path.removePrefix("/plan?"))) else reminding = followed
                            },
                            onRemove = { Reminders.remove(context, reminder.id) },
                        )
                    }
                }
            }
        }
        if (chips.isNotEmpty()) {
            item(key = "routes-label") { SubLabel(stringResource(R.string.w_favorite_routes), Modifier.padding(top = 12.dp)) }
            item(key = "routes") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    for (chip in chips) key(chip.route.routeId) {
                        val id = chip.route.routeId
                        FavoriteRouteChip(
                            chip = chip,
                            modifier = Modifier.weight(1f).reorderItem(routeReorder, id).longPressDrag(routeReorder, id),
                            onClick = { openRoute(chip.route, chip.direction) },
                            onRemove = { chip.favorites.forEach { Services.favorites.toggleRoute(it.routeId, it.direction) } },
                        )
                    }
                    if (chips.size % 2 == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (favoriteStops.isNotEmpty()) {
            item(key = "stops-label") { SubLabel(stringResource(R.string.w_favorite_stops), Modifier.padding(top = if (chips.isEmpty()) 12.dp else 8.dp)) }
            item(key = "stops") {
                Column {
                    for (stop in favoriteStops) key(stop.stopId) {
                        val look = index?.look(stop.stopId)
                        FavoriteRow(
                            modifier = Modifier.reorderItem(stopReorder, stop.stopId).longPressDrag(stopReorder, stop.stopId),
                            icon = { StationIcon(look, size = 30.dp) },
                            title = stop.name,
                            lines = { StationLines(look) },
                            kicker = null,
                            removeLabel = stringResource(R.string.w_remove_from_favorites),
                            onClick = { nav.stop(stop.stopId) },
                            onRemove = { Services.favorites.toggleStop(stop.stopId) },
                        )
                    }
                }
            }
        }
        if (online && favoritePlans.isNotEmpty()) {
            item(key = "plans-label") { SubLabel(stringResource(R.string.w_favorite_plans), Modifier.padding(top = if (chips.isEmpty() && favoriteStops.isEmpty()) 12.dp else 8.dp)) }
            item(key = "plans") {
                Column {
                    for (plan in favoritePlans) key(planKey(plan)) {
                        val key = planKey(plan)
                        FavoriteRow(
                            modifier = Modifier.reorderItem(planReorder, key).longPressDrag(planReorder, key),
                            icon = { RowIcon(Icons.Rounded.Map, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 30.dp) },
                            title = plan.label ?: plan.name,
                            titlePrefix = if (plan.label == null && plan.originName != null) stringResource(R.string.w_plan_to) + ": " else null,
                            kicker = if (plan.label == null) plan.originName?.let { "${stringResource(R.string.w_plan_from)}: $it" } else null,
                            removeLabel = stringResource(R.string.w_remove_from_favorites),
                            onClick = { openPlan(plan) },
                            onRemove = { Services.favorites.togglePlan(plan) },
                        )
                    }
                }
            }
        }
        if (online && recents.isNotEmpty()) {
            item(key = "recents-label") { SubLabel(stringResource(R.string.w_recent_destinations), Modifier.padding(top = if (hasFavorites) 8.dp else 12.dp)) }
            items(recents, key = { "recent-${planKey(it)}" }) { plan ->
                FavoriteRow(
                    modifier = Modifier.animateItem(),
                    icon = { RowIcon(Icons.Rounded.Schedule, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, size = 30.dp) },
                    title = plan.name,
                    titlePrefix = if (plan.originName != null) stringResource(R.string.w_plan_to) + ": " else null,
                    kicker = plan.originName?.let { "${stringResource(R.string.w_plan_from)}: $it" },
                    removeLabel = stringResource(R.string.w_remove_from_recents),
                    onClick = { openPlan(plan) },
                    onRemove = { Services.favorites.removeRecentPlan(plan) },
                )
            }
        }
        if (!hasFavorites && !(online && recents.isNotEmpty())) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.w_no_favorites),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
    reminding?.let { ReminderSheet(it) { reminding = null } }
}

@Composable
private fun FavoriteRouteChip(
    chip: RouteChip,
    modifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.squish(press, 0.93f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = press,
    ) {
        Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LineBadge(chip.route.shortName, chip.route.color, height = 28.dp, minWidth = 36.dp, corner = 8.dp)
            Text(
                chip.label,
                Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_remove_from_favorites), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    kicker: String?,
    removeLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    titlePrefix: String? = null,
    lines: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).squishClickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            kicker?.let {
                Text(
                    it.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                buildString {
                    titlePrefix?.let(::append)
                    append(title)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lines?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = removeLabel, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
