package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadges
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.home.StationRow

@Composable
fun StopsScreen(stopIds: List<Int>) {
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val stops by Services.repository.stops.collectAsStateWithLifecycle()
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        onDispose { map.release(owner) }
    }
    val stations = remember(stops, index, stopIds) {
        val byId = stops.associateBy { it.stopId }
        stopIds.mapNotNull(byId::get).map { stop -> stop to index?.let { stationDestinations(it, stop.stopId) }.orEmpty() }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SheetTopBar(title = null, showTitle = false) }
        items(stations, key = { it.first.stopId }) { (stop, destinations) ->
            StationRow(stop, index?.look(stop.stopId), distance = null, lines = { Towards(destinations) }) { nav.mapStop(stop.stopId) }
        }
    }
}

@Composable
private fun Towards(destinations: List<Destination>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (to in destinations) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineBadges(to.routes)
                Text(
                    to.name?.let { "→ $it" } ?: stringResource(R.string.line_ends_here),
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
