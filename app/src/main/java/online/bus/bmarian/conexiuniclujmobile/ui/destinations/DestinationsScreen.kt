package online.bus.bmarian.conexiuniclujmobile.ui.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.DestinationSummary
import online.bus.bmarian.conexiuniclujmobile.ui.LoadFailed
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SectionLabel
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.cityName
import online.bus.bmarian.conexiuniclujmobile.ui.home.ResultRow
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader

/** Port of views/DestinationsView.vue. */
@Composable
fun DestinationsScreen() {
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val city by Cities.current.collectAsStateWithLifecycle()
    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        onDispose { map.release(owner) }
    }
    var attempt by remember { mutableIntStateOf(0) }
    val loaded by produceState<Result<List<DestinationSummary>>?>(null, city, attempt) {
        value = null
        value = runCatching { Api.get<List<DestinationSummary>>("destinations", city) }
    }
    val areas = loaded?.getOrNull()
    val groups = remember(areas) {
        listOf(R.string.w_dests_districts to areas.orEmpty().filter { it.district }, R.string.w_dests_towns to areas.orEmpty().filterNot { it.district })
            .filter { it.second.isNotEmpty() }
    }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.w_destinations), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            when {
                loaded?.isFailure == true -> item { LoadFailed({ attempt++ }) }
                areas == null -> item { LoadingRow() }
                else -> {
                    item(key = "title") {
                        Text(
                            stringResource(R.string.w_dests_in, cityName()),
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    for ((label, group) in groups) {
                        item(key = "label-$label") { SectionLabel(stringResource(label)) }
                        items(group, key = { "area-${it.slug}" }) { area ->
                            ResultRow(
                                icon = {},
                                title = area.name,
                                distance = null,
                                subtitle = stringResource(R.string.w_dest_counts, area.stops, area.lines),
                            ) { nav.destination(area.slug) }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun AllDestinationsRow(modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    ResultRow(icon = {}, title = stringResource(R.string.w_dest_all, cityName()), distance = null, modifier = modifier, onClick = nav::destinations)
}
