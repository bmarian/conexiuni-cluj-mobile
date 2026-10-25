package online.bus.bmarian.conexiuniclujmobile.ui.plan

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.max
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.follow.Reminder
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.journey.JourneyManager
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.PlanOption
import online.bus.bmarian.conexiuniclujmobile.logic.PlanRide
import online.bus.bmarian.conexiuniclujmobile.logic.TimeMode
import online.bus.bmarian.conexiuniclujmobile.logic.formatDuration
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.LocalTimelineDraw
import online.bus.bmarian.conexiuniclujmobile.ui.Rail
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.TimelineDot
import online.bus.bmarian.conexiuniclujmobile.ui.TimelineDraw
import online.bus.bmarian.conexiuniclujmobile.ui.TimelineRow
import online.bus.bmarian.conexiuniclujmobile.ui.follow.ReminderLocation
import online.bus.bmarian.conexiuniclujmobile.ui.follow.rememberWithNotifications
import online.bus.bmarian.conexiuniclujmobile.ui.follow.reminderWhen
import online.bus.bmarian.conexiuniclujmobile.ui.liveColor
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapPin
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.rememberNow
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLiveColors
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import online.bus.bmarian.conexiuniclujmobile.ui.theme.lineColor
import online.bus.bmarian.conexiuniclujmobile.ui.timelineDraw
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
    val scope = rememberCoroutineScope()
    val swapMotion = remember { SwapMotion() }
    val draw = remember { TimelineDraw() }
    LaunchedEffect(selectedPlan?.key, calculating) {
        if (selectedPlan != null && !calculating) draw.play()
    }
    val permitted = UserLocation.permitted(context)
    val hasOrigin = origin != null || me != null
    val gpsResolving = permitted && me == null && origin == null && !timedOut

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
    val reminders by Reminders.all.collectAsStateWithLifecycle()
    val uiLocale = LocalConfiguration.current.locales[0]

    fun startJourney(plan: PlanOption) {
        val journey = vm.journeyFor(plan) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        withNotifications { JourneyManager.start(context, journey) }
    }

    fun remind(reminder: Reminder) {
        withNotifications {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            Reminders.put(context, reminder)
            ReminderLocation.offer(context)
            val title = resources.getString(R.string.remind_saved)
            if (Reminders.exactAllowed(context)) {
                Services.settings.showToast(title, reminder.next()?.let { reminderWhen(resources, it, uiLocale) })
            } else {
                Services.settings.showToast(title, resources.getString(R.string.remind_exact), resources.getString(R.string.remind_allow)) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                }
            }
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
                val startable = selectedPlan?.takeIf { !calculating && mode == TimeMode.Now && it.rides.isNotEmpty() && it.runs.isNotEmpty() }
                val remindable = selectedPlan?.takeIf { !calculating && mode != TimeMode.Now }
                val draft = remember(remindable, clockMs) { remindable?.let(vm::reminderFor)?.takeIf { it.next(clockMs) != null } }
                val existing = draft?.let { d -> reminders.firstOrNull { it.journey?.planPath == d.journey?.planPath && it.date == d.date && it.time == d.time } }
                ActionChips(
                    listOfNotNull(
                        startable?.let { plan ->
                            ChipAction(stringResource(R.string.journey_start), Icons.Rounded.Navigation, primary = true) { startJourney(plan) }
                        },
                        draft?.let { d ->
                            ChipAction(
                                stringResource(if (existing != null) R.string.remind_saved else R.string.remind_set),
                                Icons.Rounded.Alarm,
                                selected = existing != null,
                            ) { if (existing != null) Reminders.remove(context, existing.id) else remind(d) }
                        },
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
            item { TripSummary(selectedPlan, mode, clockMs) }
        }

        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                CompositionLocalProvider(LocalTimelineDraw provides draw) {
                Column(Modifier.padding(vertical = 8.dp).timelineDraw(draw)) {
                    EndpointRow(
                        dot = MaterialTheme.colorScheme.tertiary,
                        top = null,
                        bottom = if (selectedPlan != null) Rail.Walk else null,
                        label = stringResource(R.string.w_plan_from),
                        value = origin?.name ?: if (me != null) stringResource(R.string.w_plan_origin_current_location) else stringResource(R.string.w_plan_origin_unknown),
                        enabled = true,
                        searching = activeSearch == SearchField.Origin,
                        valueModifier = with(swapMotion) { Modifier.fromLabel() },
                        onOpen = { vm.openSearch(SearchField.Origin) },
                    )
                    if (activeSearch == SearchField.Origin) PlaceSearch(vm, SearchField.Origin, me != null, online)

                    if (selectedPlan != null) {
                        PlanLegs(vm, selectedPlan, expanded.value, ::toggleExpanded)
                    }

                    EndpointRow(
                        dot = MaterialTheme.colorScheme.primary,
                        top = if (selectedPlan != null) Rail.Walk else null,
                        bottom = null,
                        label = stringResource(R.string.w_plan_to),
                        value = destName.ifEmpty { "—" },
                        enabled = online,
                        searching = activeSearch == SearchField.Destination,
                        valueModifier = with(swapMotion) { Modifier.toLabel() },
                        onOpen = { vm.openSearch(SearchField.Destination) },
                    )
                    if (activeSearch == SearchField.Destination) PlaceSearch(vm, SearchField.Destination, me != null, online)
                }
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
                spin = swapMotion.spin.value,
                onSwap = {
                    scope.launch {
                        launch { swapMotion.turn() }
                        val apply = vm.prepareSwap() ?: return@launch
                        swapMotion.swap(apply)
                    }
                },
                onRefresh = vm::refreshRoutes,
            )
        }

        when {
            gpsResolving -> item { LoadingRow(text = stringResource(R.string.w_plan_locating)) }
            calculating -> item { LoadingRow(text = stringResource(R.string.w_plan_calculating)) }
            keyedPlans.isNotEmpty() -> itemsIndexed(keyedPlans, key = { _, keyed -> keyed.first }, contentType = { _, _ -> "plan" }) { index, (_, plan) ->
                Box(Modifier.animateItem()) {
                    PlanCard(vm, plan, destName, index == selected, mode, now, clockMs, expanded.value, ::toggleExpanded) { vm.selectPlanAt(index) }
                }
            }
            else -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        stringResource(R.string.w_plan_no_results),
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
private fun NotFound(onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.DirectionsBus, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.w_not_found), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.w_not_found_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(shapes = ButtonDefaults.shapes(), onClick = onHome) { Text(stringResource(R.string.w_home)) }
    }
}

