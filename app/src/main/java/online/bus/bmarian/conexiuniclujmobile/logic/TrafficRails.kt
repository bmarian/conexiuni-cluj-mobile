package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.TrafficStretch

class Rail(val band: Int, val color: Int, val width: Float)

val RAILS = listOf(Rail(2, 0xFFEA4335.toInt(), 2f), Rail(3, 0xFFA50E0E.toInt(), 3f))

const val RAIL_GAP = 1.5f

class TrafficRun(val from: Float, val to: Float, val band: Int)

/** Where along [points] each stretch runs, as shares of the length. */
fun trafficRuns(stretches: List<TrafficStretch>, points: List<LatLon>): List<TrafficRun> {
    if (stretches.isEmpty() || points.size < 2) return emptyList()
    val path = pathThrough(DoubleArray(points.size) { points[it].lat }, DoubleArray(points.size) { points[it].lon })
    if (path.length <= 0) return emptyList()
    var end = 0.0
    return stretches.mapNotNull { stretch ->
        val shape = decodePolyline(stretch.path, precision = 6)
        if (shape.size < 2) return@mapNotNull null
        val from = path.locate(shape.first().first, shape.first().second, from = end)?.s ?: return@mapNotNull null
        val to = path.locate(shape.last().first, shape.last().second, from = from)?.s ?: return@mapNotNull null
        if (to <= from) return@mapNotNull null
        end = to
        TrafficRun((from / path.length).toFloat(), (to / path.length).toFloat(), stretch.band)
    }
}

fun railParts(points: List<LatLon>, runs: List<TrafficRun>, done: Float = 1f): List<Pair<List<LatLon>, Int>> =
    runs.filter { it.from < done }.map { partBetween(points, it.from, minOf(it.to, done)) to it.band }.filter { it.first.size >= 2 }
