package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentRoute
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords
import online.bus.bmarian.conexiuniclujmobile.logic.Call
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.byModeThenNumber
import online.bus.bmarian.conexiuniclujmobile.logic.callsByStation
import online.bus.bmarian.conexiuniclujmobile.logic.lineStops
import online.bus.bmarian.conexiuniclujmobile.logic.lineUse
import online.bus.bmarian.conexiuniclujmobile.logic.matching
import online.bus.bmarian.conexiuniclujmobile.logic.opposite
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.usageOrder
import online.bus.bmarian.conexiuniclujmobile.logic.viaStops
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.home.RESULTS_PER_KIND
import online.bus.bmarian.conexiuniclujmobile.ui.home.ResultRow
import online.bus.bmarian.conexiuniclujmobile.ui.home.SearchField
import online.bus.bmarian.conexiuniclujmobile.ui.home.linesByMode
import online.bus.bmarian.conexiuniclujmobile.ui.home.norm
import online.bus.bmarian.conexiuniclujmobile.ui.modeLines
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination

private class Through(val stop: Stop, val calls: List<Call>, val rank: Int)

/** Port of views/GroupAddView.vue. */
@Composable
fun GroupAddScreen(raw: String, edit: String?) {
    val nav = LocalNavigator.current
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val favorites by Services.favorites.routes.collectAsStateWithLifecycle()
    val recents by Services.favorites.recents.collectAsStateWithLifecycle()
    val lines = remember(index, raw) { index?.readLines(raw).orEmpty() }
    GroupMap(lines)
    var query by rememberSaveable { mutableStateOf("") }
    val known = index ?: return

    // Read once so the lists don't reorder under the finger.
    val lineUsage = remember { Services.groups.lineUsage() }
    val nowMs = remember { System.currentTimeMillis() }

    val byMode = remember(known) { linesByMode(known) }
    val routes = remember(byMode) { byMode.flatMap { it.second } }
    val calls = remember(known, routes) { known.callsByStation(routes) }
    val vias = remember(calls) { routes.associate { it.routeId to known.viaStops(it, calls) } }

    val yours = remember(routes, favorites, recents) {
        val liked = favorites.map { it.routeId }
        val byUse = compareBy(usageOrder(nowMs)) { r: NetworkRoute -> lineUsage.lineUse(r.routeId) }.thenBy { if (it.routeId in liked) 0 else 1 }
        val ids = lineUsage.keys.mapNotNull { it.substringBefore('/').toIntOrNull() } + liked + recents.filterIsInstance<RecentRoute>().map { it.routeId }
        routes.filter { it.routeId in ids }.sortedWith(byUse.then(byModeThenNumber({ it.type }, { it.shortName })))
    }

    val asked = norm(query.trim())
    val found = remember(asked, routes) {
        if (asked.isEmpty()) emptyList() else matching(routes, { norm("${it.shortName} ${it.longName}") }, asked).sortedBy { it.rank }
    }
    val through = remember(asked, calls) {
        if (asked.isEmpty()) return@remember emptyList()
        val ranked = matching(known.stops, { norm(it.name) }, asked).sortedBy { it.rank }
        val rankOf = ranked.associate { it.item.stopId to it.rank }
        known.oneStopPerStation(ranked.map { it.item })
            .mapNotNull { stop -> calls[known.stationId(stop.stopId)]?.let { Through(stop, it, rankOf.getValue(stop.stopId)) } }
            .take(RESULTS_PER_KIND)
    }

    fun directionFor(routeId: Int): String {
        val (u0, u1) = (lineUsage["$routeId/0"]?.total ?: 0) to (lineUsage["$routeId/1"]?.total ?: 0)
        val liked = if (u0 != u1) (if (u1 > u0) "1" else "0") else favorites.firstOrNull { it.routeId == routeId }?.direction ?: "0"
        fun runs(direction: String) = known.lineStops(GroupLine(routeId, direction)).isNotEmpty()
        return if (runs(liked) || !runs(opposite(liked))) liked else opposite(liked)
    }

    fun added(routeId: Int) = lines.firstOrNull { it.routeId == routeId }

    fun pick(routeId: Int, direction: String? = null, from: Int? = null) {
        val inGroup = added(routeId)
        if (inGroup == null && Services.favorites.atCap(lines, SyncRecords.GROUP_LINES_CAP)) return
        nav.groupStops(raw, edit, known.writeLines(listOf(inGroup ?: GroupLine(routeId, direction ?: directionFor(routeId), from))))
    }

    fun LazyListScope.lineRows(key: String, label: @Composable () -> String, list: List<NetworkRoute>) {
        item(key = key) { SubLabel(label(), Modifier.padding(top = 8.dp)) }
        items(list, key = { "$key-${it.routeId}" }) { route ->
            val via = vias[route.routeId].orEmpty().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.group_via, it.joinToString(", ")) }
            LineRow(route, route.longName, via, added(route.routeId) != null) { pick(route.routeId) }
        }
    }

    fun LazyListScope.stationRows() {
        for (station in through) {
            val key = "station-${station.stop.stopId}"
            item(key = key) { SubLabel(stringResource(R.string.group_through, station.stop.name), Modifier.padding(top = 8.dp)) }
            items(station.calls, key = { "$key-${it.route.routeId}-${it.direction}" }) { call ->
                val title = "→ ${directedDestination(call.route.longName, call.direction)}"
                val from = known.stop(call.stopId)?.name?.let { stringResource(R.string.w_from, it) }
                LineRow(call.route, title, from, added(call.route.routeId) != null) { pick(call.route.routeId, call.direction, call.stopId) }
            }
        }
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.group_add_line), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "title") { GroupTitle(stringResource(R.string.group_add_line)) }
            item(key = "search") {
                SearchField(query, { query = it }, stringResource(R.string.group_search), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (asked.isEmpty()) {
                if (yours.isNotEmpty()) lineRows("yours", { stringResource(R.string.group_yours) }, yours)
                for ((mode, list) in byMode) lineRows("mode-$mode", { stringResource(modeLines(mode)) }, list)
            } else {
                // A station named outright leads lines that only have the letters somewhere.
                val stationsLead = through.isNotEmpty() && (found.isEmpty() || through.first().rank < found.first().rank)
                if (stationsLead) stationRows()
                if (found.isNotEmpty()) lineRows("found", { stringResource(R.string.group_lines) }, found.map { it.item })
                if (!stationsLead) stationRows()
                if (found.isEmpty() && through.isEmpty()) item(key = "none") { NoResults() }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NoResults() {
    Text(stringResource(R.string.w_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LineRow(route: NetworkRoute, title: String, subtitle: String?, inGroup: Boolean, onClick: () -> Unit) {
    ResultRow(
        icon = { LineBadge(route.shortName, route.color, height = 32.dp, minWidth = 40.dp, corner = 10.dp) },
        title = title,
        distance = stringResource(R.string.group_in_group).takeIf { inGroup },
        subtitle = subtitle,
        onClick = onClick,
    )
}
