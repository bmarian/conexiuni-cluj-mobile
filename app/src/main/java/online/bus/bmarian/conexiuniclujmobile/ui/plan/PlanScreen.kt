package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.journey.LeaveReminder
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.PlannedTimeEntry
import online.bus.bmarian.conexiuniclujmobile.logic.Planner
import online.bus.bmarian.conexiuniclujmobile.logic.RichPlan
import online.bus.bmarian.conexiuniclujmobile.logic.TimeMode
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.follow.rememberWithNotifications
import online.bus.bmarian.conexiuniclujmobile.ui.liveColor
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapPin
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.rememberNow
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLiveColors
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import online.bus.bmarian.conexiuniclujmobile.ui.withUniqueKeys

@Composable
fun planViewModel(query: PlanQuery): PlanViewModel {
    val app = LocalContext.current.applicationContext as android.app.Application
    return viewModel(key = "plan-${query.encode()}") { PlanViewModel(app, query) }
}

private typealias PlanQuery = online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery

private fun clock(ms: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), CLUJ_ZONE)
    return "%02d:%02d".format(t.hour, t.minute)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(query: PlanQuery) {
    val vm = planViewModel(query)
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.live() } }

    val destName by vm.destName.collectAsStateWithLifecycle()
    val destLatText by vm.destLatText.collectAsStateWithLifecycle()
    val destLonText by vm.destLonText.collectAsStateWithLifecycle()
    val origin by vm.customOrigin.collectAsStateWithLifecycle()
    val mode by vm.timeMode.collectAsStateWithLifecycle()
    val timeValue by vm.timeValue.collectAsStateWithLifecycle()
    val calculating by vm.isCalculating.collectAsStateWithLifecycle()
    val planData by vm.planData.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val selected by vm.selectedIndex.collectAsStateWithLifecycle()
    val liveEta by vm.selectedLiveEtaMin.collectAsStateWithLifecycle()
    val layer by vm.mapLayer.collectAsStateWithLifecycle()
    val vehicles by vm.mapVehicles.collectAsStateWithLifecycle()
    val fits by vm.fitRequests.collectAsStateWithLifecycle()
    val timedOut by vm.locatingTimedOut.collectAsStateWithLifecycle()
    val activeSearch by vm.activeSearch.collectAsStateWithLifecycle()
    val me by Services.location.collectAsStateWithLifecycle()
    val online by Services.online.collectAsStateWithLifecycle()
    val favoritePlans by Services.favorites.plans.collectAsStateWithLifecycle()
    val clockMs by Services.clock.collectAsStateWithLifecycle()
    val now = rememberNow()

    val keyedPlans = remember(plans) { withUniqueKeys(plans) { it.key } }

    val validCoords = destLatText?.toDoubleOrNull() != null && destLonText?.toDoubleOrNull() != null
    val selectedPlan = plans.getOrNull(selected)
    val permitted = UserLocation.permitted(context)
    val hasOrigin = origin != null || me != null
    val gpsResolving = permitted && me == null && origin == null && !timedOut

    // The pins, the selected plan's routes, walks, stops and buses on the map.
    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        map.onPinDragged = vm::onDestinationDragged
        map.onOriginDragged = vm::onOriginDragged
        onDispose { map.release(owner) }
    }
    LaunchedEffect(destLatText, destLonText, destName) {
        val lat = destLatText?.toDoubleOrNull()
        val lon = destLonText?.toDoubleOrNull()
        map.pinnedLocation = if (lat != null && lon != null) MapPin(lat, lon, destName) else null
    }
    LaunchedEffect(origin) { map.customOrigin = origin?.let { MapPin(it.lat, it.lon, it.name) } }
    // Fit once per new search; coming back to the screen or rotating doesn't fit again.
    var fitsHandled by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(fits) {
        if (fits > fitsHandled) {
            fitsHandled = fits
            map.fitWalking = true
        }
    }
    LaunchedEffect(layer) {
        map.walkingPolylines = layer.walks
        map.highlightedStops = layer.highlights
        map.showShapes(layer.shapes)
    }
    LaunchedEffect(vehicles) { map.vehicles = vehicles }

    if (!validCoords && activeSearch != SearchField.Destination) {
        Column(Modifier.fillMaxSize()) {
            SheetTopBar(null, false)
            NotFound(onHome = nav::home)
        }
        return
    }

    if (activeSearch != null && validCoords) BackHandler { vm.closeSearch() }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    val isFavorite = vm.isFavorite(favoritePlans)
    val favoriteLabel = vm.favoriteLabel(favoritePlans)
    val expanded = remember { mutableStateOf(emptySet<String>()) }
    fun toggleExpanded(key: String) {
        expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
    }

    val withNotifications = rememberWithNotifications()
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current

    // Maps' Start: the plan becomes a step-by-step journey, on the watch too.
    fun startJourney(plan: RichPlan) {
        val journey = vm.journeyFor(plan) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        withNotifications { JourneyManager.start(context, journey) }
    }

    val planPath = "/plan?" + vm.currentQuery().encode()
    var reminding by rememberSaveable { mutableStateOf(false) }
    // The reminder set for this plan, shown as a chip that cancels it.
    var pending by remember { mutableStateOf(LeaveReminder.pending(context)) }
    val routes by Services.repository.routes.collectAsStateWithLifecycle()
    val routesById = remember(routes) { routes.associateBy { it.routeId } }

    fun remindToLeave(plan: RichPlan, departIn: Double, leadMinutes: Int) {
        val leaveIn = departIn - plan.walkStartMeters / WALK_SPEED - 1 - leadMinutes
        val nowMs = System.currentTimeMillis()
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val departAt = Instant.ofEpochMilli(nowMs + (departIn * 60_000).toLong()).atZone(CLUJ_ZONE)
        if (leaveIn < 1) {
            Services.settings.showToast(resources.getString(R.string.remind_too_late, resources.getString(R.string.eta_minutes, departIn.roundToInt().coerceAtLeast(0))))
            return
        }
        val atMs = nowMs + (leaveIn * 60_000).toLong()
        val leg = plan.legs.first()
        val lines = leg.routeIds.joinToString(" / ") { id -> routesById[id]?.shortName ?: "$id" }
        val body = resources.getString(R.string.remind_leave_now_body, lines, vm.stopName(leg.startStopId), clock.format(departAt))
        withNotifications {
            LeaveReminder.schedule(context, atMs, body, planPath)
            pending = LeaveReminder.pending(context)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val title = resources.getString(R.string.remind_set, clock.format(Instant.ofEpochMilli(atMs).atZone(CLUJ_ZONE)))
            if (LeaveReminder.canBeExact(context)) {
                Services.settings.showToast(title, body, resources.getString(R.string.w_cancel)) {
                    LeaveReminder.cancel(context)
                    pending = null
                }
            } else {
                // Without exact alarms Android may run it late; once allowed, it is set again exactly.
                Services.settings.showToast(title, resources.getString(R.string.remind_inexact), resources.getString(R.string.remind_allow_exact)) {
                    LeaveReminder.askExact(context)
                }
            }
        }
    }

    if (reminding && selectedPlan != null) {
        RemindSheet(
            plan = selectedPlan,
            firstLive = liveEta.takeIf { mode == TimeMode.Now },
            routesById = routesById,
            onDismiss = { reminding = false },
        ) { departIn, lead ->
            reminding = false
            remindToLeave(selectedPlan, departIn, lead)
        }
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
    SheetTopBar(favoriteLabel ?: destName.ifEmpty { null }, listState.pastHeader())
    LazyColumn(Modifier.weight(1f), state = listState) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Map, null, tint = MaterialTheme.colorScheme.onTertiary)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.w_plan_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    if (renaming) {
                        TextField(
                            value = renameValue,
                            onValueChange = { renameValue = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.w_rename_favorite_placeholder)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                vm.rename(renameValue)
                                renaming = false
                            }),
                        )
                    } else {
                        Text(
                            favoriteLabel ?: destName.ifEmpty { stringResource(R.string.w_plan_title_generic) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (renaming) {
                    IconButton(onClick = {
                        vm.rename(renameValue)
                        renaming = false
                    }) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.w_rename_favorite)) }
                    IconButton(onClick = { renaming = false }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_cancel)) }
                }
            }
        }
        if (!renaming && validCoords) {
            item(key = "actions") {
                ActionChips(
                    listOfNotNull(
                        ChipAction(
                            stringResource(if (isFavorite) R.string.saved else R.string.save),
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            selected = isFavorite,
                            tint = if (isFavorite) Palette.Rose else Color.Unspecified,
                        ) { if (online) vm.toggleFavorite() },
                        ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) { share(context, "/plan?" + vm.currentQuery().encode()) },
                        if (online && isFavorite) {
                            ChipAction(stringResource(R.string.w_rename_favorite), Icons.Rounded.Edit) {
                                renameValue = favoriteLabel.orEmpty()
                                renaming = true
                            }
                        } else null,
                    ),
                    Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (selectedPlan != null && !calculating) {
            item { TripSummary(selectedPlan, mode, liveEta, clockMs) }
            if (selectedPlan.legs.isNotEmpty() && selectedPlan.nextTimes.isNotEmpty()) {
                item {
                    TripActions(
                        canStart = mode == TimeMode.Now,
                        onStart = { startJourney(selectedPlan) },
                        onRemind = { reminding = true },
                    )
                }
                pending?.takeIf { it.second == planPath }?.let { (at, _) ->
                    item(key = "reminder") {
                        InputChip(
                            selected = true,
                            onClick = {
                                LeaveReminder.cancel(context)
                                pending = null
                            },
                            label = { Text(stringResource(R.string.remind_pending, DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(at).atZone(CLUJ_ZONE)))) },
                            leadingIcon = { Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_cancel), Modifier.size(18.dp)) },
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        )
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    EndpointRow(
                        dot = MaterialTheme.colorScheme.tertiary,
                        label = stringResource(R.string.w_plan_from),
                        value = origin?.name ?: if (me != null) stringResource(R.string.w_plan_origin_current_location) else stringResource(R.string.w_plan_origin_unknown),
                        enabled = true,
                        searching = activeSearch == SearchField.Origin,
                        onOpen = { vm.openSearch(SearchField.Origin) },
                    )
                    if (activeSearch == SearchField.Origin) PlaceSearch(vm, SearchField.Origin, me != null, online)

                    if (selectedPlan != null) {
                        PlanLegs(vm, selectedPlan, planData?.shapes.orEmpty(), expanded.value, ::toggleExpanded)
                    }

                    EndpointRow(
                        dot = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.w_plan_to),
                        value = destName.ifEmpty { "—" },
                        enabled = online,
                        searching = activeSearch == SearchField.Destination,
                        onOpen = { vm.openSearch(SearchField.Destination) },
                    )
                    if (activeSearch == SearchField.Destination) PlaceSearch(vm, SearchField.Destination, me != null, online)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                Text(stringResource(R.string.w_plan_routes_label), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            TimeFilter(
                mode = mode,
                value = timeValue,
                calculating = calculating,
                canSwap = validCoords && hasOrigin,
                canRefresh = validCoords,
                hasOrigin = hasOrigin,
                minMs = clockMs,
                onMode = vm::setTimeMode,
                onValue = vm::setTimeValue,
                onSearch = vm::refreshRoutes,
                onSwap = vm::swap,
                onRefresh = vm::refreshRoutes,
            )
        }

        when {
            gpsResolving -> item { LoadingRow(text = stringResource(R.string.w_plan_locating)) }
            calculating -> item { LoadingRow(text = stringResource(R.string.w_plan_calculating)) }
            keyedPlans.isNotEmpty() -> itemsIndexed(keyedPlans, key = { _, keyed -> keyed.first }, contentType = { _, _ -> "plan" }) { index, (_, plan) ->
                Box(Modifier.animateItem()) {
                    PlanCard(vm, plan, planData?.shapes.orEmpty(), destName, index == selected, mode, now, clockMs, expanded.value, ::toggleExpanded) { vm.selectPlanAt(index) }
                }
            }
            else -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val none = planData?.plans?.isNotEmpty() == true
                    Icon(if (none) Icons.Rounded.Schedule else Icons.Rounded.DirectionsBus, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(if (none) R.string.w_plan_no_buses_next_hour else R.string.w_plan_no_results),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    }
}

@Composable
private fun TripActions(canStart: Boolean, onStart: () -> Unit, onRemind: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canStart) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp))
                Text(stringResource(R.string.journey_start), Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = onRemind, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp))
            Text(stringResource(R.string.remind_leave), Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NotFound(onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.w_not_found), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.w_not_found_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onHome) { Text(stringResource(R.string.w_home)) }
    }
}

