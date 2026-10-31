package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.logic.HEADING_SPAN_M
import online.bus.bmarian.conexiuniclujmobile.logic.bearingDegrees
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import kotlin.math.cos
import kotlin.math.hypot

/** Where a point falls on a line: metres along it, metres off it, the spot on it and the line's heading there. */
internal class Spot(val along: Double, val off: Double, val lat: Double, val lon: Double, val bearing: Double)

/** A line measured along its length, to follow someone along it. */
internal class RouteLine(val points: List<JourneyPoint>) {
    private val at = DoubleArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + haversineMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
    }
    val length get() = at.lastOrNull() ?: 0.0

    /** The nearest spot between `from` and `to` metres along. */
    fun locate(lat: Double, lon: Double, from: Double = 0.0, to: Double = Double.POSITIVE_INFINITY): Spot? {
        if (points.size < 2) return null
        val kx = 111_320 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        var best: Spot? = null
        for (i in 1 until points.size) {
            if (at[i] < from) continue
            if (at[i - 1] > to) break
            val a = points[i - 1]
            val b = points[i]
            val ax = (a.lon - lon) * kx
            val ay = (a.lat - lat) * ky
            val dx = (b.lon - a.lon) * kx
            val dy = (b.lat - a.lat) * ky
            val len = dx * dx + dy * dy
            val span = at[i] - at[i - 1]
            val tMin = if (from > at[i - 1] && span > 0) (from - at[i - 1]) / span else 0.0
            val tMax = if (to < at[i] && span > 0) ((to - at[i - 1]) / span).coerceAtLeast(tMin) else 1.0
            val t = if (len == 0.0) tMin else (-(ax * dx + ay * dy) / len).coerceIn(tMin, tMax)
            val off = hypot(ax + t * dx, ay + t * dy)
            if (best != null && off >= best.off) continue
            val along = at[i - 1] + t * (at[i] - at[i - 1])
            best = Spot(along, off, a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon), bearingDegrees(a.lat, a.lon, b.lat, b.lon))
        }
        return best
    }

    /** What is left of the line from `along` metres on. */
    fun after(along: Double): List<JourneyPoint> {
        val i = at.indexOfFirst { it > along }
        if (i <= 0) return if (i == 0) points else emptyList()
        val t = (along - at[i - 1]) / (at[i] - at[i - 1])
        val a = points[i - 1]
        val b = points[i]
        return listOf(JourneyPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))) + points.subList(i, points.size)
    }

    /** The line from `from` to `to` metres along. */
    fun between(from: Double, to: Double): List<JourneyPoint> {
        if (points.size < 2 || to <= from) return emptyList()
        val inside = points.indices.filter { at[it] > from && at[it] < to }.map { points[it] }
        return listOf(pointAt(from)) + inside + pointAt(to)
    }

    fun headingAt(along: Double): Double {
        val a = pointAt(along - HEADING_SPAN_M)
        val b = pointAt(along + HEADING_SPAN_M)
        return bearingDegrees(a.lat, a.lon, b.lat, b.lon)
    }

    fun pointAt(along: Double): JourneyPoint {
        val i = at.indexOfFirst { it >= along }.let { if (it < 1) if (along <= 0) 1 else points.lastIndex else it }
        val span = at[i] - at[i - 1]
        val t = if (span > 0) ((along - at[i - 1]) / span).coerceIn(0.0, 1.0) else 0.0
        val a = points[i - 1]
        val b = points[i]
        return JourneyPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }
}
