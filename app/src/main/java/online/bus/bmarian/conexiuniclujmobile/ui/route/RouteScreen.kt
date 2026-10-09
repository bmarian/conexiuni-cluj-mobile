package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
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
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.tripIdFor
import online.bus.bmarian.conexiuniclujmobile.follow.FollowManager
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyLeg
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyStop
import online.bus.bmarian.conexiuniclujmobile.logic.HourGroup
import online.bus.bmarian.conexiuniclujmobile.logic.IndexedStop
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.activeFrequency
import online.bus.bmarian.conexiuniclujmobile.logic.availableTabs
import online.bus.bmarian.conexiuniclujmobile.logic.changeRows
import online.bus.bmarian.conexiuniclujmobile.logic.changeSummary
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import online.bus.bmarian.conexiuniclujmobile.logic.chipsByHour
import online.bus.bmarian.conexiuniclujmobile.logic.chipsForDay
import online.bus.bmarian.conexiuniclujmobile.logic.daysLabel
import online.bus.bmarian.conexiuniclujmobile.logic.formatAbsoluteMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.formatChangeDate
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.logic.formatServiceStart
import online.bus.bmarian.conexiuniclujmobile.logic.groupByDays
import online.bus.bmarian.conexiuniclujmobile.logic.groupByDirection
import online.bus.bmarian.conexiuniclujmobile.logic.isPastTab
import online.bus.bmarian.conexiuniclujmobile.logic.todayTab
import online.bus.bmarian.conexiuniclujmobile.logic.tripHourlyOffsets
import online.bus.bmarian.conexiuniclujmobile.logic.tripStops
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MenuAction
import online.bus.bmarian.conexiuniclujmobile.ui.MessageText
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.badgeKey
import online.bus.bmarian.conexiuniclujmobile.ui.cascade
import online.bus.bmarian.conexiuniclujmobile.ui.follow.openOnWatch
import online.bus.bmarian.conexiuniclujmobile.ui.follow.rememberWithNotifications
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapVehicle
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.sharedBadge
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor

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
    val failed by vm.failed.collectAsStateWithLifecycle()
    val initialLoading by vm.initialLoading.collectAsStateWithLifecycle()
    val fromStopName by vm.fromStopName.collectAsStateWithLifecycle()
    val activeChange by vm.activeChange.collectAsStateWithLifecycle()
    val hourly by vm.hourlyOffsets.collectAsStateWithLifecycle()
    val favoriteStops by Services.favorites.stops.collectAsStateWithLifecycle()
    val favoriteRoutes by Services.favorites.routes.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    val showChanges by Services.settings.showTimetableChanges.collectAsStateWithLifecycle()
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
    LaunchedEffect(current?.vehicles) {
        map.vehicles = current?.vehicles.orEmpty().map { MapVehicle("${it.vehicle.id}", it.vehicle, it.routeShortName, it.routeColor, it.heading) }
    }
    LaunchedEffect(current?.stops, current?.nearestStopIdx, favoriteStops) {
        val stops = current?.stops ?: return@LaunchedEffect
        map.highlightedStops = stops.mapIndexed { idx, stop ->
            val id = stop.stop.stopId
            id to when {
                id == fromStopId -> StopHighlight.Green
                idx == current.nearestStopIdx -> StopHighlight.Purple
                id in favoriteStops -> StopHighlight.Red
                else -> StopHighlight.Gray
            }
        }.toMap()
    }

    // Easter egg: five taps on the direction buttons within 2 s.
    val taps = remember { mutableStateListOf<Long>() }
    var pong by rememberSaveable { mutableStateOf(false) }
    fun onDirection(target: String) {
        vm.direction.value = target
        val now = System.currentTimeMillis()
        taps.removeAll { now - it > 2000 }
        taps += now
        if (taps.size >= 5) {
            pong = true
            taps.clear()
        }
    }

    var tab by rememberSaveable { mutableStateOf<TimetableTab?>(null) }
    val now = current?.now
    val today = now?.let(::todayTab) ?: TimetableTab.Weekdays
    val selectedTab = tab ?: today
    var selectedTime by rememberSaveable(dir, selectedTab) { mutableStateOf<String?>(null) }
    LaunchedEffect(info?.shortName, selectedTab) {
        val name = info?.shortName ?: return@LaunchedEffect
        vm.loadHourly(name, selectedTab.dayType)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val watch by WearLink.watchInReach.collectAsStateWithLifecycle()
    val following by FollowManager.state.collectAsStateWithLifecycle()
    val withNotifications = rememberWithNotifications()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val menu = buildList {
        val shortName = info?.shortName
        if (showChanges && shortName != null) {
            add(
                MenuAction(
                    stringResource(if (isFollowing) R.string.line_following else R.string.line_follow),
                    if (isFollowing) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                ) {
                    val following = Services.routeUpdates.toggleFollow(shortName)
                    Services.settings.showToast(
                        resources.getString(if (following) R.string.w_follow_line_toast else R.string.w_unfollow_line_toast, shortName),
                        resources.getString(if (following) R.string.w_follow_line_toast_body else R.string.w_unfollow_line_toast_body),
                    )
                },
            )
        }
        if (watch) add(MenuAction(stringResource(R.string.open_on_watch), Icons.Rounded.Watch) { scope.launch { openOnWatch(context, "/route/$routeId/$dir") } })
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.load_failed))
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = vm::load) { Text(stringResource(R.string.retry)) }
                }
            } else {
                LoadingRow(text = stringResource(R.string.w_loading_route))
            }
        }
        return
    }

    val stops = current?.stops.orEmpty()
    val outgoing = dir == "0"
    val tripId = tripIdFor(routeId, dir)
    val hourlyTrip = remember(hourly, stops, tripId) { tripHourlyOffsets(hourly, tripId, stops) }
    val tabs = remember(route) { availableTabs(route.timetable) }
    val chips = remember(route, selectedTab, outgoing, hourlyTrip, stops, now) {
        if (now == null) emptyList() else chipsForDay(route.timetable, selectedTab, outgoing, hourlyTrip, stops, now)
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
        val boarding = stops.indexOfFirst { it.stop.stopId == fromStopId }.takeIf { it in 0 until idx }
            ?: current?.nearestStopIdx?.takeIf { it in 0 until idx }
            ?: 0
        if (idx <= boarding) return
        val ride = stops.subList(boarding, idx + 1)
        fun js(s: IndexedStop) = JourneyStop(s.stop.stopId, s.stop.headsign.ifEmpty { "${s.stop.stopId}" }, s.stop.lat, s.stop.lon)
        val leg = JourneyLeg(
            routeIds = listOf(routeId),
            tripIds = listOf(tripId),
            lines = listOf(route.shortName),
            colors = listOf(route.color),
            stops = ride.map(::js),
            rideMinutes = (ride.last().offsetFromStart - ride.first().offsetFromStart).toDouble(),
        )
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        JourneyManager.start(context, Journey(JourneyManager.newId(), js(ride.last()), listOf(leg), planPath = "/route/$routeId/$dir"))
    }

    Column(Modifier.fillMaxSize()) {
    SheetTopBar(barTitle, listState.pastHeader(), menu = menu)
    LazyColumn(Modifier.weight(1f), state = listState) {
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Emerald))
                            Text(stringResource(R.string.w_from, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) { share(context, "/route/$routeId/$dir") },
                ),
                Modifier.padding(bottom = 12.dp),
            )
        }
        item {
            if (pong) {
                RoutePong(route.shortName, route.color, onExit = { pong = false })
            } else {
                ChoiceRow(
                    listOf("0", "1"), dir, ::onDirection,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = vm::hasDirection,
                ) { d ->
                    Text(vm.terminal(d), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            itemsIndexed(stops, key = { idx, s -> "${s.stop.stopId}-$idx" }) { idx, stop ->
                val arrivals = current?.arrivals?.getOrNull(idx).orEmpty()
                val name = stop.stop.headsign.ifEmpty { "Stop ${idx + 1}" }
                val alerting = following?.followed?.let { it.stopId == stop.stop.stopId && it.routeId == routeId && it.tripId == tripId } == true
                Box(Modifier.cascade(idx, dir)) {
                    StopLine(
                        name = name,
                        mark = mark(idx, stop),
                        favorite = stop.stop.stopId in favoriteStops,
                        first = idx == 0,
                        last = idx == stops.lastIndex,
                        times = arrivals.map { (if (it.soon) soonLabel else formatMinutesFromNow(it.minutes, current!!.now, nowLabel)) to it.isLive },
                        trailingText = null,
                        alerting = alerting,
                        onClick = { nav.stop(stop.stop.stopId) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuFor = idx
                        },
                    )
                    DropdownMenu(expanded = menuFor == idx, onDismissRequest = { menuFor = null }, offset = DpOffset(44.dp, 0.dp)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (alerting) R.string.stop_menu_notify_off else R.string.stop_menu_notify)) },
                            leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                            onClick = {
                                menuFor = null
                                if (alerting) {
                                    FollowManager.stop(context)
                                } else {
                                    withNotifications {
                                        FollowManager.start(
                                            context,
                                            Followed(stop.stop.stopId, name, routeId, tripId, route.shortName, route.color, vm.terminal(dir)),
                                        )
                                    }
                                }
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
                    Text(stringResource(R.string.w_timetable_click_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                        color = if (isPastTab(t, today) && selectedTab != t) MaterialTheme.colorScheme.outline else Color.Unspecified,
                        maxLines = 1,
                    )
                    if (t == today) Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(LocalContentColor.current))
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
                                val label = if (frequency.minMinutes != 0 && frequency.maxMinutes != 0 && frequency.minMinutes != frequency.maxMinutes) {
                                    stringResource(R.string.w_frequency_every_range, "${frequency.minMinutes}", "${frequency.maxMinutes}")
                                } else {
                                    stringResource(R.string.w_frequency_every, "${if (frequency.minMinutes != 0) frequency.minMinutes else frequency.maxMinutes}")
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
            itemsIndexed(groups, key = { _, g -> "h-${g.hour}-${g.isNextDay}" }) { _, group ->
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
    }
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
    alerting: Boolean = false,
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
        Modifier.fillMaxWidth().background(rowTint).squishClickable(onLongClick = onLongClick, onClick = onClick).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp).height(40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 2.dp.toPx()
                drawLine(rail, androidx.compose.ui.geometry.Offset(x, if (first) size.height / 2 else 0f), androidx.compose.ui.geometry.Offset(x, if (last) size.height / 2 else size.height), w)
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
            Text(
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            group.hour,
            modifier = Modifier.width(32.dp).alignByBaseline(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (group.isNextDay) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        )
        FlowRow(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            chip.isPast -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = null,
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
    val line = routeColor(change.routeColor.ifEmpty { "#64748b" })
    Column(
        Modifier.fillMaxWidth().background(line.copy(alpha = 0.10f)).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Notifications, null, Modifier.size(20.dp).padding(top = 2.dp), tint = line)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row {
                    Text(changeTitle(change, context), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(" · ${formatChangeDate(change.detectedAt, locale, withYear = false)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(row.label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