@Composable
private fun LiveDot() {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(liveColor()))
}

@Composable
private fun TripSummary(plan: RichPlan, mode: TimeMode, liveEta: Double?, clockMs: Long) {
    val nowLabel = stringResource(R.string.w_now)
    val now = remember(clockMs) { LocalDateTime.ofInstant(Instant.ofEpochMilli(clockMs), CLUJ_ZONE) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val stats = mutableListOf<@Composable () -> Unit>()
            if (mode == TimeMode.Now) {
                val total = Planner.computeTotalMinutes(plan).let { if (it.isFinite()) it else 0.0 }
                stats += { Stat(Planner.formatDuration(total), stringResource(R.string.w_plan_trip_total)) }
            }
            plan.nextTimes.firstOrNull()?.let { first ->
                val live = liveEta != null
                val minutes = liveEta ?: first.minutes
                val prefix = if (live || first.isLive) "" else "~ "
                stats += {
                    Stat(
                        prefix + formatMinutesFromNow(Math.round(minutes).toInt(), now, nowLabel),
                        stringResource(R.string.w_plan_next_departures),
                        live = live,
                    )
                }
            }
            if (!plan.isDirect) {
                val changes = plan.legs.size - 1
                stats += { Stat("$changes", stringResource(if (changes == 1) R.string.w_plan_change else R.string.w_plan_changes)) }
            }
            stats.forEachIndexed { i, stat ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { stat() }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, live: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (live) LiveDot()
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (live) liveColor() else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun EndpointRow(dot: Color, label: String, value: String, enabled: Boolean, searching: Boolean, onOpen: () -> Unit) {
    if (searching) return
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).alpha(if (enabled) 1f else 0.5f).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(dot).border(3.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun PlaceSearch(vm: PlanViewModel, field: SearchField, hasLocation: Boolean, online: Boolean) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching by vm.isSearching.collectAsStateWithLifecycle()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.searchQuery.value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            enabled = field == SearchField.Origin || online,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { IconButton(onClick = vm::closeSearch) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_cancel)) } },
            placeholder = {
                Text(stringResource(if (field == SearchField.Origin) R.string.w_plan_search_placeholder else R.string.w_plan_dest_search_placeholder), maxLines = 1)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchNow() }),
            shape = RoundedCornerShape(14.dp),
        )
        if (searching) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.w_plan_searching), style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        if (hasLocation) {
            SearchResultRow(Icons.Rounded.MyLocation, stringResource(R.string.w_plan_origin_current_location), MaterialTheme.colorScheme.primary) {
                if (field == SearchField.Origin) vm.useCurrentLocation() else vm.useCurrentLocationAsDestination()
            }
        }
        for (place in results) {
            SearchResultRow(Icons.Rounded.Place, place.label, MaterialTheme.colorScheme.tertiary) {
                if (field == SearchField.Origin) vm.selectOrigin(place) else vm.selectDestination(place)
            }
        }
        if (field == SearchField.Destination && online && results.isEmpty() && query.trim().length >= 3) {
            SearchResultRow(Icons.Rounded.PushPin, stringResource(R.string.w_drop_pin), MaterialTheme.colorScheme.onSurfaceVariant) { vm.dropPinAsDestination() }
        }
    }
}

