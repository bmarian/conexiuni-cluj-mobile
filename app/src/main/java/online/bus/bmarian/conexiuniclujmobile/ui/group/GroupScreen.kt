package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentGroup
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.groupKey
import online.bus.bmarian.conexiuniclujmobile.logic.groupLabel
import online.bus.bmarian.conexiuniclujmobile.logic.groupRoutes
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadges
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalLiveBeat
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MenuAction
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.share
import online.bus.bmarian.conexiuniclujmobile.ui.stop.BusCard
import online.bus.bmarian.conexiuniclujmobile.ui.stop.DepartureTimes
import online.bus.bmarian.conexiuniclujmobile.ui.stop.DeparturesLabel
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

@Composable
fun groupViewModel(raw: String): GroupViewModel = viewModel(key = "group-$raw") { GroupViewModel(raw, createSavedStateHandle()) }

fun groupPath(index: NetworkIndex, lines: List<GroupLine>) = "/grup?linii=" + index.writeLines(lines)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupScreen(raw: String) {
    val vm = groupViewModel(raw)
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.live() } }

    val index by vm.index.collectAsStateWithLifecycle()
    val read by vm.lines.collectAsStateWithLifecycle()
    val lines = read.orEmpty()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val beat by vm.beat.collectAsStateWithLifecycle()
    val layer by vm.mapLayer.collectAsStateWithLifecycle()
    val saved by Services.groups.saved.collectAsStateWithLifecycle()
    val key = groupKey(lines)
    val group = saved.firstOrNull { it.key == key }

    LaunchedEffect(key) { if (lines.isNotEmpty()) Services.favorites.addRecent(RecentGroup(lines)) }

    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        map.linesApart = false
        map.lineEnds = true
        onDispose { map.release(owner) }
    }
    LaunchedEffect(layer) {
        val known = layer ?: return@LaunchedEffect
        map.showShapes(known.shapes)
        map.vehicles = known.vehicles
        map.highlightedStops = known.boarding.associateWith { StopHighlight.Green }
    }

    val routes = remember(index, lines) { index?.groupRoutes(lines).orEmpty() }
    val path = index?.let { groupPath(it, lines) }
    var naming by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    CompositionLocalProvider(LocalLiveBeat provides beat) {
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(
            group?.name ?: groupLabel(routes),
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (route in routes) LineBadge(route.shortName, route.color, height = 36.dp, minWidth = 44.dp, corner = 12.dp)
                    }
                }
            }
            item(key = "actions") {
                val edit = ChipAction(stringResource(R.string.group_lines), Icons.Rounded.Edit) {
                    index?.let { val written = it.writeLines(lines); nav.groupPick(written, edit = written.takeIf { group != null }, replace = true) }
                }
                ActionChips(
                    if (read?.isEmpty() == true) listOf(edit) else listOf(
                        ChipAction(
                            stringResource(if (group != null) R.string.saved else R.string.save),
                            if (group != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            selected = group != null,
                            tint = if (group != null) Palette.Rose else Color.Unspecified,
                        ) { if (group != null) Services.groups.remove(lines) else naming = true },
                        edit,
                        ChipAction(stringResource(R.string.w_share), Icons.Rounded.Share) { path?.let { share(context, it) } },
                    ),
                    Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "label") { DeparturesLabel(hint = false) }
            when {
                read == null -> item(key = "loading") { LoadingRow() }
                lines.isEmpty() -> item(key = "none") {
                    Text(stringResource(R.string.w_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(lines, key = { it.routeId }) { line ->
                val route = index?.route(line.routeId) ?: return@items
                val row = ui?.rows?.firstOrNull { it.line == line }
                GroupLineCard(
                    route, line, row?.stopId?.let { index?.stop(it)?.name }, row, ui?.now, line.routeId in hidden,
                    onFlip = { vm.flip(line.routeId) },
                    onToggle = { vm.toggleHidden(line.routeId) },
                ) { nav.route(line.routeId, line.direction, fromStopId = row?.stopId) }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
    }

    if (naming) {
        GroupNameDialog(routes, group?.name, onDismiss = { naming = false }) { name ->
            Services.groups.save(lines, name)
            naming = false
        }
    }
}

@Composable
private fun GroupLineCard(
    route: NetworkRoute,
    line: GroupLine,
    from: String?,
    row: GroupRow?,
    now: LocalDateTime?,
    hidden: Boolean,
    onFlip: () -> Unit,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val departure = row?.departure
    BusCard(
        live = departure?.isStatic == false,
        shortName = route.shortName,
        color = route.color,
        destination = directedDestination(route.longName, line.direction),
        subtitle = from?.let { stringResource(R.string.w_from, it) }.orEmpty(),
        onClick = onClick,
        modifier = Modifier.alpha(if (hidden) 0.55f else 1f),
        trailing = {
            IconButton(onClick = onFlip) {
                Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.group_flip), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (hidden) R.string.group_show_line else R.string.group_hide_line),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (departure != null && now != null) {
            DepartureTimes(departure, now, modeOf(route.type))
        } else if (row != null) {
            Text(
                stringResource(R.string.group_no_more),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GroupNameDialog(routes: List<NetworkRoute>, name: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LineBadges(routes)
                TextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.group_name)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.w_cancel)) } },
    )
}
