package online.bus.bmarian.conexiuniclujmobile.ui.route

import android.icu.text.PluralRules
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.linePath
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords
import online.bus.bmarian.conexiuniclujmobile.data.tripIdFor
import online.bus.bmarian.conexiuniclujmobile.follow.FollowManager
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyPoint
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.journey.ridePath
import online.bus.bmarian.conexiuniclujmobile.journey.simplifyPath
import online.bus.bmarian.conexiuniclujmobile.logic.HourGroup
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedStop
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.ScheduleDays
import online.bus.bmarian.conexiuniclujmobile.logic.SpecialDay
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.activeFrequency
import online.bus.bmarian.conexiuniclujmobile.logic.availableTabs
import online.bus.bmarian.conexiuniclujmobile.logic.changeRows
import online.bus.bmarian.conexiuniclujmobile.logic.changeSummary
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import online.bus.bmarian.conexiuniclujmobile.logic.chipsByHour
import online.bus.bmarian.conexiuniclujmobile.logic.chipsForDay
import online.bus.bmarian.conexiuniclujmobile.logic.daysLabel
import online.bus.bmarian.conexiuniclujmobile.logic.directionKeys
import online.bus.bmarian.conexiuniclujmobile.logic.formatAbsoluteMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.formatChangeDate
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.logic.formatServiceStart
import online.bus.bmarian.conexiuniclujmobile.logic.groupByDays
import online.bus.bmarian.conexiuniclujmobile.logic.groupByDirection
import online.bus.bmarian.conexiuniclujmobile.logic.isPastTab
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import online.bus.bmarian.conexiuniclujmobile.logic.pathOf
import online.bus.bmarian.conexiuniclujmobile.logic.plainTodayTab
import online.bus.bmarian.conexiuniclujmobile.logic.shownTab
import online.bus.bmarian.conexiuniclujmobile.logic.specialDayOn
import online.bus.bmarian.conexiuniclujmobile.logic.stopsAlong
import online.bus.bmarian.conexiuniclujmobile.logic.stopsForDirection
import online.bus.bmarian.conexiuniclujmobile.logic.todayTab
import online.bus.bmarian.conexiuniclujmobile.logic.tripHourlyOffsets
import online.bus.bmarian.conexiuniclujmobile.logic.tripStops
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.Bouncy
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LoadFailed
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MenuAction
import online.bus.bmarian.conexiuniclujmobile.ui.MessageText
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.Ticker
import online.bus.bmarian.conexiuniclujmobile.ui.badgeKey
import online.bus.bmarian.conexiuniclujmobile.ui.cascade
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderSheet
import online.bus.bmarian.conexiuniclujmobile.ui.follow.openOnWatch
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.rememberCascade
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.sharedBadge
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import online.bus.bmarian.conexiuniclujmobile.ui.theme.lineColor

@Composable
fun routeViewModel(routeId: Int, direction: String, fromStopId: Int?, changeId: Long?): RouteViewModel =
    viewModel(key = "route-$routeId-${fromStopId ?: ""}-${changeId ?: ""}") { RouteViewModel(routeId, direction, fromStopId, changeId) }

