package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.bearingDegrees
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.min

/** In dp along a 5 dp line; the first is solid. */
val DUPLICATE_DASH_PATTERNS = listOf(floatArrayOf(), floatArrayOf(8f, 7f), floatArrayOf(2f, 7f), floatArrayOf(10f, 4f, 2f, 4f), floatArrayOf(1f, 6f))

class SceneLine(val name: String, val color: String, val dash: Int, val points: List<LatLon>)
class SceneBadge(val at: LatLon, val routes: List<Pair<String, String>>)
class SceneEnd(val at: LatLon, val color: String)
class SceneArrow(val at: LatLon, val bearing: Double)

class MapScene(val lines: List<SceneLine>, val badges: List<SceneBadge>, val ends: List<SceneEnd>, val arrows: List<SceneArrow>) {
    companion object {
        val Empty = MapScene(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

fun buildScene(shapes: List<MapShape>, terminals: Boolean, arrows: Boolean): MapScene {
    val drawable = shapes.filter { it.points.isNotEmpty() }
    val counts = drawable.groupingBy { it.color }.eachCount()
    val seen = HashMap<String, Int>()
    val signatures = HashSet<String>()
    val kept = drawable.mapNotNull { shape ->
        val variant = seen[shape.color] ?: 0
        seen[shape.color] = variant + 1
        val dash = if ((counts[shape.color] ?: 0) > 1) variant % DUPLICATE_DASH_PATTERNS.size else 0
        val signature = "$dash|" + shape.points.joinToString("|") { "%.4f,%.4f".format(it.lat, it.lon) }
        if (!signatures.add(signature)) null else shape to dash
    }
    val lines = kept.map { (shape, dash) -> SceneLine(shape.shortName, shape.color, dash, shape.points) }
    val badges = if (!terminals) emptyList() else {
        val grouped = LinkedHashMap<String, Pair<LatLon, MutableList<Pair<String, String>>>>()
        for ((shape, _) in kept) {
            if (shape.shortName.isEmpty()) continue
            val start = shape.points.first()
            val key = "%.4f,%.4f".format(start.lat, start.lon)
            val entry = grouped.getOrPut(key) { start to mutableListOf() }
            if (entry.second.none { it.first == shape.shortName }) entry.second += shape.shortName to shape.color
        }
        grouped.values.map { SceneBadge(it.first, it.second) }
    }
    val ends = if (!terminals) emptyList() else kept.map { (shape, _) -> SceneEnd(shape.points.last(), shape.color) }
    val arrowList = if (!arrows) emptyList() else kept.mapNotNull { (shape, _) ->
        routeHeading(shape.points)?.let { SceneArrow(shape.points.first(), it) }
    }
    return MapScene(lines, badges, ends, arrowList)
}

private const val LOOP_SHARE = 0.25

fun routeHeading(points: List<LatLon>): Double? {
    if (points.size < 2) return null
    val start = points[0]
    fun fromStart(p: LatLon) = haversineMeters(start.lat, start.lon, p.lat, p.lon)
    val end = points.last()
    val toward = if (fromStart(end) >= points.maxOf(::fromStart) * LOOP_SHARE) end
    else points.subList(1, min(points.size, 20)).firstOrNull { fromStart(it) >= 30 } ?: points[1]
    return bearingDegrees(start.lat, start.lon, toward.lat, toward.lon)
}

fun headingBetween(from: Double, to: Double, t: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return ((from + delta * t) % 360.0 + 360.0) % 360.0
}
