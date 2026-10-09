package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.Nominatim
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.formatMeters
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.stop.RouteRow
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import java.text.Normalizer

private val DIACRITICS = Regex("\\p{Mn}+")

/** NFD without the marks, lower-cased: the website's norm(). */
fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

fun <T> sortByDistance(items: List<T>, lat: Double, lon: Double, latOf: (T) -> Double, lonOf: (T) -> Double): List<T> =
    items.map { it to haversineMeters(lat, lon, latOf(it), lonOf(it)) }.sortedBy { it.second }.map { it.first }

/** The website's universal search: lines, stops and places from one box. */
@Stable
class SearchState {
    var query by mutableStateOf("")
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var placesLoading by mutableStateOf(false)
        private set

    val active: Boolean get() = query.trim().isNotEmpty()

    fun routes(routes: List<Route>): List<Route> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        return routes.filter { norm(it.shortName).contains(q) || norm(it.longName).contains(q) }.take(3)
    }

    fun stops(stops: List<Stop>, me: Fix?): List<Stop> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        val filtered = stops.filter { norm(it.name).contains(q) }
        val sorted = if (me != null) sortByDistance(filtered, me.lat, me.lon, { it.lat }, { it.lon }) else filtered
        return sorted.take(3)
    }

    /** Debounced like the website: places once three letters have rested for 350 ms. */
    suspend fun searchPlaces(query: String, online: Boolean, locale: String, me: Fix?) {
        places = emptyList()
        val trimmed = query.trim()
        if (!online || trimmed.length < 3) {
            placesLoading = false
            return
        }
        placesLoading = true
        delay(350)
        try {
            if (Nominatim.isGoogleMapsUrl(trimmed)) {
                places = listOfNotNull(Nominatim.resolveGoogleMapsLink(trimmed))
            } else {
                val results = Nominatim.search(trimmed, locale, 3)
                places = if (me != null && results.size > 1) {
                    sortByDistance(results, me.lat, me.lon, { it.latitude ?: 0.0 }, { it.longitude ?: 0.0 })
                } else results
            }
        } finally {
            placesLoading = false
        }
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
    val stopResults = state.stops(stops, me)
    val places = if (online) state.places else emptyList()
    if (state.placesLoading) {
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
            ResultRow(
                icon = { RowIcon(Icons.Rounded.DirectionsBus, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                title = stop.name,
                distance = distanceFrom(me, stop.lat, stop.lon),
                onClick = { onStop(stop) },
            )
        }
    }
    if (!state.placesLoading && places.isEmpty() && routeResults.isEmpty() && stopResults.isEmpty()) {
        item {
            if (online) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onDropPin).padding(horizontal = 16.dp, vertical = 12.dp),
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

@Composable
fun ResultRow(icon: @Composable () -> Unit, title: String, distance: String?, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
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