@Composable
private fun LiveDot() {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(liveColor()))
}

@Composable
private fun TripSummary(plan: PlanOption, mode: TimeMode, clockMs: Long) {
    val nowLabel = stringResource(R.string.w_now)
    val now = remember(clockMs) { LocalDateTime.ofInstant(Instant.ofEpochMilli(clockMs), CLUJ_ZONE) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            val stats = mutableListOf<@Composable () -> Unit>()
            val run = plan.runs.firstOrNull()
            if (mode == TimeMode.Now && run != null) {
                val total = max(0.0, (run.arrive * 1000 - clockMs) / 60_000.0)
                stats += { Stat(formatDuration(total), stringResource(R.string.w_plan_trip_total)) }
            }
            plan.firstRide?.let { first ->
                stats += {
                    Stat(
                        (if (first.live) "" else "~ ") + (if (mode == TimeMode.Now) formatMinutesFromNow(minutesUntil(first.depart, clockMs), now, nowLabel) else clock(first.depart * 1000)),
                        stringResource(if (mode == TimeMode.Last) R.string.w_plan_last_departure else R.string.w_plan_next_departures),
                        live = first.live,
                    )
                }
            }
            if (plan.transfers > 0) {
                stats += { Stat("${plan.transfers}", stringResource(if (plan.transfers == 1) R.string.w_plan_change else R.string.w_plan_changes)) }
            }
            stats.forEachIndexed { i, stat ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { stat() }
            }
        }
    }
}

