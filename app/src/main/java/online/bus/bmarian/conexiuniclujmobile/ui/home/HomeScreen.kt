package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.Box
import online.bus.bmarian.conexiuniclujmobile.logic.HomeSections
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import online.bus.bmarian.conexiuniclujmobile.logic.groupRoutes
import online.bus.bmarian.conexiuniclujmobile.logic.writeLegs
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
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadges
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.follow.FollowingCard
import online.bus.bmarian.conexiuniclujmobile.ui.follow.JourneyCard
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderSheet
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderRepeat
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderWhen
import online.bus.bmarian.conexiuniclujmobile.ui.longPressDrag
import online.bus.bmarian.conexiuniclujmobile.ui.moved
import online.bus.bmarian.conexiuniclujmobile.ui.rememberReorderState
import online.bus.bmarian.conexiuniclujmobile.ui.reorderItem
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.Removal
import online.bus.bmarian.conexiuniclujmobile.logic.placeIcon
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.foundation.layout.heightIn
import online.bus.bmarian.conexiuniclujmobile.data.SavedGroup
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.FirstSteps
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** One chip per line: both directions saved show as one and open the direction nearest the user. */
private class RouteChip(val route: Route, val parts: List<String>, val both: Boolean, val favorites: List<FavoriteRoute>) {
    val direction get() = favorites.singleOrNull()?.direction ?: "auto"
}

private fun planKey(p: FavoritePlan) = "${p.lat}_${p.lon}_${p.originLat ?: ""}_${p.originLon ?: ""}"

