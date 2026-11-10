package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.ShapePoint
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle

// Port of utils/lineStretch.ts.

class Span(val path: RoutePath, val start: Double, val end: Double)

/** Null when the whole line is followed or a stop can't be placed on the shape. */
fun NetworkIndex.stretchSpan(line: GroupLine, shape: List<ShapePoint>): Span? {
    val stretch = stretchOf(line)
    val whole = stretch.from < 0 && stretch.to >= stretch.stops.lastIndex
    val places = stretch.stops.mapNotNull { id -> stop(id)?.let { LatLon(it.lat, it.lon) } }
    if (whole || shape.isEmpty() || places.size < stretch.stops.size) return null
    val path = pathOf(shape)
    val at = placesAlong(path, places)
    val start = if (stretch.from < 0) 0.0 else at[stretch.from] ?: return null
    val end = if (stretch.to >= stretch.stops.lastIndex) path.length else at[stretch.to] ?: return null
    return Span(path, start, end).takeIf { end > start }
}

fun NetworkIndex.followedPart(line: GroupLine, shape: List<ShapePoint>): List<LatLon>? = stretchSpan(line, shape)?.let { it.path.between(it.start, it.end) }

fun Span.passedBy(vehicle: Vehicle): Boolean = path.locate(vehicle.latitude, vehicle.longitude)?.let { it.s > end } == true