private fun minutesUntil(at: Long, clockMs: Long): Int = Math.round((at * 1000 - clockMs) / 60_000.0).toInt()

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
private fun EndpointRow(dot: Color, top: Rail?, bottom: Rail?, label: String, value: String, enabled: Boolean, searching: Boolean, valueModifier: Modifier = Modifier, onOpen: () -> Unit) {
    if (searching) return
    TimelineRow(
        top = top,
        bottom = bottom,
        dot = TimelineDot.Station,
        color = dot,
        modifier = Modifier.squishClickable(enabled = enabled, onClick = onOpen).alpha(if (enabled) 1f else 0.5f),
        trailing = { Icon(Icons.Rounded.Edit, null, Modifier.padding(8.dp).size(16.dp), tint = MaterialTheme.colorScheme.outline) },
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, valueModifier, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).squishClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanLegs(vm: PlanViewModel, plan: PlanOption, expanded: Set<String>, toggle: (String) -> Unit) {
    val nav = LocalNavigator.current
    val rides = plan.rides
    if (rides.isEmpty()) {
        TimelineRow(top = Rail.Walk, bottom = Rail.Walk, dot = TimelineDot.None, color = MaterialTheme.colorScheme.outline) {
            Text(stringResource(R.string.w_plan_walk).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(plan.minutes), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val apart = vm.linesApart(plan)
    var ride = 0
    plan.legs.forEachIndexed { i, leg ->
        if (!leg.isRide) return@forEachIndexed
        val legIdx = ride++
        val trips = plan.legTrips(legIdx)
        val color = vm.line(leg.lines.firstOrNull()).let { lineColor(it?.shortName, it?.color, apart) }
        val key = "detail-${plan.key}-$legIdx"
        val open = key in expanded
        TimelineRow(top = Rail.Walk, bottom = Rail.Ride(color), dot = TimelineDot.Station, color = color) {
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text("${stringResource(R.string.w_plan_boarding).uppercase()} ", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                val shown = if (open) leg.lines else leg.lines.take(4)
                shown.forEachIndexed { rIdx, routeId ->
                    Text(
                        vm.line(routeId)?.shortName.orEmpty(),
                        Modifier.clickable { nav.route(routeId, if (trips[routeId]?.endsWith("_1") == true) "1" else "0") },
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Black,
                    )
                    if (rIdx < shown.lastIndex) Text(" / ", style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (leg.lines.size > 4) {
                    Text(
                        if (open) " «" else " +${leg.lines.size - 4}",
                        Modifier.clickable { toggle(key) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val rideMin = max(0, Math.round(leg.seconds / 60.0).toInt())
                if (rideMin > 0) Text(" · $rideMin min ${stringResource(R.string.w_plan_ride_time)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StopLink(vm.stopName(leg.from), bold = true) { nav.stop(leg.from) }
        }
        for (id in leg.passed) {
            TimelineRow(top = Rail.Ride(color), bottom = Rail.Ride(color), dot = TimelineDot.Stop, color = color, compact = true) {
                StopLink(vm.stopName(id), bold = false) { nav.stop(id) }
            }
        }
        TimelineRow(top = Rail.Ride(color), bottom = Rail.Walk, dot = TimelineDot.Station, color = color) {
            Text(stringResource(R.string.w_plan_alighting).uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            StopLink(vm.stopName(leg.to), bold = true) { nav.stop(leg.to) }
        }
        if (legIdx != rides.lastIndex) {
            val walk = plan.legs.getOrNull(i + 1)?.takeIf { !it.isRide }
            TimelineRow(top = Rail.Walk, bottom = Rail.Walk, dot = TimelineDot.None, color = color, compact = true) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Amber.copy(alpha = 0.12f)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(18.dp), tint = Palette.Amber)
                    Text(stringResource(R.string.w_plan_transfer), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (walk != null) "${Math.round(walk.meters)} m ${stringResource(R.string.w_plan_transfer_walk)}" else stringResource(R.string.w_plan_transfer_same_stop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    spin: Float,
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onSearch, enabled = !calculating && value.isNotEmpty() && hasOrigin) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.w_plan_time_search_aria), Modifier.size(18.dp))
                    Text(stringResource(R.string.w_plan_time_search), Modifier.padding(start = 4.dp))
                }
            }
            if (canSwap) {
                FilledTonalIconButton(onClick = onSwap, enabled = !calculating) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.w_plan_swap), Modifier.graphicsLayer { rotationZ = spin })
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
    plan: PlanOption,
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
    val first = plan.firstRide
    val rides = plan.rides
    val press = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).squish(press),
        interactionSource = press,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (rides.isEmpty()) {
                        Chip(stringResource(R.string.w_plan_walk), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                    }
                    rides.forEachIndexed { lIdx, leg ->
                        val key = "list-${plan.key}-$lIdx"
                        val open = key in expanded
                        val names = (if (open) leg.lines else leg.lines.take(3)).joinToString("/") { vm.line(it)?.shortName.orEmpty() }
                        Chip(names, vm.line(leg.lines.firstOrNull()).let { lineColor(it?.shortName, it?.color, vm.linesApart(plan)) }, Color.White)
                        if (leg.lines.size > 3) {
                            Text(
                                if (open) "«" else "+${leg.lines.size - 3}",
                                Modifier.clickable { toggle(key) }.padding(horizontal = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (lIdx < rides.lastIndex) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                if (plan.runs.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (first != null) {
                            val color = if (first.live) liveColor() else MaterialTheme.colorScheme.onSurface
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (first.live) LiveDot()
                                Text(relativeDeparture(first, mode, clockMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                        Text(arrivalTime(plan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Text(
                "→ " + if (rides.isNotEmpty()) vm.stopName(rides.last().to) else destName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                StatChip(Icons.Rounded.Schedule, formatDuration(Math.round(plan.minutes).toDouble()))
                if (plan.transfers > 0) {
                    StatChip(Icons.Rounded.SwapVert, "${plan.transfers} ${stringResource(if (plan.transfers == 1) R.string.w_plan_change else R.string.w_plan_changes)}")
                }
                if (first?.live == true) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(LocalLiveColors.current.container).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LiveDot()
                        Text(stringResource(R.string.w_live), style = MaterialTheme.typography.labelSmall, color = LocalLiveColors.current.onContainer, fontWeight = FontWeight.Bold)
                    }
                }
                val later = plan.laterRides
                if (later.isNotEmpty()) {
                    Text(
                        stringResource(if (mode == TimeMode.Last) R.string.w_plan_earlier else R.string.w_plan_then) + " " + later.joinToString("  ") {
                            (if (it.live) "" else "~ ") + (if (mode == TimeMode.Now) formatMinutesFromNow(minutesUntil(it.depart, clockMs), now, nowLabel) else clock(it.depart * 1000))
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
private fun relativeDeparture(ride: PlanRide, mode: TimeMode, clockMs: Long): String {
    val approx = if (ride.live) "" else "~"
    if (mode != TimeMode.Now) return stringResource(R.string.w_plan_at_time, approx + clock(ride.depart * 1000))
    val rounded = minutesUntil(ride.depart, clockMs)
    if (rounded <= 0) return stringResource(R.string.w_now)
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(clockMs), CLUJ_ZONE)
    val part = formatMinutesFromNow(rounded, now, "")
    return if (rounded < 60) stringResource(R.string.w_plan_in_time, approx + part) else stringResource(R.string.w_plan_at_time, approx + part)
}

@Composable
private fun arrivalTime(plan: PlanOption): String {
    val run = plan.runs.firstOrNull() ?: return ""
    val approx = if (plan.firstRide?.live == true) "" else "~"
    return stringResource(R.string.w_plan_arrival_at, approx + clock(run.arrive * 1000))
}
