package online.bus.bmarian.conexiuniclujmobile.journey

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

    /** The nearest spot no earlier than `from` metres along. */
    fun locate(lat: Double, lon: Double, from: Double = 0.0): Spot? {
        if (points.size < 2) return null
        val kx = 111_320 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        var best: Spot? = null
        for (i in 1 until points.size) {
            if (at[i] < from) continue
            val a = points[i - 1]
            val b = points[i]
            val ax = (a.lon - lon) * kx
            val ay = (a.lat - lat) * ky
            val dx = (b.lon - a.lon) * kx
            val dy = (b.lat - a.lat) * ky
            val len = dx * dx + dy * dy
            val tMin = if (from > at[i - 1] && at[i] > at[i - 1]) (from - at[i - 1]) / (at[i] - at[i - 1]) else 0.0
            val t = if (len == 0.0) tMin else (-(ax * dx + ay * dy) / len).coerceIn(tMin, 1.0)
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
}
