package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.Leg
import online.bus.bmarian.conexiuniclujmobile.logic.StopRole
import online.bus.bmarian.conexiuniclujmobile.logic.StopRow
import online.bus.bmarian.conexiuniclujmobile.logic.StretchEnd
import online.bus.bmarian.conexiuniclujmobile.logic.boardingStop
import online.bus.bmarian.conexiuniclujmobile.logic.flipLine
import online.bus.bmarian.conexiuniclujmobile.logic.lineStops
import online.bus.bmarian.conexiuniclujmobile.logic.on
import online.bus.bmarian.conexiuniclujmobile.logic.opposite
import online.bus.bmarian.conexiuniclujmobile.logic.pickStop
import online.bus.bmarian.conexiuniclujmobile.logic.readLegs
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.stopRows
import online.bus.bmarian.conexiuniclujmobile.logic.stretchNames
import online.bus.bmarian.conexiuniclujmobile.logic.with
import online.bus.bmarian.conexiuniclujmobile.logic.writeLegs
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.route.StopLine
import online.bus.bmarian.conexiuniclujmobile.ui.route.StopMark
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks

private val StopRow.painted: ClosedFloatingPointRange<Float>?
    get() = if (paintedTop || paintedBottom) (if (paintedTop) 0f else 0.5f)..(if (paintedBottom) 1f else 0.5f) else null

/** Port of views/GroupStopsView.vue. */
@Composable
fun GroupStopsScreen(raw: String, edit: String?, rawLine: String, leg: Leg) {
    val nav = LocalNavigator.current
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val fix by Services.location.collectAsStateWithLifecycle()
    val legs = remember(index, raw) { index?.readLegs(raw) }
    val lines = legs?.on(leg).orEmpty()
    var written by rememberSaveable { mutableStateOf(rawLine) }
    val line = remember(index, written) { index?.readLines(written)?.firstOrNull() }
    var chosenEnd by rememberSaveable { mutableStateOf<StretchEnd?>(null) }
    val end = chosenEnd ?: if (line?.from != null && line.to == null) StretchEnd.To else StretchEnd.From

    val others = lines.filter { it.routeId != line?.routeId }
    val nearest = if (line != null && line.from == null) fix?.let { index?.boardingStop(line, it.lat, it.lon) } else null

    var placed by rememberSaveable { mutableStateOf(false) }

    fun edited(next: GroupLine, nextEnd: StretchEnd) {
        written = index?.writeLines(listOf(next)) ?: return
        chosenEnd = nextEnd
        placed = true
    }

    fun tap(stopId: Int) {
        val (next, nextEnd) = line?.let { index?.pickStop(it, end, stopId) } ?: return
        edited(next, nextEnd)
    }

    StretchMap(line, others, nearest) { tap(it) }
    val known = index ?: return
    if (line == null) return
    val route = known.route(line.routeId) ?: return
    val isNew = others.size == lines.size
    val rows = remember(known, line, nearest) { known.stopRows(line, nearest) }
    val (from, to) = known.stretchNames(line)
    val lineColor = LocalLineLooks.current.color(route.shortName, route.color)

    val listState = rememberLazyListState()
    val boards = rows.indexOfFirst { !it.dimmed }
    LaunchedEffect(boards) {
        if (placed || boards <= 0) return@LaunchedEffect
        placed = true
        listState.scrollToItem(boards)
    }

    Column(Modifier.fillMaxSize()) {
        SheetTopBar(
            null,
            false,
            lead = {
                LineBadge(route.shortName, route.color, Modifier.padding(start = 4.dp), height = 32.dp, minWidth = 40.dp, corner = 10.dp)
                Text(
                    "→ ${directedDestination(route.longName, line.direction)}",
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                if (known.lineStops(GroupLine(line.routeId, opposite(line.direction))).isNotEmpty()) {
                    IconButton(onClick = { edited(known.flipLine(line), StretchEnd.From) }) {
                        Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.group_flip))
                    }
                }
            },
        )
        ChoiceRow(
            StretchEnd.entries, end, { chosenEnd = it },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            icon = { if (it == StretchEnd.From) Icons.Rounded.Place else Icons.Rounded.Flag },
        ) { side ->
            val label = if (side == StretchEnd.From) from ?: stringResource(R.string.group_board_at) else to ?: stringResource(R.string.group_leave_at)
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            itemsIndexed(rows, key = { at, row -> "$at-${row.stopId}" }) { at, row ->
                StopLine(
                    name = known.stop(row.stopId)?.name.orEmpty(),
                    mark = when (row.role) {
                        StopRole.Board -> StopMark.From
                        StopRole.Near -> StopMark.Nearest
                        StopRole.Leave -> StopMark.Terminal
                        StopRole.Pass -> if (at == rows.lastIndex) StopMark.Terminal else StopMark.Plain
                    },
                    favorite = false,
                    first = at == 0,
                    last = at == rows.lastIndex,
                    times = emptyList(),
                    trailingText = when (row.role) {
                        StopRole.Board -> stringResource(R.string.group_board_at)
                        StopRole.Near -> stringResource(R.string.group_near_you)
                        StopRole.Leave -> stringResource(R.string.group_leave_at)
                        StopRole.Pass -> null
                    },
                    paint = { row.painted },
                    paintColor = lineColor,
                    dimmed = row.dimmed,
                    onClick = { tap(row.stopId) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item { HiddenSpace() }
        }
        GroupBar {
            if (line.from != null || line.to != null) {
                TextButton(onClick = { edited(line.copy(from = null, to = null), StretchEnd.From) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.group_whole_line))
                }
            }
            Button(
                onClick = {
                    val next = if (isNew) lines + line else lines.map { if (it.routeId == line.routeId) line else it }
                    legs?.let { nav.groupBuilt(known.writeLegs(it.with(leg, next)), edit, leg) }
                },
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(if (isNew) R.string.group_add_to_group else R.string.done))
            }
        }
    }
}
