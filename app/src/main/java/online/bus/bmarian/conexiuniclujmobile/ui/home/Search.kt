package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.logic.stopMatchRank
import online.bus.bmarian.conexiuniclujmobile.ui.StationIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationLines
import androidx.compose.ui.unit.Dp
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.Places
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.formatMeters
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.stop.RouteRow
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import java.text.Normalizer

private val DIACRITICS = Regex("\\p{Mn}+")

/** Port of norm(). */
fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

fun <T> sortByDistance(items: List<T>, lat: Double, lon: Double, latOf: (T) -> Double, lonOf: (T) -> Double): List<T> =
    items.map { it to haversineMeters(lat, lon, latOf(it), lonOf(it)) }.sortedBy { it.second }.map { it.first }

@Stable
class SearchState {
    var query by mutableStateOf("")
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    // The query the places are for.
    private var placesQuery by mutableStateOf("")

    val active: Boolean get() = query.trim().isNotEmpty()

    fun placesLoading(online: Boolean): Boolean {
        val q = query.trim()
        return online && q.length >= 3 && placesQuery != q
    }

    fun routes(routes: List<Route>): List<Route> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        return routes.filter { norm(it.shortName).contains(q) || norm(it.longName).contains(q) }.take(3)
    }

    /** Port of searchStopResults: the stop's own name before what tells namesakes apart, one stop per station. */
    fun stops(stops: List<Stop>, me: Fix?, index: NetworkIndex?): List<Stop> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        val matches = stops.mapNotNull { stop -> stopMatchRank(norm(stop.name), q).takeIf { it >= 0 }?.let { stop to it } }
        val sorted = if (me != null) sortByDistance(matches, me.lat, me.lon, { it.first.lat }, { it.first.lon }) else matches
        val ranked = sorted.sortedBy { it.second }.map { it.first }
        return (index?.oneStopPerStation(ranked) ?: ranked).take(3)
    }

    suspend fun searchPlaces(query: String, online: Boolean, me: Fix?) {
        val trimmed = query.trim()
        if (placesQuery == trimmed) return
        if (!online || trimmed.length < 3) {
            places = emptyList()
            placesQuery = trimmed
            return
        }
        delay(350)
        val results = try {
            Places.search(trimmed, 3)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        places = if (me != null && results.size > 1) {
            sortByDistance(results, me.lat, me.lon, { it.latitude ?: 0.0 }, { it.longitude ?: 0.0 })
        } else results
        placesQuery = trimmed
    }
}

@Composable
fun SearchField(state: SearchState, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    val focus = LocalFocusManager.current
    TextField(
        value = state.query,
        onValueChange = { state.query = it },
        modifier = modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        placeholder = { Text(stringResource(R.string.w_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (state.query.isNotEmpty()) {
            {
                IconButton(onClick = { state.query = "" }) { Icon(Icons.Rounded.Close, contentDescription = null) }
            }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

private fun distanceFrom(me: Fix?, lat: Double, lon: Double): String? =
    me?.let { formatMeters(haversineMeters(it.lat, it.lon, lat, lon)) }

fun LazyListScope.searchResults(
    state: SearchState,
    routes: List<Route>,
    stops: List<Stop>,
    me: Fix?,
    online: Boolean,
    navigatingRouteId: Int?,
    onPlace: (Place) -> Unit,
    onRoute: (Route) -> Unit,
    onStop: (Stop) -> Unit,
    onDropPin: () -> Unit,
) {
    val routeResults = state.routes(routes)
    val index = Services.repository.network.index.value
    val stopResults = state.stops(stops, me, index)
    val places = if (online) state.places else emptyList()
    val loading = state.placesLoading(online)
    if (loading) {
        item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
    }
    if (places.isNotEmpty()) {
        item { SubLabel(stringResource(R.string.w_search_results_places), Modifier.padding(top = 8.dp)) }
        items(places, key = { "place-${it.id}" }) { place ->
            ResultRow(
                icon = { RowIcon(Icons.Rounded.Place, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) },
                title = place.label,
                distance = place.latitude?.let { lat -> place.longitude?.let { lon -> distanceFrom(me, lat, lon) } },
                onClick = { onPlace(place) },
            )
        }
    }
    if (routeResults.isNotEmpty()) {
        item { SubLabel(stringResource(R.string.w_search_results_routes), Modifier.padding(top = 8.dp)) }
        items(routeResults, key = { "route-${it.routeId}" }) { route ->
            RouteRow(route.shortName, route.color, route.longName, busy = navigatingRouteId == route.routeId) { onRoute(route) }
        }
    }
    if (stopResults.isNotEmpty()) {
        item { SubLabel(stringResource(R.string.w_search_results_stops), Modifier.padding(top = 8.dp)) }
        items(stopResults, key = { "stop-${it.stopId}" }) { stop ->
            StationRow(stop, index?.look(stop.stopId), distanceFrom(me, stop.lat, stop.lon)) { onStop(stop) }
        }
    }
    if (!loading && places.isEmpty() && routeResults.isEmpty() && stopResults.isEmpty()) {
        item {
            if (online) {
                Row(
                    Modifier.fillMaxWidth().squishClickable(onClick = onDropPin).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RowIcon(Icons.Rounded.PushPin, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text(stringResource(R.string.w_no_results), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.w_drop_pin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(stringResource(R.string.w_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A station: its mode in its colour, its name and every line calling there. */
@Composable
fun StationRow(stop: Stop, look: StationLook?, distance: String?, iconSize: Dp = 36.dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().squishClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationIcon(look, size = iconSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StationLines(look)
        }
        distance?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ResultRow(icon: @Composable () -> Unit, title: String, distance: String?, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().squishClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        distance?.let {
            Box(Modifier.clip(CircleShape).padding(horizontal = 2.dp)) {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
}
