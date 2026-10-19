package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo
import online.bus.bmarian.conexiuniclujmobile.data.Shortcuts
import online.bus.bmarian.conexiuniclujmobile.data.directionFromTripId
import online.bus.bmarian.conexiuniclujmobile.data.tripIdFor
import online.bus.bmarian.conexiuniclujmobile.follow.FollowManager
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.follow.justNotify
import online.bus.bmarian.conexiuniclujmobile.follow.nextBusReminder
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.tripIdForRouteAtStop
import online.bus.bmarian.conexiuniclujmobile.logic.walkMinutes
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MenuAction
import online.bus.bmarian.conexiuniclujmobile.ui.MessageText
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.PopIcon
import online.bus.bmarian.conexiuniclujmobile.ui.RealtimeIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SectionLabel
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.TimePill
import online.bus.bmarian.conexiuniclujmobile.ui.badgeKey
import online.bus.bmarian.conexiuniclujmobile.ui.cascade
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderSheet
import online.bus.bmarian.conexiuniclujmobile.ui.follow.openOnWatch
import online.bus.bmarian.conexiuniclujmobile.ui.follow.rememberWithNotifications
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderWhen
import online.bus.bmarian.conexiuniclujmobile.ui.liveColor
import online.bus.bmarian.conexiuniclujmobile.ui.minutesLabel
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.route.RouteHandoff
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.sharedBadge
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLiveColors
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import online.bus.bmarian.conexiuniclujmobile.ui.withUniqueKeys
import online.bus.bmarian.conexiuniclujmobile.widget.WidgetPinning

private const val FRAME_WAIT_MS = 2_000L

@Composable
fun stopViewModel(stopId: Int): StopViewModel = viewModel(key = "stop-$stopId") { StopViewModel(stopId) }

fun routeDestination(name: String): String {
    val i = name.lastIndexOf(" - ")
    return if (i >= 0) name.substring(i + 3) else name
}

fun routeOrigin(name: String): String {
    val i = name.lastIndexOf(" - ")
    return if (i >= 0) name.substring(0, i) else ""
}

