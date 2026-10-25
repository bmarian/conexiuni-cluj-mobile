package online.bus.bmarian.conexiuniclujmobile.ui.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.DestinationPage
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.Ride
import online.bus.bmarian.conexiuniclujmobile.data.rideMinutes
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadges
import online.bus.bmarian.conexiuniclujmobile.ui.LoadFailed
import online.bus.bmarian.conexiuniclujmobile.ui.LoadingRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.MessageText
import online.bus.bmarian.conexiuniclujmobile.ui.Navigator
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.ui.SectionLabel
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.home.ResultRow
import online.bus.bmarian.conexiuniclujmobile.ui.home.StationRow
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader

private const val RIDES_SHOWN = 3

/** Port of views/DestinationView.vue. */
@Composable
fun DestinationScreen(slug: String) {
    val nav = LocalNavigator.current
    val map = LocalMap.current
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner)
        onDispose { map.release(owner) }
    }
    var attempt by remember { mutableIntStateOf(0) }
    val loaded by produceState<Result<DestinationPage>?>(null, slug, attempt) {
        value = null
        value = runCatching { Api.get<DestinationPage>("destinations/${Api.encode(slug)}") }
    }
    val page = loaded?.getOrNull()
    LaunchedEffect(page) { page?.let { map.flyTo(it.lat, it.lon) } }
    var opened by remember(slug) { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(page?.name, listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            val network = index
            when {
                loaded?.isFailure == true -> item { LoadFailed({ attempt++ }) }
                page == null || network == null -> item { LoadingRow() }
                else -> {
                    destination(page, network, nav, opened) { opened = opened + it }
                    item(key = "all") { AllDestinationsRow(Modifier.padding(top = 8.dp)) }
                    item(key = "end") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun LazyListScope.destination(page: DestinationPage, network: NetworkIndex, nav: Navigator, opened: Set<String>, open: (String) -> Unit) {
    item(key = "header") {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
            Text(
                stringResource(if (page.district) R.string.w_dest_kind_district else R.string.w_dest_kind_town),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.w_dest_question, page.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
    item(key = "actions") {
        ActionChips(
            listOf(
                ChipAction(stringResource(R.string.directions), Icons.Rounded.Directions, primary = true) {
                    nav.plan(PlanQuery.to(page.lat, page.lon, page.name))
                },
            ),
            Modifier.padding(bottom = 8.dp),
        )
    }
    val lines = page.routeIds.mapNotNull(network::route)
    if (lines.isNotEmpty()) {
        item(key = "lines") {
            Column(Modifier.padding(bottom = 8.dp)) {
                SectionLabel(stringResource(R.string.w_dest_lines_in, page.name), icon = Icons.Rounded.Map)
                LineBadges(lines, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    item(key = "from") { SectionLabel(stringResource(R.string.w_dest_from), Modifier.padding(top = 8.dp), icon = Icons.Rounded.DirectionsBus) }
    if (page.from.isEmpty()) item(key = "no-rides") { MessageText(stringResource(R.string.w_dest_no_rides)) }
    for (origin in page.from) {
        val rides = origin.rides.mapNotNull { ride -> network.route(ride.routeId)?.let { ride to it } }
        val shown = if (origin.slug in opened) rides else rides.take(RIDES_SHOWN)
        item(key = "from-${origin.slug}") { SubLabel(stringResource(R.string.w_dest_from_area, origin.name), Modifier.padding(top = 8.dp)) }
        itemsIndexed(shown, key = { i, _ -> "ride-${origin.slug}-$i" }) { _, (ride, route) ->
            RideRow(ride, route, network) { nav.route(ride.routeId, ride.direction.toString()) }
        }
        if (rides.size > shown.size) {
            item(key = "more-${origin.slug}") {
                TextButton(onClick = { open(origin.slug) }, Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.w_dest_more_rides, rides.size - shown.size))
                }
            }
        }
    }
    val stops = page.stopIds.mapNotNull(network::stop)
    if (stops.isNotEmpty()) {
        item(key = "stops") { SectionLabel(stringResource(R.string.w_dest_stops_in, page.name), Modifier.padding(top = 16.dp), icon = Icons.Rounded.Place) }
        items(stops, key = { "stop-${it.stopId}" }) { stop ->
            StationRow(stop, network.look(stop.stopId), distance = null) { nav.stop(stop.stopId) }
        }
    }
}

@Composable
private fun RideRow(ride: Ride, route: NetworkRoute, network: NetworkIndex, onClick: () -> Unit) {
    val detail = listOfNotNull(
        if (ride.everyMin > 0) stringResource(R.string.w_dest_every, ride.everyMin) else null,
        "${ride.first}–${ride.last}",
        if (ride.dayType != "weekday") stringResource(R.string.w_dest_no_weekdays) else null,
    ).joinToString(" · ")
    ResultRow(
        icon = { LineBadge(route.shortName, route.color, height = 28.dp, minWidth = 36.dp, corner = 6.dp) },
        title = "${network.stop(ride.board)?.name.orEmpty()} → ${network.stop(ride.alight)?.name.orEmpty()}",
        distance = rideMinutes(ride.minutes),
        subtitle = detail,
        onClick = onClick,
    )
}
