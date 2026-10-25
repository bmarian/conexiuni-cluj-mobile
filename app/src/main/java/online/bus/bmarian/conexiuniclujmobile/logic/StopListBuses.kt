package online.bus.bmarian.conexiuniclujmobile.logic

/** Port of utils/stopListBuses.ts. Each stop is searched after the one before, so a line passing a street twice finds the right side. */
fun stopsAlong(path: RoutePath, stops: List<IndexedStop>): List<Double?> {
    var from = 0.0
    return stops.map { indexed -> path.locate(indexed.stop.lat, indexed.stop.lon, from)?.also { from = it.s }?.s }
}

/** Where a point [s] metres along the path sits in the stop list: 2.5 is halfway from the third stop to the fourth. */
fun rowAlong(stopsAt: List<Double?>, s: Double): Float {
    val known = stopsAt.withIndex().mapNotNull { (row, at) -> at?.let { row to it } }
    if (known.isEmpty()) return 0f
    if (s <= known.first().second) return known.first().first.toFloat()
    for (i in 1 until known.size) {
        val (rowA, a) = known[i - 1]
        val (rowB, b) = known[i]
        if (s < b) return (rowA + (rowB - rowA) * (s - a) / (if (b == a) 1.0 else b - a)).toFloat()
    }
    return known.last().first.toFloat()
}
