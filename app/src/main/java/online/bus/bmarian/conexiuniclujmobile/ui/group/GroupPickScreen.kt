package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentRoute
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.byModeThenNumber
import online.bus.bmarian.conexiuniclujmobile.logic.lineUse
import online.bus.bmarian.conexiuniclujmobile.logic.matching
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.usageOrder
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.home.SearchField
import online.bus.bmarian.conexiuniclujmobile.ui.home.linesByMode
import online.bus.bmarian.conexiuniclujmobile.ui.home.norm
import online.bus.bmarian.conexiuniclujmobile.ui.modeLines
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks

/** Port of views/GroupPickView.vue. */
@Composable
fun GroupPickScreen(raw: String, edit: String?) {
    val nav = LocalNavigator.current
    val looks = LocalLineLooks.current
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val favorites by Services.favorites.routes.collectAsStateWithLifecycle()
    val recents by Services.favorites.recents.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(index) {
        if (chosen == null) index?.let { i -> chosen = i.readLines(raw).map { "${it.routeId}/${it.direction}" } }
    }
    val lines = chosen.orEmpty().mapNotNull { key -> key.split('/').takeIf { it.size == 2 }?.let { (id, dir) -> id.toIntOrNull()?.let { GroupLine(it, dir) } } }
    val known = index ?: return

    // Read once so the lists don't reorder under the finger.
    val lineUsage = remember { Services.groups.lineUsage() }
    val nowMs = remember { System.currentTimeMillis() }
    fun usageOf(routeId: Int) = lineUsage.lineUse(routeId)
    val recentIds = recents.filterIsInstance<RecentRoute>().map { it.routeId }.toSet()
    fun isFavorite(routeId: Int) = favorites.any { it.routeId == routeId }
    val byUse = compareBy(usageOrder(nowMs)) { r: Int -> usageOf(r) }.thenBy { if (isFavorite(it)) 0 else 1 }

    val servable = remember(known) { linesByMode(known).flatMap { it.second } }
    val servableIds = servable.map { it.routeId }.toSet()
    fun isChosen(routeId: Int) = lines.any { it.routeId == routeId }
    val often = (lineUsage.keys.mapNotNull { it.substringBefore('/').toIntOrNull() } + favorites.map { it.routeId } + recentIds)
        .distinct().filter { it in servableIds }
        .mapNotNull(known::route)
        .sortedWith(compareBy(byUse) { r: NetworkRoute -> r.routeId }.then(byModeThenNumber({ it.type }, { it.shortName })))

    fun directionFor(routeId: Int): String {
        val (u0, u1) = (lineUsage["$routeId/0"]?.total ?: 0) to (lineUsage["$routeId/1"]?.total ?: 0)
        if (u0 != u1) return if (u1 > u0) "1" else "0"
        return favorites.firstOrNull { it.routeId == routeId }?.direction ?: "0"
    }

    fun toggle(routeId: Int) {
        chosen = if (isChosen(routeId)) chosen.orEmpty().filterNot { it.startsWith("$routeId/") }
        else if (Services.favorites.atCap(chosen.orEmpty(), SyncRecords.GROUP_LINES_CAP)) chosen
        else chosen.orEmpty() + "$routeId/${directionFor(routeId)}"
    }

    fun flip(routeId: Int) {
        chosen = chosen.orEmpty().map { if (it.startsWith("$routeId/")) "$routeId/${if (it.endsWith("/0")) "1" else "0"}" else it }
    }

    fun badgeColor(route: NetworkRoute) = looks.hex(route.shortName, route.color) ?: route.color

    fun show() {
        if (lines.isEmpty()) return
        edit?.let { Services.groups.replaceLines(known.readLines(it), lines) }
        nav.group(known.writeLines(lines), replace = true)
    }

    val found = remember(query, servable) {
        val q = norm(query.trim())
        if (q.isEmpty()) emptyList() else matching(servable, { norm("${it.shortName} ${it.longName}") }, q).sortedBy { it.rank }.map { it.item }
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.group_title), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "title") {
                Text(
                    stringResource(R.string.group_title),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            item(key = "search") {
                SearchField(query, { query = it }, stringResource(R.string.group_search), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (lines.isNotEmpty()) {
                item(key = "chosen-label") { SubLabel(stringResource(R.string.group_chosen), Modifier.padding(top = 8.dp)) }
                items(lines, key = { "chosen-${it.routeId}" }) { line ->
                    val route = known.route(line.routeId) ?: return@items
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LineBadge(route.shortName, badgeColor(route), height = 32.dp, minWidth = 40.dp, corner = 10.dp)
                        Text(
                            "→ ${directedDestination(route.longName, line.direction)}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { flip(line.routeId) }) {
                            Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.group_flip), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toggle(line.routeId) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.group_remove_line), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            if (query.isBlank()) {
                if (often.isNotEmpty()) {
                    item(key = "often") { SubLabel(stringResource(R.string.group_often), Modifier.padding(top = 8.dp)) }
                    items(often, key = { "often-${it.routeId}" }) { route ->
                        PickRow(route, badgeColor(route), isChosen(route.routeId)) { toggle(route.routeId) }
                    }
                }
                allLines(linesByMode(known), ::badgeColor, ::isChosen) { toggle(it.routeId) }
            } else {
                items(found, key = { "found-${it.routeId}" }) { route ->
                    PickRow(route, badgeColor(route), isChosen(route.routeId)) { toggle(route.routeId) }
                }
                if (found.isEmpty()) {
                    item(key = "none") { Text(stringResource(R.string.w_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(onClick = ::show, enabled = lines.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.group_show))
            }
        }
    }
}

private fun LazyListScope.allLines(
    groups: List<Pair<online.bus.bmarian.conexiuniclujmobile.logic.Mode, List<NetworkRoute>>>,
    color: (NetworkRoute) -> String,
    chosen: (Int) -> Boolean,
    onToggle: (NetworkRoute) -> Unit,
) {
    for ((mode, lines) in groups) {
        item(key = "mode-$mode") { SubLabel(stringResource(modeLines(mode)), Modifier.padding(top = 8.dp)) }
        items(lines, key = { "all-${it.routeId}" }) { route -> PickRow(route, color(route), chosen(route.routeId)) { onToggle(route) } }
    }
}

@Composable
private fun PickRow(route: NetworkRoute, color: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(route.shortName, color, height = 32.dp, minWidth = 40.dp, corner = 10.dp)
        Text(
            route.longName,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
