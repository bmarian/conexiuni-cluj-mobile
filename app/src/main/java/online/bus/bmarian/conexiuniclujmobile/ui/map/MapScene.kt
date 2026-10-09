package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.bearingDegrees
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.min

/**
 * The website's dashes for a second, third... route of the same colour, in dp along a 5 dp line.
 * The first is solid.
 */
val DUPLICATE_DASH_PATTERNS = listOf(floatArrayOf(), floatArrayOf(8f, 7f), floatArrayOf(2f, 7f), floatArrayOf(10f, 4f, 2f, 4f), floatArrayOf(1f, 6f))

class SceneLine(val color: String, val dash: Int, val points: List<LatLon>)
class SceneBadge(val at: LatLon, val routes: List<Pair<String, String>>)
class SceneEnd(val at: LatLon, val color: String)
class SceneArrow(val at: LatLon, val bearing: Double)

/** What the routes on the map draw: lines, the badges at their starts, the ends, the arrows. */
class MapScene(val lines: List<SceneLine>, val badges: List<SceneBadge>, val ends: List<SceneEnd>, val arrows: List<SceneArrow>) {
    companion object {
        val Empty = MapScene(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Routes as the website draws them: a second shape of the same colour gets a dash, the same
 * path twice is drawn once. Start badges and end markers only when [terminals]; a direction
 * arrow at each start when [arrows].
 */
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
    val lines = kept.map { (shape, dash) -> SceneLine(shape.color, dash, shape.points) }
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
        computeStartBearing(shape.points)?.let { SceneArrow(shape.points.first(), it) }
    }
    return MapScene(lines, badges, ends, arrowList)
}

/** Which way a route leaves its start: towards the first point 30 m on, or the next one. */
fun computeStartBearing(points: List<LatLon>): Double? {
    if (points.size < 2) return null
    val start = points[0]
    for (i in 1 until min(points.size, 20)) {
        val p = points[i]
        if (haversineMeters(start.lat, start.lon, p.lat, p.lon) >= 30) return bearingDegrees(start.lat, start.lon, p.lat, p.lon)
    }
    return bearingDegrees(start.lat, start.lon, points[1].lat, points[1].lon)
}

/** The shortest way round from one heading to another, for buses turning as they glide. */
fun headingBetween(from: Double, to: Double, t: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return ((from + delta * t) % 360.0 + 360.0) % 360.0
}