@Composable
fun StopScreen(stopId: Int) {
    val vm = stopViewModel(stopId)
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.live() } }

    val name by vm.name.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val routesHere by vm.routesHere.collectAsStateWithLifecycle()
    val layer by vm.mapLayer.collectAsStateWithLifecycle()
    val favoriteStops by Services.favorites.stops.collectAsStateWithLifecycle()
    val favoriteRoutes by Services.favorites.routes.collectAsStateWithLifecycle()
    val isFavorite = stopId in favoriteStops
    val follows by FollowManager.follows.collectAsStateWithLifecycle()
    val reminders by Reminders.all.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var reminding by remember { mutableStateOf<Followed?>(null) }
    val haptics = LocalHapticFeedback.current

    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        map.selectedStopId = stopId
        map.fitNewShapes = false
        onDispose { map.release(owner) }
    }
    var centred by rememberSaveable(stopId) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (centred) return@LaunchedEffect
        val stop = Services.repository.stops.first { it.isNotEmpty() }.firstOrNull { it.stopId == stopId } ?: return@LaunchedEffect
        val routes = if (!Services.settings.autoFitMap.value) null else withTimeoutOrNull(FRAME_WAIT_MS) {
            val ui = vm.ui.filterNotNull().first()
            if (ui.departures.none(vm::isDepartureFavorite)) null
            else vm.mapLayer.first { it.shapes.isNotEmpty() }.shapes
        }
        centred = true
        if (routes.isNullOrEmpty()) {
            map.flyTo(stop.lat, stop.lon)
        } else {
            map.showShapes(routes)
            map.fitShapes()
        }
    }
    LaunchedEffect(layer) {
        map.showShapes(layer.shapes)
        map.vehicles = layer.vehicles
    }

    val departures = remember(ui, favoriteRoutes) {
        ui?.departures?.sortedWith(compareBy({ if (vm.isDepartureFavorite(it)) 0 else 1 }, { it.minutesLeft }))
    }
    val keyedDepartures = remember(departures) { departures?.let { list -> withUniqueKeys(list) { it.shortName } } }

    fun openDeparture(departure: StopDeparture) {
        val shape = info?.shapesInfo?.find { it.routeId == departure.routeId } ?: return
        RouteHandoff.set(shape, departure.tripId, stopId, name.orEmpty())
        nav.route(departure.routeId, directionFromTripId(departure.tripId), fromStopId = stopId)
    }

    fun openRoute(shape: ShapeInfo) {
        val current = info
        val tripId = current?.let { tripIdForRouteAtStop(it.outgoingTripIds, it.incomingTripIds, shape.routeId) }
            ?: tripIdFor(shape.routeId, "0")
        RouteHandoff.set(shape, tripId, stopId, name.orEmpty())
        nav.route(shape.routeId, directionFromTripId(tripId), fromStopId = stopId)
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val withNotifications = rememberWithNotifications()

    // At the stop, or too late to walk there, it's told when the bus comes; otherwise reminded when to leave for it.
    fun remind(followed: Followed, departure: StopDeparture) = withNotifications {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        val stop = info
        val walk = Services.location.value?.let { fix -> stop?.let { walkMinutes(fix.lat, fix.lon, it.lat, it.lon) } }
        if (stop == null || justNotify(walk, departure.minutesLeft)) {
            FollowManager.start(context, followed)
        } else {
            // Outlives the screen, so leaving it doesn't drop the reminder.
            Services.scope.launch {
                val reminder = nextBusReminder(stop, followed)
                if (reminder == null) {
                    FollowManager.start(context, followed)
                } else {
                    Reminders.put(context, reminder)
                    val title = resources.getString(R.string.remind_saved)
                    if (Reminders.exactAllowed(context)) {
                        Services.settings.showToast(title, reminder.next()?.let { reminderWhen(resources, it, locale) })
                    } else {
                        // Without exact alarms the time to leave can come late.
                        Services.settings.showToast(title, resources.getString(R.string.remind_exact), resources.getString(R.string.remind_allow)) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                        }
                    }
                }
            }
        }
    }

    val watch by WearLink.watchInReach.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val allStops by Services.repository.stops.collectAsStateWithLifecycle()
    val here = remember(allStops, stopId) { allStops.find { it.stopId == stopId } }
    val menu = buildList {
        add(MenuAction(stringResource(R.string.add_to_home), Icons.Rounded.AddToHomeScreen) { Shortcuts.pinStop(context, stopId, name.orEmpty()) })
        if (WidgetPinning.supported(context)) {
            add(MenuAction(stringResource(R.string.add_widget), Icons.Rounded.Widgets) { WidgetPinning.pinStop(context, stopId, name.orEmpty()) })
        }
        if (watch) add(MenuAction(stringResource(R.string.open_on_watch), Icons.Rounded.Watch) { scope.launch { openOnWatch(context, "/stop/$stopId") } })
        add(MenuAction(stringResource(R.string.refresh), Icons.Rounded.Refresh) { vm.load() })
    }

    Column(Modifier.fillMaxSize()) {
    SheetTopBar(name, listState.pastHeader(), menu = menu)
    LazyColumn(Modifier.weight(1f), state = listState) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.w_bus_stop),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        info?.stopCode?.takeIf { it.isNotEmpty() }?.let {
                            Text("#$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Text(
                        name ?: "…",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item(key = "actions") {
            ActionChips(
                listOfNotNull(
                    here?.let { s ->
                        ChipAction(stringResource(R.string.directions), Icons.Rounded.Directions, primary = true) {
                            nav.plan(PlanQuery.to(s.lat, s.lon, s.name))
                        }
                    },
                    ChipAction(
                        stringResource(if (isFavorite) R.string.saved else R.string.save),
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        selected = isFavorite,
                        tint = if (isFavorite) Palette.Rose else Color.Unspecified,
                    ) {
                        haptics.performHapticFeedback(if (!isFavorite) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        Services.favorites.toggleStop(stopId)
                    },
                    ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) { share(context, "/stop/$stopId") },
                ),
                Modifier.padding(bottom = 8.dp),
            )
        }

        when {
            failed && info == null -> item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = vm::load) { Text(stringResource(R.string.retry)) }
                }
            }
            else -> {
                item { DeparturesLabel() }
                when {
                    departures == null -> items(3) { DepartureSkeleton() }
                    departures.isEmpty() -> item { MessageText(stringResource(R.string.w_no_schedule)) }
                    else -> itemsIndexed(keyedDepartures.orEmpty(), key = { _, keyed -> keyed.first }, contentType = { _, _ -> "departure" }) { index, (key, departure) ->
                        val followed = Followed(
                            stopId, name.orEmpty(), departure.routeId, departure.tripId,
                            departure.shortName, departure.color, routeDestination(departure.longName), Cities.current.value,
                        )
                        val following = follows.firstOrNull { it.matches(stopId, departure.routeId, departure.tripId) }
                        val forBus = reminders.filter { it.isFor(stopId, departure.routeId, departure.tripId) }
                        // The clock's own: a one-off for this bus, until it starts following.
                        val clocked = forBus.filter { it.once }
                        Box(Modifier.animateItem().cascade(index, stopId)) {
                            DepartureCard(
                                departure, ui!!.now, vm.isDepartureFavorite(departure),
                                remindOn = following != null || clocked.isNotEmpty(),
                                reminded = forBus.any { !it.once },
                                onRemind = {
                                    if (following == null && clocked.isEmpty()) {
                                        remind(followed, departure)
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                        following?.let { FollowManager.stop(context, it.id) }
                                        clocked.forEach { Reminders.remove(context, it.id) }
                                    }
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuFor = key
                                },
                            ) { openDeparture(departure) }
                            DropdownMenu(expanded = menuFor == key, onDismissRequest = { menuFor = null }, offset = DpOffset(64.dp, 0.dp)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remind_menu)) },
                                    leadingIcon = { Icon(Icons.Rounded.AlarmAdd, null) },
                                    onClick = {
                                        menuFor = null
                                        reminding = followed
                                    },
                                )
                            }
                        }
                    }
                }
                if (routesHere.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(R.string.w_all_routes_at_stop),
                            Modifier.padding(top = 16.dp),
                            icon = Icons.Rounded.Map,
                        )
                    }
                    items(routesHere, key = { "r-${it.routeId}" }) { shape ->
                        RouteRow(
                            shortName = shape.shortName,
                            color = shape.color,
                            name = shape.timetable.routeLongName,
                            favorite = Services.favorites.isRouteFavorite(shape.routeId),
                        ) { openRoute(shape) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    }
    reminding?.let { ReminderSheet(it) { reminding = null } }
}

