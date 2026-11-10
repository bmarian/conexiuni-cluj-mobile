package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import online.bus.bmarian.conexiuniclujmobile.logic.readLines
import online.bus.bmarian.conexiuniclujmobile.logic.stretchNames
import online.bus.bmarian.conexiuniclujmobile.logic.writeLines
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.stop.BusCard
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination

/** Port of views/GroupPickView.vue. */
@Composable
fun GroupPickScreen(raw: String, edit: String?) {
    val nav = LocalNavigator.current
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    var written by rememberSaveable { mutableStateOf(raw) }
    val lines = remember(index, written) { index?.readLines(written).orEmpty() }
    GroupMap(lines)
    val known = index ?: return

    fun show() {
        edit?.let { Services.groups.replaceLines(known.readLines(it), lines) }
        nav.group(known.writeLines(lines), replace = true)
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.group_title), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "title") { GroupTitle(stringResource(R.string.group_title)) }
            items(lines, key = { it.routeId }) { line ->
                val route = known.route(line.routeId) ?: return@items
                val (from, to) = known.stretchNames(line)
                BusCard(
                    live = false,
                    shortName = route.shortName,
                    color = route.color,
                    destination = directedDestination(route.longName, line.direction),
                    subtitle = stretchLabel(from, to),
                    onClick = { nav.groupStops(written, edit, known.writeLines(listOf(line))) },
                    trailing = {
                        IconButton(onClick = { written = known.writeLines(lines - line) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.group_remove_line), tint = MaterialTheme.colorScheme.outline)
                        }
                    },
                ) {}
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        GroupBar {
            FilledTonalButton(onClick = { nav.groupAdd(written, edit) }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.group_add_line))
            }
            Button(onClick = { show() }, enabled = lines.isNotEmpty(), modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.group_show))
            }
        }
    }
}
