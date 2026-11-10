package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.followedPart
import online.bus.bmarian.conexiuniclujmobile.logic.stretchOf
import online.bus.bmarian.conexiuniclujmobile.logic.tripOf
import online.bus.bmarian.conexiuniclujmobile.ui.LocalMap
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapController
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight

// Port of composables/useGroupShapes.ts and useStretchMap.ts.

/** [part]: the followed stretch, null when it is the whole line. */
private class Drawn(val trip: String, val route: NetworkRoute, val whole: List<LatLon>, val part: List<LatLon>?) {
    fun shape(points: List<LatLon>, faint: Boolean = false, skipFit: Boolean = false) = MapShape(trip, route.shortName, route.color, points, faint, skipFit)

    val followed get() = shape(part ?: whole)
}

private fun NetworkIndex.drawn(line: GroupLine, shapes: Map<String, List<ShapePoint>>): Drawn? {
    val route = route(line.routeId) ?: return null
    val shape = shapes[tripOf(line)].orEmpty().ifEmpty { return null }
    return Drawn(tripOf(line), route, shape.map { LatLon(it.lat, it.lon) }, followedPart(line, shape))
}

fun NetworkIndex.groupShapes(lines: List<GroupLine>, shapes: Map<String, List<ShapePoint>>): List<MapShape> {
    val drawn = lines.mapNotNull { drawn(it, shapes) }
    return drawn.mapNotNull { line -> line.part?.let { line.shape(line.whole, faint = true, skipFit = true) } } + drawn.map { it.followed }
}

private fun NetworkIndex.stretchShapes(line: GroupLine, others: List<GroupLine>, shapes: Map<String, List<ShapePoint>>): List<MapShape> {
    val beside = others.mapNotNull { drawn(it, shapes) }.map { it.shape(it.part ?: it.whole, faint = true, skipFit = true) }
    val own = drawn(line, shapes) ?: return beside
    return beside + listOfNotNull(own.part?.let { own.shape(own.whole, faint = true) }, own.followed)
}

@Composable
private fun linesTogether(): MapController {
    val map = LocalMap.current
    val owner = remember { Any() }
    DisposableEffect(owner) {
        map.claim(owner, keepLines = true)
        map.linesApart = false
        map.lineEnds = true
        onDispose { map.release(owner) }
    }
    return map
}

@Composable
private fun shapesOf(lines: List<GroupLine>): Map<String, List<ShapePoint>> {
    val trips = lines.map(::tripOf)
    return produceState(emptyMap(), trips) { value = runCatching { Services.repository.shapes(trips) }.getOrDefault(emptyMap()) }.value
}

@Composable
fun GroupMap(lines: List<GroupLine>) {
    val map = linesTogether()
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val shapes = shapesOf(lines)
    LaunchedEffect(index, lines, shapes) { index?.let { map.showShapes(it.groupShapes(lines, shapes)) } }
}

@Composable
fun StretchMap(line: GroupLine?, others: List<GroupLine>, nearest: Int?, onPick: (Int) -> Unit) {
    val map = linesTogether()
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val pick by rememberUpdatedState(onPick)
    LaunchedEffect(map) { map.stopPick = { pick(it) } }

    val shapes = shapesOf(listOfNotNull(line) + others)
    val fitted = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(index, line, others, shapes) {
        val known = index ?: return@LaunchedEffect
        val trip = line?.let(::tripOf) ?: return@LaunchedEffect
        if (shapes[trip].isNullOrEmpty()) return@LaunchedEffect
        // Taps on stops leave the view alone; a new line or direction fits it.
        map.fitNewShapes = fitted[0] != trip
        fitted[0] = trip
        map.showShapes(known.stretchShapes(line, others, shapes))
    }
    LaunchedEffect(index, line, nearest) {
        val stretch = line?.let { index?.stretchOf(it) } ?: return@LaunchedEffect
        val marks = LinkedHashMap<Int, StopHighlight>()
        stretch.stops.forEachIndexed { at, id ->
            marks[id] = if (at == stretch.from) StopHighlight.Green else if (id == nearest) StopHighlight.Purple else marks[id] ?: StopHighlight.Gray
        }
        map.highlightedStops = marks
    }
}
