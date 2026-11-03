package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.RecentGroup
import online.bus.bmarian.conexiuniclujmobile.follow.clockOf
import online.bus.bmarian.conexiuniclujmobile.logic.ChainLeg
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.Leg
import online.bus.bmarian.conexiuniclujmobile.logic.LegTimes
import online.bus.bmarian.conexiuniclujmobile.logic.chainOf
import online.bus.bmarian.conexiuniclujmobile.logic.chainText
import online.bus.bmarian.conexiuniclujmobile.logic.groupKey
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalLiveBeat
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MenuAction
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.StationLine
import online.bus.bmarian.conexiuniclujmobile.ui.TimePill
import online.bus.bmarian.conexiuniclujmobile.ui.minutesLabel
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.stop.BusCard
import online.bus.bmarian.conexiuniclujmobile.ui.stop.DeparturesLabel
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

@Composable
fun groupViewModel(raw: String): GroupViewModel = viewModel(key = "group-$raw") { GroupViewModel(raw) }

fun groupPath(index: NetworkIndex, lines: List<GroupLine>) = "/grup?linii=" + index.writeLines(lines)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChain(chain: List<ChainLeg>, modifier: Modifier = Modifier, small: Boolean = false) {
    val looks = LocalLineLooks.current
    val joint = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEachIndexed { i, leg ->
            if (i > 0 && leg.change) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.group_then), Modifier.size(if (small) 14.dp else 20.dp), tint = joint)
            } else if (i > 0) {
                Text("+", color = joint, fontSize = if (small) 12.sp else 18.sp, fontWeight = FontWeight.Medium)
            }
            leg.lines.forEachIndexed { j, line ->
                if (j > 0) Text("/", color = joint, fontSize = if (small) 12.sp else 18.sp, fontWeight = FontWeight.Medium)
                if (small) {
                    StationLine(line.name, looks.color(line.name, line.color, apart = true))
                } else {
                    LineBadge(line.name, looks.hex(line.name, line.color, apart = true) ?: line.color, height = 36.dp, minWidth = 44.dp, corner = 12.dp)
                }
            }
        }
    }
}

