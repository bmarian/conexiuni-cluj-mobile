package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.SearchAnswer
import online.bus.bmarian.conexiuniclujmobile.data.SearchBus
import online.bus.bmarian.conexiuniclujmobile.data.SearchOtherLine
import online.bus.bmarian.conexiuniclujmobile.data.SearchTrip
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.directionFromTripId
import online.bus.bmarian.conexiuniclujmobile.logic.formatMeters
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.ui.ActionChips
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.stop.RouteRow
import online.bus.bmarian.conexiuniclujmobile.ui.stop.directedDestination

private val CARD_SHAPE = RoundedCornerShape(20.dp)

class SearchTopActions(
    val line: (Route, timetable: Boolean) -> Unit,
    val stop: (Stop) -> Unit,
    val place: (Place) -> Unit,
    val bus: (SearchBus) -> Unit,
    val other: (SearchOtherLine) -> Unit,
    val city: (String) -> Unit,
    val trip: (SearchTrip) -> Unit,
    val link: (String) -> Unit,
)

fun LazyListScope.searchTop(
    answer: SearchAnswer,
    routes: List<Route>,
    stops: List<Stop>,
    index: NetworkIndex?,
    me: Fix?,
    actions: SearchTopActions,
) {
    if (answer.best == null && answer.notice == null) return
    item(key = "search-top") {
        if (answer.best != null) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, CARD_SHAPE)) {
                Best(answer, routes, stops, index, me, actions)
            }
        } else {
            Notice(answer, actions)
        }
    }
}

private fun distanceFrom(me: Fix?, lat: Double, lon: Double): String? = me?.let { formatMeters(haversineMeters(it.lat, it.lon, lat, lon)) }

@Composable
fun InfoRow(title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(Icons.Rounded.Info, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Best(answer: SearchAnswer, routes: List<Route>, stops: List<Stop>, index: NetworkIndex?, me: Fix?, actions: SearchTopActions) {
    val scheme = MaterialTheme.colorScheme
    val route = { id: Int? -> routes.firstOrNull { it.routeId == id } }
    when (answer.best) {
        SearchAnswer.BEST_LINE -> route(answer.lines.firstOrNull())?.let { line ->
            RouteRow(line.shortName, line.color, line.longName) { actions.line(line, answer.show == SearchAnswer.SHOW_TIMETABLE) }
            val timetable = ChipAction(
                stringResource(R.string.w_timetable),
                Icons.Rounded.Schedule,
                selected = answer.show == SearchAnswer.SHOW_TIMETABLE,
            ) { actions.line(line, true) }
            ActionChips(listOf(timetable), Modifier.padding(start = 52.dp, bottom = 8.dp))
        }
        SearchAnswer.BEST_STOP -> stops.firstOrNull { it.stopId == answer.stops.firstOrNull()?.id }?.let { stop ->
            StationRow(stop, index?.look(stop.stopId), distanceFrom(me, stop.lat, stop.lon)) { actions.stop(stop) }
        }
        SearchAnswer.BEST_PLACE -> answer.places.firstOrNull()?.let { place ->
            ResultRow(
                icon = { RowIcon(Icons.Rounded.Place, scheme.tertiaryContainer, scheme.onTertiaryContainer) },
                title = place.name,
                distance = place.latitude?.let { lat -> place.longitude?.let { lon -> distanceFrom(me, lat, lon) } },
                subtitle = place.detail.ifEmpty { null },
                onClick = { actions.place(place) },
            )
        }
        SearchAnswer.BEST_BUS -> answer.buses.firstOrNull()?.let { bus ->
            val line = route(bus.routeId)
            ResultRow(
                icon = { RowIcon(Icons.Rounded.DirectionsBus, scheme.primaryContainer, scheme.onPrimaryContainer) },
                title = stringResource(R.string.w_search_bus, bus.label),
                distance = null,
                subtitle = line?.let { "${it.shortName} → ${directedDestination(it.longName, directionFromTripId(bus.tripId))}" },
                onClick = { actions.bus(bus) },
            )
        }
        SearchAnswer.BEST_OTHER -> answer.other.firstOrNull()?.let { other ->
            ResultRow(
                icon = { LineBadge(other.name, other.color) },
                title = other.longName,
                distance = null,
                subtitle = other.cityName,
                onClick = { actions.other(other) },
            )
            val switch = ChipAction(stringResource(R.string.w_search_switch_city, other.cityName), Icons.Rounded.SwapHoriz, selected = true) {
                actions.other(other)
            }
            ActionChips(listOf(switch), Modifier.padding(start = 52.dp, bottom = 8.dp))
        }
        SearchAnswer.BEST_CITY -> answer.cities.firstOrNull()?.let { city ->
            ResultRow(
                icon = { RowIcon(Icons.Rounded.LocationCity, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) },
                title = city.name,
                distance = null,
                subtitle = stringResource(R.string.w_search_change_city),
                onClick = { actions.city(city.slug) },
            )
        }
        SearchAnswer.BEST_TRIP -> answer.trip?.let { trip ->
            val from = trip.from?.name ?: stringResource(R.string.w_plan_origin_current_location)
            ResultRow(
                icon = { RowIcon(Icons.Rounded.Directions, scheme.tertiaryContainer, scheme.onTertiaryContainer) },
                title = "$from → ${trip.to.name}",
                distance = null,
                subtitle = stringResource(R.string.w_plan_title),
                onClick = { actions.trip(trip) },
            )
        }
        SearchAnswer.BEST_FAR -> answer.far?.let { far ->
            val line = route(far.routeId)
            val distance = formatMeters(far.meters.toDouble())
            InfoRow(
                stringResource(if (far.town) R.string.w_search_far_town else R.string.w_search_far_place, far.name),
                line?.let { stringResource(R.string.w_search_far_closest, it.shortName, distance) },
            )
            val stop = stops.firstOrNull { it.stopId == far.stopId }
            val place = answer.places.firstOrNull()
            if (line != null && stop != null && place != null) {
                ResultRow(
                    icon = { LineBadge(line.shortName, line.color) },
                    title = "→ ${stop.name}",
                    distance = null,
                    subtitle = stringResource(R.string.w_search_far_then, distance, far.name),
                    onClick = { actions.place(place) },
                )
            }
        }
    }
}

@Composable
private fun Notice(answer: SearchAnswer, actions: SearchTopActions) {
    val notice = answer.notice ?: return
    when (notice.kind) {
        SearchAnswer.NOTICE_CITY_MISSING -> InfoRow(stringResource(R.string.w_search_city_missing, notice.name.orEmpty()))
        SearchAnswer.NOTICE_BUS_OFF -> InfoRow(stringResource(R.string.w_search_bus_off, notice.name.orEmpty()))
        SearchAnswer.NOTICE_TICKETS -> Column {
            InfoRow(stringResource(R.string.w_search_tickets))
            notice.url?.let { url ->
                ResultRow(
                    icon = {
                        RowIcon(Icons.AutoMirrored.Rounded.OpenInNew, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    },
                    title = stringResource(R.string.w_search_operator_site, notice.operator.orEmpty()),
                    distance = null,
                    subtitle = url.toUri().host?.removePrefix("www."),
                    onClick = { actions.link(url) },
                )
            }
        }
    }
}