private const val RECENTS_SHOWN = 5

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
    val recentList by Services.favorites.recents.collectAsStateWithLifecycle()
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
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val lines = remember(index) { linesByMode(index) }

    val routesById = remember(routes) { routes.associateBy { it.routeId } }
    val stopsById = remember(stops) { stops.associateBy { it.stopId } }
    val chips = remember(routesById, favoriteRoutes) {
        favoriteRoutes.groupBy { it.routeId }.mapNotNull { (routeId, favorites) ->
            val route = routesById[routeId] ?: return@mapNotNull null
            val longName = route.longName
            val i = longName.lastIndexOf(" - ")
            val origin = if (i >= 0) longName.substring(0, i) else ""
            val end = if (i >= 0) longName.substring(i + 3) else longName
            val both = favorites.size > 1
            val parts = when {
                origin.isEmpty() -> listOf(if (both) longName else "→\u00a0$end")
                both -> listOf(origin, end)
                favorites[0].direction == "1" -> listOf("→\u00a0$origin")
                else -> listOf("→\u00a0$end")
            }
            RouteChip(route, parts, both && origin.isNotEmpty(), favorites)
        }
    }
    val favoriteStops = remember(stopsById, favoriteStopIds) { favoriteStopIds.mapNotNull { stopsById[it] } }
    // Saved ones count until lines and stops load.
    val shownFavorites = if (routes.isEmpty() || stops.isEmpty()) favoriteRoutes.size + favoriteStopIds.size else chips.size + favoriteStops.size
    val groups by Services.groups.saved.collectAsStateWithLifecycle()
    val hasFavorites = shownFavorites > 0 || groups.isNotEmpty() || (online && favoritePlans.isNotEmpty())
    val recents = remember(recentList, stops, routes, favoritePlans, online) { recentItems(recentList, stops, routes, favoritePlans, online, RECENTS_SHOWN) }

    val firstSteps by Services.settings.firstSteps.collectAsStateWithLifecycle()
    val stepsLeft = firstSteps?.left.orEmpty()
    LaunchedEffect(Unit) {
        Services.settings.startFirstSteps(favoriteRoutes.isEmpty() && favoriteStopIds.isEmpty() && groups.isEmpty() && favoritePlans.isEmpty() && recentList.isEmpty())
    }
    LaunchedEffect(favoriteStopIds, favoriteRoutes, groups) {
        if (favoriteStopIds.isNotEmpty()) Services.settings.markFirstStep(FirstSteps.STOP)
        if (favoriteRoutes.isNotEmpty() || groups.isNotEmpty()) Services.settings.markFirstStep(FirstSteps.LINE)
    }
    val me by Services.location.collectAsStateWithLifecycle()
    val nearest = remember(me, index) {
        val fix = me ?: return@remember null
        index?.bundle?.stops?.filter { index?.look(it.stopId)?.routes?.isNotEmpty() == true }
            ?.minByOrNull { haversineMeters(fix.lat, fix.lon, it.lat, it.lon) }
    }
    val scope = rememberCoroutineScope()

    fun openRoute(route: Route, direction: String) = nav.route(route.routeId, direction)

    fun sectionTitle(id: String) = resources.getString(
        when (id) {
            HomeSections.REMINDERS -> R.string.reminders
            HomeSections.LINES -> R.string.w_favorite_routes
            HomeSections.GROUPS -> R.string.group_title
            HomeSections.STOPS -> R.string.w_favorite_stops
            HomeSections.PLACES -> R.string.w_favorite_plans
            else -> R.string.w_recent_destinations
        },
    )

    fun openPlan(plan: FavoritePlan) {
        if (online) nav.plan(planQueryFor(plan))
    }

    val removedLabel = stringResource(R.string.w_removed_from_favorites)
    val undoLabel = stringResource(R.string.w_undo)
    fun dropped(remove: () -> Unit) = FavoriteRemoval.dropped(removedLabel, undoLabel, remove)

    val routeReorder = rememberReorderState(
        chips.map { it.route.routeId },
        Removal(FavoriteRemoval::holdStart, { routeId: Int ->
            dropped { Services.favorites.reorderRoutes(Services.favorites.routes.value.filter { it.routeId != routeId }) }
        }) { routeId -> chips.find { it.route.routeId == routeId }?.let { FavoriteRouteChip(it, Modifier.fillMaxSize(), onClick = {}) } },
    ) { from, to ->
        Services.favorites.reorderRoutes(chips.moved(from, to).flatMap { it.favorites })
    }
    val stopReorder = rememberReorderState(
        favoriteStopIds,
        Removal(FavoriteRemoval::holdStart, { stopId: Int -> dropped { Services.favorites.reorderStops(Services.favorites.stops.value - stopId) } }) { stopId ->
            stopsById[stopId]?.let { FavoriteStopRow(it, index?.look(it.stopId), Modifier, lifted = true) {} }
        },
    ) { from, to -> Services.favorites.reorderStops(favoriteStopIds.moved(from, to)) }
    val planReorder = rememberReorderState(
        favoritePlans.map(::planKey),
        Removal(FavoriteRemoval::holdStart, { planId: String ->
            dropped { Services.favorites.reorderPlans(Services.favorites.plans.value.filter { planKey(it) != planId }) }
        }) { planId -> favoritePlans.find { planKey(it) == planId }?.let { FavoritePlanRow(it, Modifier, lifted = true) {} } },
    ) { from, to -> Services.favorites.reorderPlans(favoritePlans.moved(from, to)) }
    val groupReorder = rememberReorderState(
        groups.map { it.key },
        Removal(FavoriteRemoval::holdStart, { key: String ->
            dropped { Services.groups.reorder(Services.groups.saved.value.filter { it.key != key }) }
        }) { key ->
            groups.find { it.key == key }?.let { FavoriteGroupRow(it, index, Modifier, lifted = true) {} }
        },
    ) { from, to -> Services.groups.reorder(groups.moved(from, to)) }

    val sectionOrder by Services.settings.homeOrder.collectAsStateWithLifecycle()
    val collapsedSections by Services.settings.homeCollapsed.collectAsStateWithLifecycle()
    val shownSections = sectionOrder.filter {
        when (it) {
            HomeSections.REMINDERS -> reminders.isNotEmpty()
            HomeSections.LINES -> chips.isNotEmpty()
            HomeSections.GROUPS -> groups.isNotEmpty()
            HomeSections.STOPS -> favoriteStops.isNotEmpty()
            HomeSections.PLACES -> online && favoritePlans.isNotEmpty()
            HomeSections.RECENTS -> recents.isNotEmpty()
            else -> false
        }
    }
    val sectionReorder = rememberReorderState(shownSections) { from, to ->
        Services.settings.setHomeOrder(HomeSections.withShownOrder(sectionOrder, shownSections.moved(from, to)))
    }
    var arranging by rememberSaveable { mutableStateOf(false) }
    BackHandler(arranging) { arranging = false }
    LaunchedEffect(arranging) { if (arranging) listState.animateScrollToItem(0) }

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

        item(key = "places") { PlaceChips() }
        if (arranging) {
            item(key = "arrange-bar") { ArrangeBar(onSave = { arranging = false }) }
        } else if (stepsLeft.isNotEmpty()) {
            item(key = "first-steps") {
                FirstStepsCard(
                    left = stepsLeft,
                    nearest = nearest,
                    nearestLook = nearest?.let { index?.look(it.stopId) },
                    onPlan = { HomeRequests.search.value = "" },
                    onFindStop = { HomeRequests.search.value = "" },
                    onOpenStop = { nav.stop(it.stopId) },
                    onSaveStop = { stop -> Services.favorites.toggleStation(stop.stopId, index?.platforms(stop.stopId).orEmpty().map { it.stopId }) },
                    onLines = {
                        scope.launch {
                            val at = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "lines-in" }?.index
                                ?: (listOfNotNull(journey).size + follows.size + 2 + shownSections.size)
                            listState.animateScrollToItem(at)
                        }
                    },
                    onHide = Services.settings::hideFirstSteps,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        items(shownSections, key = { "section-$it" }) { id ->
            HomeSectionCard(
                id = id,
                title = sectionTitle(id),
                collapsed = id in collapsedSections,
                arranging = arranging,
                reorder = sectionReorder,
                onToggle = { Services.settings.toggleHomeSection(id) },
                onArrange = { arranging = true },
                modifier = Modifier.reorderItem(sectionReorder, id),
            ) {
                when (id) {
                    HomeSections.REMINDERS -> for (reminder in reminders) key(reminder.id) {
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
                            background = Color.Transparent,
                        )
                    }
                    HomeSections.LINES -> FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        for (chip in chips) key(chip.route.routeId) {
                            val routeId = chip.route.routeId
                            FavoriteRouteChip(
                                chip = chip,
                                modifier = Modifier.weight(1f).reorderItem(routeReorder, routeId).longPressDrag(routeReorder, routeId),
                                onClick = { openRoute(chip.route, chip.direction) },
                            )
                        }
                        if (chips.size % 2 == 1) Spacer(Modifier.weight(1f))
                    }
                    HomeSections.GROUPS -> for (group in groups) key(group.key) {
                        FavoriteGroupRow(
                            group,
                            index,
                            Modifier.reorderItem(groupReorder, group.key).longPressDrag(groupReorder, group.key),
                            lifted = groupReorder.dragging == group.key,
                        ) { index?.let { nav.group(it.writeLegs(group.legs)) } }
                    }
                    HomeSections.STOPS -> for (stop in favoriteStops) key(stop.stopId) {
                        FavoriteStopRow(
                            stop,
                            index?.look(stop.stopId),
                            Modifier.reorderItem(stopReorder, stop.stopId).longPressDrag(stopReorder, stop.stopId),
                            lifted = stopReorder.dragging == stop.stopId,
                        ) { nav.stop(stop.stopId) }
                    }
                    HomeSections.PLACES -> for (plan in favoritePlans) key(planKey(plan)) {
                        val planId = planKey(plan)
                        FavoritePlanRow(
                            plan,
                            Modifier.reorderItem(planReorder, planId).longPressDrag(planReorder, planId),
                            lifted = planReorder.dragging == planId,
                        ) { openPlan(plan) }
                    }
                    HomeSections.RECENTS -> for (item in recents) key(item.recent.key) {
                        val plan = item.plan
                        val stop = item.stop
                        val route = item.route
                        val group = item.group
                        RecentRow(
                            item,
                            iconSize = 30.dp,
                            background = Color.Transparent,
                            byKind = true,
                            onClick = {
                                if (plan != null) openPlan(plan)
                                else if (group != null) index?.let { nav.group(it.writeLegs(group)) }
                                else {
                                    Services.favorites.addRecent(item.recent)
                                    if (stop != null) nav.stop(stop.stopId) else if (route != null) openRoute(route, "auto")
                                }
                            },
                        )
                    }
                }
            }
        }
        if (!hasFavorites && recents.isEmpty() && stepsLeft.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.w_no_favorites),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!hasFavorites || stepsLeft.isNotEmpty()) {
            allLines(lines) { nav.route(it.routeId, "auto") }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
    reminding?.let { ReminderSheet(it) { reminding = null } }
}