@Composable
private fun SearchResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The legs of the selected plan: boarding, the stops ridden past, alighting, the change between. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanLegs(vm: PlanViewModel, plan: RichPlan, shapes: Map<String, online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo>, expanded: Set<String>, toggle: (String) -> Unit) {
    val nav = LocalNavigator.current
    if (plan.legs.isEmpty()) {
        LegRow(color = MaterialTheme.colorScheme.outline, dashed = true, small = true) {
            Text(stringResource(R.string.w_plan_walk).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Planner.formatDuration(Planner.getJourneyDuration(plan)), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val planner = vm.planner()
    plan.legs.forEachIndexed { legIdx, leg ->
        val shape = shapes["${leg.routeIds.firstOrNull()}"]
        val color = routeColor(shape?.color)
        val key = "detail-${plan.key}-$legIdx"
        val open = key in expanded
        LegRow(color = color, dashed = false) {
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text("${stringResource(R.string.w_plan_boarding).uppercase()} ", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                val shown = if (open) leg.routeIds else leg.routeIds.take(4)
                shown.forEachIndexed { rIdx, routeId ->
                    Text(
                        shapes["$routeId"]?.shortName.orEmpty(),
                        Modifier.clickable { nav.route(routeId, if (leg.tripIds.getOrNull(rIdx)?.endsWith("_1") == true) "1" else "0") },
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Black,
                    )
                    if (rIdx < shown.lastIndex) Text(" / ", style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (leg.routeIds.size > 4) {
                    Text(
                        if (open) " «" else " +${leg.routeIds.size - 4}",
                        Modifier.clickable { toggle(key) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val ride = max(0, Math.round(leg.rideSeconds / 60).toInt())
                if (ride > 0) Text(" · $ride min ${stringResource(R.string.w_plan_ride_time)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StopLink(vm.stopName(leg.startStopId), bold = true) { nav.stop(leg.startStopId) }
        }
        for (id in leg.intermediateStopIds) {
            LegRow(color = color, dashed = false, small = true) {
                StopLink(vm.stopName(id), bold = false) { nav.stop(id) }
            }
        }
        val last = legIdx == plan.legs.lastIndex
        LegRow(color = color, dashed = last, lineVisible = last) {
            Text(stringResource(R.string.w_plan_alighting).uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            StopLink(vm.stopName(leg.destStopId), bold = true) { nav.stop(leg.destStopId) }
        }
        if (!last) {
            val meters = planner.getTransferWalkMeters(plan, legIdx)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Palette.Amber.copy(alpha = 0.12f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(18.dp), tint = Palette.Amber)
                Text(stringResource(R.string.w_plan_transfer), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (meters > 25) "${Math.round(meters)} m ${stringResource(R.string.w_plan_transfer_walk)}" else stringResource(R.string.w_plan_transfer_same_stop),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StopLink(name: String, bold: Boolean, onClick: () -> Unit) {
    Text(
        name,
        Modifier.clickable(onClick = onClick),
        style = if (bold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LegRow(color: Color, dashed: Boolean, small: Boolean = false, lineVisible: Boolean = true, content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Box(Modifier.width(20.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val top = size.height / 2
                if (lineVisible) {
                    drawLine(
                        if (dashed) rail else color,
                        Offset(x, top),
                        Offset(x, size.height),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
                    )
                }
                drawLine(if (dashed && small) rail else color, Offset(x, 0f), Offset(x, top), strokeWidth = 3.dp.toPx())
                if (small) {
                    drawCircle(surface, 4.dp.toPx(), Offset(x, top))
                    drawCircle(rail, 4.dp.toPx(), Offset(x, top), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                } else {
                    drawCircle(surface, 7.dp.toPx(), Offset(x, top))
                    drawCircle(color, 7.dp.toPx(), Offset(x, top), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                    drawCircle(color, 3.dp.toPx(), Offset(x, top))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, top = if (small) 4.dp else 8.dp, bottom = if (small) 4.dp else 8.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFilter(
    mode: TimeMode,
    value: String,
    calculating: Boolean,
    canSwap: Boolean,
    canRefresh: Boolean,
    hasOrigin: Boolean,
    minMs: Long,
    onMode: (TimeMode) -> Unit,
    onValue: (String) -> Unit,
    onSearch: () -> Unit,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(
        TimeMode.Now to stringResource(R.string.w_plan_time_leave_now),
        TimeMode.Leave to stringResource(R.string.w_plan_time_leave_at),
        TimeMode.Arrive to stringResource(R.string.w_plan_time_arrive_by),
        TimeMode.Last to stringResource(R.string.w_plan_time_last),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = labels.getValue(mode),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.w_plan_time_filter_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (m in TimeMode.entries) {
                        DropdownMenuItem(text = { Text(labels.getValue(m)) }, onClick = {
                            open = false
                            onMode(m)
                        })
                    }
                }
            }
            if (mode == TimeMode.Leave || mode == TimeMode.Arrive) {
                FilledTonalButton(onClick = onSearch, enabled = !calculating && value.isNotEmpty() && hasOrigin) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.w_plan_time_search_aria), Modifier.size(18.dp))
                    Text(stringResource(R.string.w_plan_time_search), Modifier.padding(start = 4.dp))
                }
            }
            if (canSwap) {
                FilledTonalIconButton(onClick = onSwap, enabled = !calculating) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.w_plan_swap))
                }
            }
            if (canRefresh) {
                FilledTonalIconButton(onClick = onRefresh, enabled = !calculating && hasOrigin) {
                    if (calculating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.w_plan_refresh))
                }
            }
        }
        if (mode == TimeMode.Leave || mode == TimeMode.Arrive) {
            DateTimeField(value, minMs, if (mode == TimeMode.Arrive) labels.getValue(TimeMode.Arrive) else labels.getValue(TimeMode.Leave), onValue)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    vm: PlanViewModel,
    plan: RichPlan,
    shapes: Map<String, online.bus.bmarian.conexiuniclujmobile.data.ShapeInfo>,
    destName: String,
    selected: Boolean,
    mode: TimeMode,
    now: LocalDateTime,
    clockMs: Long,
    expanded: Set<String>,
    toggle: (String) -> Unit,
    onClick: () -> Unit,
) {
    val nowLabel = stringResource(R.string.w_now)
    val first = plan.nextTimes.firstOrNull()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (plan.legs.isEmpty()) {
                        Chip(stringResource(R.string.w_plan_walk), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                    }
                    plan.legs.forEachIndexed { lIdx, leg ->
                        val key = "list-${plan.key}-$lIdx"
                        val open = key in expanded
                        val names = (if (open) leg.routeIds else leg.routeIds.take(3)).joinToString("/") { shapes["$it"]?.shortName.orEmpty() }
                        Chip(names, routeColor(shapes["${leg.routeIds.firstOrNull()}"]?.color), Color.White)
                        if (leg.routeIds.size > 3) {
                            Text(
                                if (open) "«" else "+${leg.routeIds.size - 3}",
                                Modifier.clickable { toggle(key) }.padding(horizontal = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (lIdx < plan.legs.lastIndex) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                if (first != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val color = if (first.isLive) liveColor() else MaterialTheme.colorScheme.onSurface
                        if (plan.legs.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (first.isLive) LiveDot()
                                Text(relativeDeparture(first, mode, clockMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                        Text(arrivalTime(plan, first, clockMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Text(
                "→ " + if (plan.legs.isNotEmpty()) vm.stopName(plan.legs.last().destStopId) else destName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                StatChip(Icons.Rounded.Schedule, Planner.formatDuration(Math.round(Planner.getJourneyDuration(plan)).toDouble()))
                if (!plan.isDirect) {
                    val changes = plan.legs.size - 1
                    StatChip(Icons.Rounded.SwapVert, "$changes ${stringResource(if (changes == 1) R.string.w_plan_change else R.string.w_plan_changes)}")
                }
                if (plan.isLive) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(LocalLiveColors.current.container).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LiveDot()
                        Text(stringResource(R.string.w_live), style = MaterialTheme.typography.labelSmall, color = LocalLiveColors.current.onContainer, fontWeight = FontWeight.Bold)
                    }
                }
                if (plan.nextTimes.size > 1) {
                    Text(
                        stringResource(R.string.w_plan_then) + " " + plan.nextTimes.drop(1).take(2).joinToString("  ") {
                            (if (it.isLive) "" else "~ ") + formatMinutesFromNow(Math.round(it.minutes).toInt(), now, nowLabel)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = content)
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun relativeDeparture(entry: PlannedTimeEntry, mode: TimeMode, clockMs: Long): String {
    val approx = if (entry.isLive) "" else "~"
    val departure = clockMs + (entry.minutes * 60_000).toLong()
    if (mode != TimeMode.Now) return stringResource(R.string.w_plan_at_time, approx + clock(departure))
    val wait = entry.minutes
    val rounded = Math.round(wait).toInt()
    if (rounded <= 0) return stringResource(R.string.w_now)
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(clockMs), CLUJ_ZONE)
    val part = formatMinutesFromNow(rounded, now, "")
    return if (rounded < 60) stringResource(R.string.w_plan_in_time, approx + part) else stringResource(R.string.w_plan_at_time, approx + part)
}

@Composable
private fun arrivalTime(plan: RichPlan, entry: PlannedTimeEntry, clockMs: Long): String {
    val approx = if (entry.isLive) "" else "~"
    val duration = Planner.getJourneyDuration(plan)
    val departure = clockMs + entry.minutes * 60_000
    val arrival = if (plan.legs.isEmpty()) departure + duration * 60_000
    else departure + (duration - plan.walkStartMeters / WALK_SPEED) * 60_000
    return stringResource(R.string.w_plan_arrival_at, approx + clock(arrival.toLong()))
}

private val REMIND_LEADS = listOf(0, 2, 5, 10)

/**
 * "Remind me": which of the next buses to catch and how early to hear about it, each with the
 * time to leave by, counting the walk to the stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindSheet(
    plan: RichPlan,
    firstLive: Double?,
    routesById: Map<Int, Route>,
    onDismiss: () -> Unit,
    onConfirm: (departIn: Double, leadMinutes: Int) -> Unit,
) {
    val walk = plan.walkStartMeters / WALK_SPEED + 1
    val nowMs = remember { System.currentTimeMillis() }
    val options = remember(plan, firstLive) {
        plan.nextTimes.take(3)
            .mapIndexed { i, t -> if (i == 0 && firstLive != null) t.copy(minutes = firstLive, isLive = true) else t }
            .filter { it.minutes - walk >= 0.5 }
    }
    var choice by rememberSaveable { mutableIntStateOf(0) }
    var lead by rememberSaveable { mutableIntStateOf(0) }
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun at(minutes: Double) = clock.format(Instant.ofEpochMilli(nowMs + (minutes * 60_000).toLong()).atZone(CLUJ_ZONE))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.remind_title), style = MaterialTheme.typography.titleLarge)
            if (options.isEmpty()) {
                Text(stringResource(R.string.remind_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            options.forEachIndexed { i, time ->
                val route = routesById[time.routeId ?: plan.legs.first().routeIds.first()]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .selectable(selected = choice == i, onClick = { choice = i })
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RadioButton(selected = choice == i, onClick = null)
                    route?.let { LineBadge(it.shortName, it.color, height = 28.dp, minWidth = 36.dp, corner = 8.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.remind_leave_by, at(time.minutes - walk)), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.remind_bus_at, at(time.minutes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (time.isLive) LiveDot()
                }
            }
            Text(stringResource(R.string.remind_how_early), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                REMIND_LEADS.forEachIndexed { i, minutes ->
                    SegmentedButton(
                        selected = lead == minutes,
                        onClick = { lead = minutes },
                        shape = SegmentedButtonDefaults.itemShape(i, REMIND_LEADS.size),
                    ) { Text(if (minutes == 0) stringResource(R.string.remind_on_time) else stringResource(R.string.eta_minutes, minutes), maxLines = 1) }
                }
            }
            Button(
                onClick = { onConfirm(options[choice.coerceIn(0, options.lastIndex)].minutes, lead) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remind_confirm))
            }
        }
    }
}
