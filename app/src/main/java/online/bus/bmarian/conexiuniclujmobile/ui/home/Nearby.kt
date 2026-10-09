package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.data.VehicleStream
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.computeStopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.formatMeters
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.logic.trackableTripIds
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.SectionLabel
import online.bus.bmarian.conexiuniclujmobile.ui.TimePill
import online.bus.bmarian.conexiuniclujmobile.ui.minutesLabel
import java.time.Instant
import java.time.LocalDateTime

private const val NEARBY_STOPS = 3
private const val NEARBY_METERS = 600.0
private const val NEARBY_DEPARTURES = 3

/** A stop near you and its next buses; null departures while they load. */
class NearbyStop(val stop: Stop, val meters: Double, val departures: List<StopDeparture>?)

class NearbyUi(val stops: List<NearbyStop>, val now: LocalDateTime)

/** The stops closest to you, within a short walk. */
fun nearestStops(stops: List<Stop>, fix: Fix, count: Int = NEARBY_STOPS, within: Double = NEARBY_METERS): List<Pair<Stop, Double>> =
    stops.asSequence()
        .map { it to haversineMeters(fix.lat, fix.lon, it.lat, it.lon) }
        .filter { it.second <= within }
        .sortedBy { it.second }
        .take(count)
        .toList()

/**
 * Home's "Nearby": the next buses at the few stops around you, worked out the way a stop's page
 * does, live while Home is on screen. The watch's Nearby screen does the same.
 */
class NearbyViewModel : ViewModel() {
    private val repository = Services.repository
    private val infos = MutableStateFlow<Map<Int, StopInfo>>(emptyMap())
    private val shapes = MutableStateFlow<Map<String, List<ShapePoint>>>(emptyMap())
    private val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())

    // Recomputed as you walk; the same stops in the same order leave everything as it is.
    private val nearest: StateFlow<List<Pair<Stop, Double>>> =
        combine(Services.location, repository.stops) { fix, stops -> if (fix == null) emptyList() else nearestStops(stops, fix) }
            .distinctUntilChanged { a, b -> a.map { it.first.stopId } == b.map { it.first.stopId } && a.zip(b).all { (x, y) -> kotlin.math.abs(x.second - y.second) < 25 } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ui: StateFlow<NearbyUi?> = combine(nearest, infos, shapes, vehicles, Services.clock) { near, infos, shapes, vehicles, _ ->
        if (near.isEmpty()) return@combine null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
        NearbyUi(
            near.map { (stop, meters) ->
                val info = infos[stop.stopId]
                NearbyStop(stop, meters, info?.let { computeStopDepartures(it, shapes, vehicles, now, nowMs).take(NEARBY_DEPARTURES) })
            },
            now,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            nearest.map { list -> list.map { it.first.stopId } }.distinctUntilChanged().collectLatest { ids ->
                for (id in ids) {
                    if (id in infos.value) continue
                    repository.cachedStopInfo(id)?.let { infos.value = infos.value + (id to it) }
                }
                for (id in ids) {
                    val fresh = try {
                        repository.stopInfo(id)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        continue
                    }
                    infos.value = infos.value + (id to fresh)
                    val trips = trackableTripIds(fresh).filter { it !in shapes.value }
                    if (trips.isNotEmpty()) runCatching { repository.shapes(trips) }.getOrNull()?.let { shapes.value = shapes.value + it }
                }
            }
        }
    }

    /** Runs while Home is visible: the buses heading to these stops stream in. */
    suspend fun live() {
        combine(nearest, infos) { near, infos -> near.mapNotNull { infos[it.first.stopId] }.flatMap(::trackableTripIds).distinct() }
            .distinctUntilChanged()
            .collectLatest { ids ->
                if (ids.isEmpty()) return@collectLatest
                VehicleStream.vehicles(ids).collect { vehicles.value = it }
            }
    }
}

/** The Nearby cards: a stop, how far it is, and its next buses with their minutes. */
fun LazyListScope.nearbySection(ui: NearbyUi, onStop: (Int) -> Unit) {
    item(key = "nearby-label") {
        SectionLabel(stringResource(R.string.nearby_title), Modifier.padding(top = 4.dp), icon = Icons.Rounded.NearMe, iconTint = MaterialTheme.colorScheme.primary)
    }
    items(ui.stops, key = { "nearby-${it.stop.stopId}" }, contentType = { "nearby" }) { near ->
        Surface(
            onClick = { onStop(near.stop.stopId) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).animateItem(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowIcon(Icons.Rounded.DirectionsBus, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 28.dp)
                    Text(near.stop.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatMeters(near.meters), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val departures = near.departures
                when {
                    departures == null -> Text(stringResource(R.string.w_news_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    departures.isEmpty() -> Text(stringResource(R.string.nearby_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (d in departures) {
                            val first = d.nextTimes.firstOrNull() ?: continue
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LineBadge(d.shortName, d.color, height = 24.dp, minWidth = 32.dp, corner = 6.dp)
                                TimePill(minutesLabel(first.minutes, ui.now), live = first.isLive)
                            }
                        }
                    }
                }
            }
        }
    }
}