private enum class StopMark { From, Nearest, Favorite, Terminal, Plain }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteScreen(routeId: Int, direction: String, fromStopId: Int?, changeId: Long?) {
    val vm = routeViewModel(routeId, direction, fromStopId, changeId)
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.live() } }

    val info by vm.shapeInfo.collectAsStateWithLifecycle()
    val dir by vm.direction.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val headings by vm.headings.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val initialLoading by vm.initialLoading.collectAsStateWithLifecycle()
    val fromStopName by vm.fromStopName.collectAsStateWithLifecycle()
    val activeChange by vm.activeChange.collectAsStateWithLifecycle()
    val hourly by vm.hourlyOffsets.collectAsStateWithLifecycle()
    val favoriteStops by Services.favorites.stops.collectAsStateWithLifecycle()
    val city by Cities.current.collectAsStateWithLifecycle()
    val favoriteRoutes by Services.favorites.routes.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    val isFavorite = favoriteRoutes.any { it.routeId == routeId && it.direction == dir }
    val isFollowing = info?.let { it.shortName in followed } == true

    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        map.directionArrowAtStart = true
        onDispose { map.release(owner) }
    }
    val current = ui
    LaunchedEffect(current?.shape, info) {
        val shape = current?.shape ?: return@LaunchedEffect
        val route = info ?: return@LaunchedEffect
        map.showShapes(listOf(MapShape(tripIdFor(routeId, dir), route.shortName, route.color, shape.shape.map { LatLon(it.lat, it.lon) })))
    }
    LaunchedEffect(current?.vehicles, current?.roundTripVehicles) {
        val name = info?.shortName.orEmpty()
        val color = info?.color ?: "#64748b"
        map.vehicles = current?.roundTripVehicles.orEmpty().map { MapVehicle("${it.id}", it, name, color, it.heading, faded = true) } +
            current?.vehicles.orEmpty().map { MapVehicle("${it.id}", it, name, color, it.heading) }
    }
    LaunchedEffect(current?.stops, current?.nearestStopIdx, favoriteStops) {
        val stops = current?.stops ?: return@LaunchedEffect
        map.highlightedStops = stops.mapIndexed { idx, stop ->
            val id = stop.stop.stopId
            id to when {
                id == fromStopId -> StopHighlight.Green
                idx == current.nearestStopIdx -> StopHighlight.Purple
                id in favoriteStops -> StopHighlight.Favorite
                else -> StopHighlight.Gray
            }
        }.toMap()
    }

    var tab by rememberSaveable { mutableStateOf<TimetableTab?>(null) }
    val now = current?.now
    val calendar by ScheduleDays.state.collectAsStateWithLifecycle()
    val today = now?.let { todayTab(it, calendar) } ?: TimetableTab.Weekdays
    val plainToday = now?.let(::plainTodayTab) ?: TimetableTab.Weekdays
    val special = now?.let { specialDayOn(it.toLocalDate(), calendar) }
    val tabs = remember(info) { info?.let { availableTabs(it.timetable) }.orEmpty() }
    val selectedTab = shownTab(tab ?: today, tabs)
    var selectedTime by rememberSaveable(dir, selectedTab) { mutableStateOf<String?>(null) }
    LaunchedEffect(info?.shortName, selectedTab) {
        val name = info?.shortName ?: return@LaunchedEffect
        vm.loadHourly(name, selectedTab.dayType)
    }

    val listState = rememberLazyListState()
    val cascade = rememberCascade(dir)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val watch by WearLink.watchInReach.collectAsStateWithLifecycle()
    val follows by FollowManager.follows.collectAsStateWithLifecycle()
    val reminders by Reminders.all.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var reminding by remember { mutableStateOf<Followed?>(null) }
    val menu = buildList {
        val shortName = info?.shortName
        if (shortName != null) {
            add(
                MenuAction(
                    stringResource(if (isFollowing) R.string.line_following else R.string.line_follow),
                    if (isFollowing) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                ) {
                    val following = Services.routeUpdates.toggleFollow(shortName)
                        ?: return@MenuAction Services.settings.showToast(resources.getString(R.string.w_limit_reached, SyncRecords.CITY_CAP.toString()))
                    Services.settings.showToast(
                        resources.getString(if (following) R.string.w_follow_line_toast else R.string.w_unfollow_line_toast, shortName),
                        resources.getString(if (following) R.string.w_follow_line_toast_body else R.string.w_unfollow_line_toast_body),
                    )
                },
            )
        }
        if (watch) {
            add(MenuAction(stringResource(R.string.open_on_watch), Icons.Rounded.Watch) { scope.launch { openOnWatch(context, "/route/$routeId/$dir") } })
        }
        add(MenuAction(stringResource(R.string.refresh), Icons.Rounded.Refresh) { vm.load() })
    }
    val barTitle = info?.let { "${it.shortName} · ${it.timetable.routeLongName.ifEmpty { it.shortName }}" }

    if (initialLoading && info == null) {
        Column(Modifier.fillMaxSize()) {
            SheetTopBar(null, false)
            LoadingRow(text = stringResource(R.string.w_loading_route))
        }
        return
    }
    val route = info
    if (route == null) {
        Column(Modifier.fillMaxSize()) {
            SheetTopBar(null, false)
            if (failed) {
                LoadFailed(vm::load)
            } else {
                LoadingRow(text = stringResource(R.string.w_loading_route))
            }
        }
        return
    }

    val stops = current?.stops.orEmpty()
    val networkIndex by Services.repository.network.index.collectAsStateWithLifecycle()
    val keysByDirection = remember(route, networkIndex) {
        directionKeys(stopsForDirection(route, tripIdFor(routeId, "0")).map { it.stop }, stopsForDirection(route, tripIdFor(routeId, "1")).map { it.stop }) {
            networkIndex?.stationId(it.stopId) ?: it.stopId
        }
    }
    val stopKeys = (if (dir == "0") keysByDirection.first else keysByDirection.second).takeIf { it.size == stops.size } ?: stops.indices.map { "s-$it" }
    var shownKeys by remember { mutableStateOf(emptySet<String>()) }
    val stayingKeys = remember(dir) { shownKeys }
    SideEffect { shownKeys = stopKeys.toSet() }
    val busTarget = stops.indexOfFirst { it.stop.stopId == fromStopId }.takeIf { it >= 0 } ?: current?.nearestStopIdx ?: -1
    val directionPath = remember(current?.shape) { current?.shape?.shape?.let(::pathOf) }
    val stopsAt = remember(directionPath, stops) { directionPath?.let { stopsAlong(it, stops) }.orEmpty() }
    val buses = rememberListBuses(directionPath, stopsAt, current?.vehicles.orEmpty(), busTarget)
    val lineColor = LocalLineLooks.current.color(route.shortName, route.color)
    val outgoing = dir == "0"
    val tripId = tripIdFor(routeId, dir)
    val hourlyTrip = remember(hourly, stops, tripId) { tripHourlyOffsets(hourly, tripId, stops) }
    val chips = remember(route, selectedTab, outgoing, hourlyTrip, stops, now, calendar) {
        if (now == null) emptyList() else chipsForDay(route.timetable, selectedTab, outgoing, hourlyTrip, stops, now, calendar)
    }
    val suspended = chips.isNotEmpty() && chips.all { it.startMin == null }
    val groups = remember(chips) { chipsByHour(chips) }
    val frequency = activeFrequency(route.timetable, selectedTab, outgoing)
    val nextChip = groups.firstNotNullOfOrNull { g -> g.chips.firstOrNull { !it.isPast }?.time }
    val trip = remember(selectedTime, hourlyTrip, stops) { selectedTime?.let { tripStops(it, hourlyTrip, stops) }.orEmpty() }
    val nowLabel = stringResource(R.string.w_now)
    val soonLabel = stringResource(R.string.w_soon)

    fun mark(idx: Int, stop: IndexedStop): StopMark = when {
        stop.stop.stopId == fromStopId -> StopMark.From
        idx == current?.nearestStopIdx -> StopMark.Nearest
        stop.stop.stopId in favoriteStops -> StopMark.Favorite
        idx == 0 || idx == stops.lastIndex -> StopMark.Terminal
        else -> StopMark.Plain
    }

    fun wakeMeAt(idx: Int) {
        val boarding = current?.nearestStopIdx?.takeIf { it in 0 until idx }
            ?: stops.indexOfFirst { it.stop.stopId == fromStopId }.takeIf { it in 0 until idx }
            ?: 0
        if (idx <= boarding) return
        val ride = stops.subList(boarding, idx + 1)
        fun js(s: IndexedStop) = JourneyStop(s.stop.stopId, s.stop.headsign.ifEmpty { "${s.stop.stopId}" }, s.stop.lat, s.stop.lon)
        val path = current?.shape?.shape.orEmpty().map { JourneyPoint(it.lat, it.lon) }
            .let { shape -> ridePath(shape, stops.map { JourneyPoint(it.stop.lat, it.stop.lon) }, boarding, idx) }
        val leg = JourneyLeg(
            routeIds = listOf(routeId),
            tripIds = listOf(tripId),
            lines = listOf(route.shortName),
            colors = listOf(route.color),
            stops = ride.map(::js),
            rideMinutes = (ride.last().secondsFromStart - ride.first().secondsFromStart) / 60,
            path = simplifyPath(path.map { LatLon(it.lat, it.lon) }),
        )
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val journey = Journey(
            JourneyManager.newId(), js(ride.last()), listOf(leg),
            planPath = "/route/$routeId/$dir", city = Cities.current.value, onBoard = true,
        )
        JourneyManager.start(context, journey)
    }

    Column(Modifier.fillMaxSize()) {
    SheetTopBar(barTitle, listState.pastHeader(), menu = menu)
    Box(Modifier.weight(1f)) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        activeChange?.let { change ->
            item(key = "change") {
                RouteChangeBanner(change, onDismiss = { vm.activeChange.value = null })
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LineBadge(
                    route.shortName, route.color, Modifier.sharedBadge(badgeKey(routeId, tripId)),
                    height = 52.dp, minWidth = 52.dp, corner = 16.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.w_route), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        route.timetable.routeLongName.ifEmpty { route.shortName },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    fromStopName?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Emerald))
                            Text(
                                stringResource(R.string.w_from, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            ActionChips(
                listOfNotNull(
                    ChipAction(
                        stringResource(if (isFavorite) R.string.saved else R.string.save),
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        selected = isFavorite,
                        tint = if (isFavorite) Palette.Rose else Color.Unspecified,
                    ) {
                        haptics.performHapticFeedback(if (!isFavorite) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        Services.favorites.toggleRoute(routeId, dir)
                    },
                    ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) {
                        share(context, Services.repository.network.index.value.linePath(routeId, dir))
                    },
                ),
                Modifier.padding(bottom = 12.dp),
            )
        }
        item {
            ChoiceRow(
                listOf("0", "1"), dir, { vm.direction.value = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = vm::hasDirection,
            ) { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val heading = headings[d]?.minus(map.bearing)
                    if (d == "0") DirectionArrow(Icons.AutoMirrored.Rounded.ArrowForward, 90.0, heading, d == dir)
                    Text(vm.terminal(d), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d == "1") DirectionArrow(Icons.AutoMirrored.Rounded.ArrowBack, 270.0, heading, d == dir)
                }
            }
        }
        if (stops.isEmpty()) {
            item { MessageText(stringResource(R.string.w_no_schedule), Modifier.padding(top = 16.dp)) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(stringResource(R.string.w_stops), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.stops_hold_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.w_next_departures).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TimeColumns(current?.headerTimes.orEmpty().map { formatMinutesFromNow(it, current!!.now, nowLabel) to false }, header = true)
                    }
                }
            }
            itemsIndexed(stops, key = { idx, _ -> stopKeys[idx] }) { idx, stop ->
                val arrivals = current?.arrivals?.getOrNull(idx).orEmpty()
                val name = stop.stop.headsign.ifEmpty { "Stop ${idx + 1}" }
                val alerting = follows.firstOrNull { it.matches(stop.stop.stopId, routeId, tripId) }
                val followed = Followed(stop.stop.stopId, name, routeId, tripId, route.shortName, route.color, vm.terminal(dir), city)
                Box(
                    Modifier
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(Bouncy.slide.dampingRatio, Bouncy.slide.stiffness, IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(140),
                        )
                        .then(if (stopKeys[idx] in stayingKeys) Modifier else Modifier.cascade(cascade)),
                ) {
                    StopLine(
                        name = name,
                        mark = mark(idx, stop),
                        favorite = stop.stop.stopId in favoriteStops,
                        first = idx == 0,
                        last = idx == stops.lastIndex,
                        times = arrivals.map { (if (it.soon) soonLabel else formatMinutesFromNow(it.minutes, current!!.now, nowLabel)) to it.isLive },
                        trailingText = null,
                        paint = { buses.paintIn(idx) },
                        paintColor = lineColor,
                        alerting = alerting != null,
                        reminded = reminders.any { it.isFor(stop.stop.stopId, routeId, tripId) },
                        onClick = { nav.stop(stop.stop.stopId) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuFor = idx
                        },
                    )
                    DropdownMenu(expanded = menuFor == idx, onDismissRequest = { menuFor = null }, offset = DpOffset(44.dp, 0.dp)) {
                        if (alerting != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stop_menu_notify_off)) },
                                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                                onClick = {
                                    menuFor = null
                                    FollowManager.stop(context, alerting.id)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remind_menu)) },
                            leadingIcon = { Icon(Icons.Rounded.AlarmAdd, null) },
                            onClick = {
                                menuFor = null
                                reminding = followed
                            },
                        )
                        if (idx > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stop_menu_wake)) },
                                leadingIcon = { Icon(Icons.Rounded.Alarm, null) },
                                onClick = {
                                    menuFor = null
                                    wakeMeAt(idx)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (tabs.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.w_timetable), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(
                        stringResource(R.string.w_timetable_click_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            item {
                ChoiceRow(tabs, selectedTab, { tab = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { t ->
                    val label = stringResource(
                        when (t) {
                            TimetableTab.Weekdays -> R.string.w_weekdays
                            TimetableTab.Saturday -> R.string.w_saturday
                            TimetableTab.Sunday -> R.string.w_sunday
                        },
                    )
                    Text(
                        label,
                        color = if (isPastTab(t, plainToday) && selectedTab != t) MaterialTheme.colorScheme.outline else Color.Unspecified,
                        maxLines = 1,
                    )
                    if (t == today) Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(LocalContentColor.current))
                }
            }
            if (special != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 6.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(specialDayText(special), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (suspended) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(stringResource(R.string.w_service_suspended), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (frequency != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("↻", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                        Column {
                            if (frequency.minMinutes != 0 || frequency.maxMinutes != 0) {
                                val isRange = frequency.minMinutes != 0 && frequency.maxMinutes != 0 && frequency.minMinutes != frequency.maxMinutes
                                val last = if (isRange || frequency.minMinutes == 0) frequency.maxMinutes else frequency.minMinutes
                                val withDe = PluralRules.forLocale(LocalConfiguration.current.locales[0]).select(last.toDouble()) == "other"
                                val label = if (isRange) {
                                    val id = if (withDe) R.string.w_frequency_every_range_other else R.string.w_frequency_every_range
                                    stringResource(id, "${frequency.minMinutes}", "${frequency.maxMinutes}")
                                } else {
                                    stringResource(if (withDe) R.string.w_frequency_every_other else R.string.w_frequency_every, "$last")
                                }
                                Text(label, style = MaterialTheme.typography.titleSmall)
                            }
                            if (frequency.start.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.w_frequency_window, frequency.start, frequency.end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            items(groups, key = { g -> "h-${g.hour}-${g.isNextDay}" }) { group ->
                HourRow(group, selectedTime, nextChip) { chip ->
                    selectedTime = if (selectedTime == chip) null else chip
                    if (selectedTime != null) {
                        scope.launch {
                            val index = listState.layoutInfo.totalItemsCount - 1
                            listState.animateScrollToItem(index.coerceAtLeast(0))
                        }
                    }
                }
            }
            if (trip.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.w_trip_at, trip.first().arrival),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { selectedTime = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_close_trip_view))
                        }
                    }
                }
                itemsIndexed(trip, key = { idx, s -> "trip-${s.stop.stop.stopId}-$idx" }) { idx, s ->
                    StopLine(
                        name = s.stop.stop.headsign.ifEmpty { "Stop ${idx + 1}" },
                        mark = mark(idx, s.stop),
                        favorite = s.stop.stop.stopId in favoriteStops,
                        first = idx == 0,
                        last = idx == trip.lastIndex,
                        times = emptyList(),
                        trailingText = s.arrival,
                        trailingHighlight = idx == 0,
                        onClick = { nav.stop(s.stop.stop.stopId) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    StopListBuses(
        buses, listState, stopKeys, busTarget,
        mark = if (stops.getOrNull(busTarget)?.stop?.stopId == fromStopId) Palette.Emerald else Palette.Purple,
        color = lineColor,
        mode = modeOf(route.type),
        modifier = Modifier.matchParentSize(),
    )
    }
    }
    reminding?.let { ReminderSheet(it) { reminding = null } }
}

@Composable
private fun TimeColumns(times: List<Pair<String, Boolean>>, header: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, live) in times) {
            Text(
                label,
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = if (live) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    live -> MaterialTheme.colorScheme.primary
                    header -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

internal val RAIL_START = 16.dp
internal val RAIL_WIDTH = 20.dp

@Composable
private fun StopLine(
    name: String,
    mark: StopMark,
    favorite: Boolean,
    first: Boolean,
    last: Boolean,
    times: List<Pair<String, Boolean>>,
    trailingText: String?,
    trailingHighlight: Boolean = false,
    paint: (() -> ClosedFloatingPointRange<Float>?)? = null,
    paintColor: Color = Color.Unspecified,
    alerting: Boolean = false,
    reminded: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val rowTint = when (mark) {
        StopMark.From -> Palette.Emerald.copy(alpha = 0.08f)
        StopMark.Nearest -> Palette.Purple.copy(alpha = 0.08f)
        StopMark.Favorite -> Palette.Rose.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().background(rowTint).squishClickable(onLongClick = onLongClick, onClick = onClick).padding(start = RAIL_START, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RAIL_WIDTH).height(40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 2.dp.toPx()
                drawLine(rail, Offset(x, if (first) size.height / 2 else 0f), Offset(x, if (last) size.height / 2 else size.height), w)
                paint?.invoke()?.let { part ->
                    drawLine(paintColor, Offset(x, part.start * size.height), Offset(x, part.endInclusive * size.height), 4.dp.toPx())
                }
            }
            val color = when (mark) {
                StopMark.From -> Palette.Emerald
                StopMark.Nearest -> Palette.Purple
                StopMark.Favorite -> Palette.Rose
                StopMark.Terminal -> Palette.Slate400
                StopMark.Plain -> null
            }
            if (color != null) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color).border(2.dp, surface, CircleShape))
            } else {
                Box(Modifier.size(9.dp).clip(CircleShape).background(surface).border(2.dp, rail, CircleShape))
            }
        }
        Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ticker(
                name,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = when (mark) {
                    StopMark.From -> FontWeight.Bold
                    StopMark.Nearest, StopMark.Terminal -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                color = when (mark) {
                    StopMark.From -> Palette.Emerald
                    StopMark.Nearest -> Palette.Purple
                    StopMark.Terminal -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            when (mark) {
                StopMark.From -> Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = Palette.Emerald)
                StopMark.Nearest -> Icon(Icons.Rounded.MyLocation, null, Modifier.size(14.dp), tint = Palette.Purple)
                else -> {}
            }
            if (favorite) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = Palette.Rose)
            }
            if (alerting) {
                Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (reminded) {
                Icon(Icons.Rounded.Alarm, stringResource(R.string.remind_saved), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (trailingHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TimeColumns(times)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourRow(group: HourGroup, selected: String?, next: String?, onSelect: (String) -> Unit) {
    val gone = group.chips.all { it.isPast }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(
            group.hour,
            modifier = Modifier.width(36.dp).padding(end = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = if (gone || group.isNextDay) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
        )
        VerticalDivider(Modifier.fillMaxHeight(), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
        FlowRow(
            Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (chip in group.chips) {
                val isSelected = selected == chip.time
                val isNext = selected == null && next == chip.time
                val press = remember { MutableInteractionSource() }
                val corner by animateDpAsState(if (isSelected) 16.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "time")
                Surface(
                    onClick = { onSelect(chip.time) },
                    modifier = Modifier.squish(press, 0.88f),
                    interactionSource = press,
                    shape = RoundedCornerShape(corner),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        chip.isPast -> Color.Transparent
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    border = if (isNext) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Text(
                        formatAbsoluteMinutes(chip.startMin!!).drop(3),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (chip.isPast && !isSelected) FontWeight.Normal else FontWeight.Bold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            chip.isPast -> MaterialTheme.colorScheme.outlineVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private const val ENTRY_LIMIT = 12

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteChangeBanner(change: RouteChange, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    var showMore by rememberSaveable(change.id) { mutableStateOf(false) }
    var openRows by remember(change.id) { mutableStateOf(emptySet<String>()) }
    val line = lineColor(change.routeShortName, change.routeColor.ifEmpty { "#64748b" })
    Column(
        Modifier.fillMaxWidth().background(line.copy(alpha = 0.10f)).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Notifications, null, Modifier.size(20.dp).padding(top = 2.dp), tint = line)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row {
                    Text(changeTitle(change, context), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        " · ${formatChangeDate(change.detectedAt, locale, withYear = false)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(changeSummary(change, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showMore) {
                    for (group in groupByDirection(change)) {
                        if (group.toward.isNotEmpty()) {
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.w_route_change_toward, group.toward), style = MaterialTheme.typography.labelLarge)
                                HorizontalDivider(Modifier.weight(1f).padding(start = 8.dp), color = line.copy(alpha = 0.3f))
                            }
                        }
                        for (day in groupByDays(group.items)) {
                            val label = daysLabel(day.days, context)
                            if (label.isNotEmpty()) {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    day.since?.let {
                                        Text(
                                            stringResource(R.string.w_route_change_since, formatServiceStart(it, locale)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            for (row in changeRows(day.items, context)) {
                                val key = "${group.direction}-${day.key}-${row.kind}"
                                val entries = if (key in openRows) row.entries else row.entries.take(ENTRY_LIMIT)
                                val hidden = row.entries.size - entries.size
                                val names = row.kind == "stops_added" || row.kind == "stops_removed"
                                Text(
                                    row.label,
                                    Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(if (names) 6.dp else 14.dp)) {
                                    entries.forEachIndexed { i, entry ->
                                        Row {
                                            entry.old?.let {
                                                Text(
                                                    it,
                                                    Modifier.padding(end = 5.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    textDecoration = TextDecoration.LineThrough,
                                                )
                                            }
                                            Text(
                                                entry.text + if (names && i < entries.lastIndex) "," else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (entry.struck) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                                textDecoration = if (entry.struck) TextDecoration.LineThrough else null,
                                            )
                                        }
                                    }
                                    if (hidden > 0) {
                                        Text(
                                            stringResource(R.string.w_route_change_more, "$hidden"),
                                            Modifier.clickable { openRows = openRows + key },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(if (showMore) R.string.w_route_change_show_less else R.string.w_route_change_show_more),
                    Modifier.padding(top = 6.dp).clickable { showMore = !showMore },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_dismiss))
            }
        }
    }
}

private fun timetableName(schedule: String): Int = when (schedule) {
    "saturday" -> R.string.w_timetable_name_saturday
    "sunday" -> R.string.w_timetable_name_sunday
    else -> R.string.w_timetable_name_weekday
}

private fun holidayName(key: String): Int? = when (key) {
    "new_year" -> R.string.w_holiday_new_year
    "new_year_2" -> R.string.w_holiday_new_year_2
    "epiphany" -> R.string.w_holiday_epiphany
    "saint_john" -> R.string.w_holiday_saint_john
    "union_day" -> R.string.w_holiday_union_day
    "good_friday" -> R.string.w_holiday_good_friday
    "easter" -> R.string.w_holiday_easter
    "easter_monday" -> R.string.w_holiday_easter_monday
    "labour_day" -> R.string.w_holiday_labour_day
    "childrens_day" -> R.string.w_holiday_childrens_day
    "pentecost" -> R.string.w_holiday_pentecost
    "pentecost_monday" -> R.string.w_holiday_pentecost_monday
    "assumption" -> R.string.w_holiday_assumption
    "saint_andrew" -> R.string.w_holiday_saint_andrew
    "national_day" -> R.string.w_holiday_national_day
    "christmas" -> R.string.w_holiday_christmas
    "christmas_2" -> R.string.w_holiday_christmas_2
    else -> null
}

@Composable
private fun specialDayText(special: SpecialDay): String {
    val timetable = stringResource(timetableName(special.schedule))
    val holiday = special.note?.holiday?.let(::holidayName)
    return if (holiday != null) {
        stringResource(R.string.w_special_day_holiday, stringResource(holiday), timetable)
    } else {
        stringResource(R.string.w_special_day_changed, timetable)
    }
}