@Composable
fun GroupScreen(raw: String) {
    val vm = groupViewModel(raw)
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.live() } }

    val index by vm.index.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val legs by vm.legs.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val layer by vm.mapLayer.collectAsStateWithLifecycle()
    val beat by vm.beat.collectAsStateWithLifecycle()
    val saved by Services.groups.saved.collectAsStateWithLifecycle()
    val key = groupKey(lines)
    val group = saved.firstOrNull { it.key == key }

    LaunchedEffect(key) {
        if (lines.isEmpty()) return@LaunchedEffect
        Services.groups.recordUse("groups", key)
        Services.favorites.addRecent(RecentGroup(lines))
    }

    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        onDispose { map.release(owner) }
    }
    LaunchedEffect(layer) {
        val known = layer ?: return@LaunchedEffect
        map.showShapes(known.shapes)
        map.vehicles = known.vehicles
        map.highlightedStops = known.stops
    }

    val chain = remember(index, legs) { index?.chainOf(legs).orEmpty() }
    val path = index?.let { groupPath(it, lines) }
    var naming by remember { mutableStateOf(false) }

    fun directions() {
        val known = index ?: return
        val to = group?.destination
        val query = if (to != null) PlanQuery.to(to.lat, to.lon, to.name) else PlanQuery()
        nav.plan(query.copy(linii = known.writeLines(lines)))
    }

    val listState = rememberLazyListState()
    CompositionLocalProvider(LocalLiveBeat provides beat) {
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(
            group?.name ?: chainText(chain),
            listState.pastHeader(),
            menu = if (group != null) listOf(MenuAction(stringResource(R.string.group_rename), Icons.Rounded.Edit) { naming = true }) else emptyList(),
        )
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "head") {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.group_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    group?.name?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    LineChain(chain)
                }
            }
            item(key = "actions") {
                ActionChips(
                    listOf(
                        ChipAction(stringResource(R.string.directions), Icons.Rounded.Directions, primary = true, onClick = ::directions),
                        ChipAction(
                            stringResource(if (group != null) R.string.saved else R.string.save),
                            if (group != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            selected = group != null,
                            tint = if (group != null) Palette.Rose else Color.Unspecified,
                        ) { if (group != null) Services.groups.remove(lines) else naming = true },
                        ChipAction(stringResource(R.string.group_lines), Icons.Rounded.Edit) {
                            index?.let { val written = it.writeLines(lines); nav.groupPick(written, edit = written.takeIf { group != null }, replace = true) }
                        },
                        ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) { path?.let { share(context, it) } },
                    ),
                    Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "label") { DeparturesLabel(hint = false) }
            val known = index
            val current = ui
            val times = current?.times
            if (known != null && current != null && times != null && times.size == legs.size) {
                legs.forEachIndexed { i, leg ->
                    if (leg.alight != null) {
                        item(key = "change-$i") { ChangeRow(known.stop(leg.lines.first().stopId)?.name.orEmpty(), times[i].arrival) }
                    }
                    item(key = "leg-$i") {
                        LegCard(known, leg, times[i], current) {
                            val first = leg.lines.first()
                            if (leg.lines.size > 1) nav.stop(first.stopId) else nav.route(first.routeId, first.direction, fromStopId = first.stopId)
                        }
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
    }

    if (naming) {
        GroupNameDialog(chain, group?.name, onDismiss = { naming = false }) { name ->
            Services.groups.save(lines, name)
            naming = false
        }
    }
}

@Composable
private fun ChangeRow(stopName: String, arrival: Long?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(72.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
        }
        Text(stringResource(R.string.group_change, stopName), style = MaterialTheme.typography.titleSmall)
        arrival?.let {
            Text(
                clockOf(it),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegCard(index: NetworkIndex, leg: Leg, times: LegTimes, ui: GroupUi, onClick: () -> Unit) {
    val looks = LocalLineLooks.current
    val routes = leg.lines.mapNotNull { index.route(it.routeId) }
    val live = times.shown.firstOrNull()?.live == true
    val mixed = routes.size > 1
    val destination = destinationOf(index, leg)
    fun minutes(at: Long) = ((at - ui.nowMs) / 60_000.0).roundToInt().coerceAtLeast(0)
    fun lineOf(routeId: Int) = routes.firstOrNull { it.routeId == routeId }
    BusCard(
        live = live,
        destination = destination,
        subtitle = stringResource(R.string.w_from, index.stop(leg.lines.first().stopId)?.name.orEmpty()),
        onClick = onClick,
        badge = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (route in routes) {
                    LineBadge(
                        route.shortName, route.color,
                        height = if (mixed) 28.dp else 36.dp, minWidth = 44.dp, corner = if (mixed) 9.dp else 12.dp,
                    )
                }
            }
        },
    ) {
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            fun badge(routeId: Int) = lineOf(routeId)?.takeIf { mixed }?.let { it.shortName to looks.color(it.shortName, it.color, apart = true) }
            times.missed?.let { m ->
                TimePill(minutesLabel(minutes(m.at), ui.now), live = false, line = badge(m.routeId), missed = true)
            }
            times.shown.forEachIndexed { i, d ->
                val label = minutesLabel(minutes(d.at), ui.now)
                TimePill(
                    text = if (i == 0 && !d.live) "~ $label" else label,
                    live = i == 0 && d.live,
                    extra = i > 0,
                    due = i == 0 && d.live && minutes(d.at) == 0,
                    mode = modeOf(lineOf(d.routeId)?.type ?: 3),
                    line = badge(d.routeId),
                )
            }
            if (times.shown.isEmpty() && times.missed == null) {
                Text(stringResource(R.string.group_no_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GroupNameDialog(chain: List<ChainLeg>, name: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LineChain(chain, small = true)
                TextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.group_name)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.w_cancel)) } },
    )
}

private fun destinationOf(index: NetworkIndex, leg: Leg) = leg.lines.mapNotNull { l ->
    index.route(l.routeId)?.longName?.let { directedDestination(it, l.direction) }
}.distinct().joinToString(" / ")
