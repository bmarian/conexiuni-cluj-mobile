package online.bus.bmarian.conexiuniclujmobile.logic

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

private const val EARTH_RADIUS_METERS = 6_371_000.0

data class LatLon(val lat: Double, val lon: Double)

fun mercatorX(lon: Double) = (lon + 180.0) / 360.0
fun mercatorY(lat: Double): Double {
    val rad = Math.toRadians(lat)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
}

fun lonOf(x: Double) = x * 360.0 - 180.0
fun latOf(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun findClosestShapeIdx(lat: Double, lon: Double, shape: List<online.bus.bmarian.conexiuniclujmobile.data.ShapePoint>): Int {
    var idx = -1
    var best = Double.POSITIVE_INFINITY
    for (i in shape.indices) {
        val d = haversineMeters(shape[i].lat, shape[i].lon, lat, lon)
        if (d < best) {
            best = d
            idx = i
        }
    }
    return idx
}

fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

fun formatMeters(meters: Double): String =
    if (meters < 1000) "${Math.round(meters)} m" else String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000)

/** Port of convexHull in utils/geo.ts, points as (lon, lat). */
fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val sorted = points.sortedWith(compareBy({ it.first }, { it.second }))
    if (sorted.size < 3) return sorted
    fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
    fun half(list: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        for (p in list) {
            while (out.size >= 2 && cross(out[out.size - 2], out[out.size - 1], p) <= 0) out.removeAt(out.size - 1)
            out += p
        }
        return out.dropLast(1)
    }
    return half(sorted) + half(sorted.reversed())
}

/** The hull widened all round by [radius] degrees of longitude, drawn round on a Mercator map, as a closed ring. */
fun roundedHull(points: List<Pair<Double, Double>>, radius: Double, steps: Int = 16): List<Pair<Double, Double>> {
    val around = convexHull(points).flatMap { (lon, lat) ->
        val k = cos(Math.toRadians(lat))
        (0 until steps).map { i ->
            val a = 2 * Math.PI * i / steps
            (lon + radius * cos(a)) to (lat + radius * k * sin(a))
        }
    }
    val ring = convexHull(around)
    return ring + ring.first()
}