@Composable
private fun rowBackground(lifted: Boolean) = if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent

@Composable
private fun FavoriteRouteChip(chip: RouteChip, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.squish(press, 0.93f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = press,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LineBadge(chip.route.shortName, chip.route.color, height = 28.dp, minWidth = 36.dp, corner = 8.dp)
                if (chip.both) Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chip.both) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    for (part in chip.parts) {
                        Text(part, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Text(
                    chip.parts.first(),
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FavoriteGroupRow(group: SavedGroup, index: NetworkIndex?, modifier: Modifier, lifted: Boolean, onClick: () -> Unit) = FavoriteRow(
    modifier = modifier,
    icon = null,
    title = group.name,
    lines = { LineBadges(index?.groupRoutes(group.lines).orEmpty()) },
    kicker = null,
    onClick = onClick,
    background = rowBackground(lifted),
)

@Composable
private fun FavoriteStopRow(stop: Stop, look: StationLook?, modifier: Modifier, lifted: Boolean, onClick: () -> Unit) = FavoriteRow(
    modifier = modifier,
    icon = { StationIcon(look, size = 30.dp) },
    title = stop.name,
    lines = { StationLines(look) },
    kicker = null,
    onClick = onClick,
    background = rowBackground(lifted),
)

@Composable
private fun FavoritePlanRow(plan: FavoritePlan, modifier: Modifier, lifted: Boolean, onClick: () -> Unit) {
    val label = placeIcon(plan.label ?: plan.name)
    FavoriteRow(
        modifier = modifier,
        icon = {
            RowIcon(
                Icons.Rounded.Map,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                size = 30.dp,
                emoji = label.icon,
            )
        },
        title = label.name,
        titlePrefix = if (plan.label == null && plan.originName != null) stringResource(R.string.w_plan_to) + ": " else null,
        kicker = if (plan.label == null) plan.originName?.let { "${stringResource(R.string.w_plan_from)}: $it" } else null,
        onClick = onClick,
        background = rowBackground(lifted),
    )
}

@Composable
fun FavoriteRow(
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    title: String?,
    kicker: String?,
    onClick: () -> Unit,
    removeLabel: String? = null,
    onRemove: () -> Unit = {},
    titlePrefix: String? = null,
    subtitle: String? = null,
    lines: (@Composable () -> Unit)? = null,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().background(background).squishClickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
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
            if (title != null) {
                Text(
                    buildString {
                        titlePrefix?.let(::append)
                        append(title)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            lines?.let { Box(Modifier.padding(top = if (title != null) 4.dp else 0.dp)) { it() } }
        }
        trailing?.invoke()
        if (removeLabel != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = removeLabel, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}
