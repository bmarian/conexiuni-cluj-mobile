package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute

// Port of utils/lineCalls.ts.

class Call(val route: NetworkRoute, val direction: String, val stopId: Int)

private val DIRECTIONS = listOf("0", "1")
private const val VIA_SHOWN = 3

fun NetworkIndex.callsByStation(routes: List<NetworkRoute>): Map<Int, List<Call>> {
    val out = LinkedHashMap<Int, MutableList<Call>>()
    for (route in routes.sortedWith(byModeThenNumber({ it.type }, { it.shortName }))) {
        for (direction in DIRECTIONS) {
            val seen = HashSet<Int>()
            for (stopId in lineStops(GroupLine(route.routeId, direction)).dropLast(1)) {
                val station = stationId(stopId)
                if (seen.add(station)) out.getOrPut(station) { mutableListOf() } += Call(route, direction, stopId)
            }
        }
    }
    return out
}

/** The stations most lines call at, in the line's order. */
fun NetworkIndex.viaStops(route: NetworkRoute, calls: Map<Int, List<Call>>): List<String> {
    val stops = DIRECTIONS.map { lineStops(GroupLine(route.routeId, it)) }.firstOrNull { it.isNotEmpty() }.orEmpty()
    val seen = HashSet<Int>()
    fun linesAt(stopId: Int) = calls[stationId(stopId)].orEmpty().distinctBy { it.route.routeId }.size
    return stops.drop(1).dropLast(1).withIndex()
        .filter { seen.add(stationId(it.value)) }
        .sortedWith(compareByDescending<IndexedValue<Int>> { linesAt(it.value) }.thenBy { it.index })
        .take(VIA_SHOWN)
        .sortedBy { it.index }
        .mapNotNull { stop(it.value)?.name }
}