@Composable
private fun DeparturesLabel() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(liveColor()))
        Text(
            stringResource(R.string.w_next_departures),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.departures_hold_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DepartureSkeleton() {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(64.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {}
}

@Composable
fun DepartureCard(
    departure: StopDeparture,
    now: java.time.LocalDateTime,
    favorite: Boolean,
    remindOn: Boolean = false,
    reminded: Boolean = false,
    onRemind: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val live = !departure.isStatic
    val press = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).squish(press)
            .clip(CardDefaults.shape)
            .combinedClickable(interactionSource = press, indication = ripple(), onLongClick = onLongClick, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (favorite) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (favorite) BorderStroke(1.dp, Palette.Rose.copy(alpha = 0.45f)) else null,
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (live) liveColor() else Color.Transparent),
            )
            LineBadge(
                departure.shortName, departure.color,
                Modifier.sharedBadge(badgeKey(departure.routeId, departure.tripId)),
                height = 36.dp, minWidth = 44.dp, corner = 12.dp,
            )
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (live) LiveBadge()
                    Text(
                        "→ ${routeDestination(departure.longName)}",
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (reminded) {
                        Icon(Icons.Rounded.Alarm, stringResource(R.string.remind_saved), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                val origin = routeOrigin(departure.longName)
                if (origin.isNotEmpty()) {
                    Text(
                        origin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    departure.nextTimes.forEachIndexed { i, time ->
                        val label = minutesLabel(time.minutes, now)
                        TimePill(
                            text = if (i == 0 && departure.isStatic) "~ $label" else label,
                            live = i == 0 && live,
                            extra = i > 0,
                        )
                    }
                }
            }
            if (onRemind != null) {
                IconToggleButton(checked = remindOn, onCheckedChange = { onRemind() }, shapes = IconButtonDefaults.toggleableShapes()) {
                    PopIcon(
                        if (remindOn) Icons.Rounded.AlarmOn else Icons.Rounded.AlarmAdd,
                        remindOn,
                        contentDescription = stringResource(if (remindOn) R.string.stop_following else R.string.remind_set),
                        tint = if (remindOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
fun LiveBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(LocalLiveColors.current.container).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RealtimeIcon(LocalLiveColors.current.onContainer, size = 9.dp)
        Text(
            stringResource(R.string.w_live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LocalLiveColors.current.onContainer,
        )
    }
}

@Composable
fun RouteRow(shortName: String, color: String, name: String, favorite: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .squishClickable(enabled = !busy, onClick = onClick)
            .background(if (favorite) Palette.Rose.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (busy) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(shortName, color, height = 28.dp, minWidth = 40.dp, corner = 6.dp)
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
