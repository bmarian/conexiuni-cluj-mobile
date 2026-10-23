package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters

/** The web's SWEEP_MS, in step with the badge's heart. */
const val SWEEP_MS = 1300L

/** The first [done] (0 to 1) of [points] by length, or the last when [backwards]. */
fun sweptPart(points: List<LatLon>, done: Float, backwards: Boolean): List<LatLon> {
    if (points.size < 2 || done <= 0f) return emptyList()
    val path = if (backwards) points.asReversed() else points
    val steps = path.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }
    var left = steps.sum() * done.coerceAtMost(1f)
    val out = mutableListOf(path[0])
    for ((i, step) in steps.withIndex()) {
        if (left >= step) {
            out += path[i + 1]
            left -= step
            continue
        }
        val f = if (step > 0) left / step else 0.0
        val a = path[i]
        val b = path[i + 1]
        out += LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        break
    }
    return out
}
