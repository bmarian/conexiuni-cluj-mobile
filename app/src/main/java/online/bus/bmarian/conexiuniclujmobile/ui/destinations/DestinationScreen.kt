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
import online.bus.bmarian.conexiuniclujmobile.data.DestinationSummary
import online.bus.bmarian.conexiuniclujmobile.data.HttpException
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.Ride
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.rideMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.NaturalOrder
import online.bus.bmarian.conexiuniclujmobile.logic.stopMatchRank
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
import online.bus.bmarian.conexiuniclujmobile.ui.home.RESULTS_PER_KIND
import online.bus.bmarian.conexiuniclujmobile.ui.home.ResultRow
import online.bus.bmarian.conexiuniclujmobile.ui.home.StationRow
import online.bus.bmarian.conexiuniclujmobile.ui.home.norm
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader

private const val RIDES_SHOWN = 3

private class NamedLike(val areas: List<DestinationSummary>, val stops: List<Stop>)

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
    val notFound = (loaded?.exceptionOrNull() as? HttpException)?.code == 404
    val areas by produceState(emptyList<DestinationSummary>(), notFound) {
        if (notFound) value = runCatching { Api.get<List<DestinationSummary>>("destinations") }.getOrDefault(emptyList())
    }
    val asked = slug.replace('-', ' ')
    val similar = remember(asked, areas, index) { index?.let { namedLike(asked, areas, it) } }
    LaunchedEffect(page) { page?.let { map.flyTo(it.lat, it.lon) } }
    var opened by remember(slug) { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(page?.name ?: asked.takeIf { notFound }, listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            val network = index
            when {
                notFound -> notFound(asked, similar, network, nav)
                loaded?.isFailure == true -> item { LoadFailed({ attempt++ }) }
                page == null || network == null -> item { LoadingRow() }
                else -> destination(page, network, nav, opened) { opened = opened + it }
            }
            if (notFound || (page != null && network != null)) {
                item(key = "all") { AllDestinationsRow(Modifier.padding(top = 8.dp)) }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun namedLike(asked: String, areas: List<DestinationSummary>, index: NetworkIndex): NamedLike {
    val q = norm(asked.trim())
    fun <T> ranked(items: List<T>, name: (T) -> String) = items
        .mapNotNull { item -> stopMatchRank(norm(name(item)), q).takeIf { it >= 0 }?.let { item to it } }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy(NaturalOrder) { name(it.first) })
        .map { it.first }
    return NamedLike(
        ranked(areas) { it.name }.take(RESULTS_PER_KIND),
        index.oneStopPerStation(ranked(index.stops) { it.name }).take(RESULTS_PER_KIND),
    )
}

@Composable
private fun Header(title: String, kicker: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        kicker?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun LazyListScope.notFound(asked: String, similar: NamedLike?, network: NetworkIndex?, nav: Navigator) {
    item(key = "header") { Header(stringResource(R.string.w_dest_not_found, asked)) }
    val areas = similar?.areas.orEmpty()
    val stops = similar?.stops.orEmpty()
    if (areas.isNotEmpty()) {
        item(key = "like-areas") { SectionLabel(stringResource(R.string.w_destinations), Modifier.padding(top = 8.dp), icon = Icons.Rounded.Place) }
        items(areas, key = { "area-${it.slug}" }) { area -> AreaRow(area) { nav.destination(area.slug) } }
    }
    if (stops.isNotEmpty()) {
        item(key = "like-stops") { SectionLabel(stringResource(R.string.w_stops), Modifier.padding(top = 8.dp), icon = Icons.Rounded.DirectionsBus) }
        items(stops, key = { "stop-${it.stopId}" }) { stop ->
            StationRow(stop, network?.look(stop.stopId), distance = null) { nav.stop(stop.stopId) }
        }
    }
}

private fun LazyListScope.destination(page: DestinationPage, network: NetworkIndex, nav: Navigator, opened: Set<String>, open: (String) -> Unit) {
    item(key = "header") {
        Header(
            stringResource(R.string.w_dest_question, page.name),
            stringResource(if (page.district) R.string.w_dest_kind_district else R.string.w_dest_kind_town),
        )
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
