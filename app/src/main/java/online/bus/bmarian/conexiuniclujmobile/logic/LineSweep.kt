package online.bus.bmarian.conexiuniclujmobile.logic

/** The web's SWEEP_MS, in step with the badge's heart. */
const val SWEEP_MS = 1130L

/** The part of [points] between the shares [from] and [to] of its length. */
fun partBetween(points: List<LatLon>, from: Float, to: Float): List<LatLon> {
    if (points.size < 2 || to <= from) return emptyList()
    val steps = points.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }
    val total = steps.sum()
    val start = total * from.coerceIn(0f, 1f)
    val end = total * to.coerceIn(0f, 1f)
    fun at(i: Int, into: Double): LatLon {
        val f = if (steps[i] > 0) into / steps[i] else 0.0
        val a = points[i]
        val b = points[i + 1]
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }
    val out = mutableListOf<LatLon>()
    var walked = 0.0
    for ((i, step) in steps.withIndex()) {
        val next = walked + step
        if (next >= start && walked <= end) {
            if (out.isEmpty()) out += at(i, (start - walked).coerceAtLeast(0.0))
            if (next <= end) out += points[i + 1] else {
                out += at(i, end - walked)
                break
            }
        }
        walked = next
    }
    return out
}
