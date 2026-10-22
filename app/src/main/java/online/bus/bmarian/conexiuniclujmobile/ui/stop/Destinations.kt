package online.bus.bmarian.conexiuniclujmobile.ui.stop

import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.OUTGOING_SUFFIX
import online.bus.bmarian.conexiuniclujmobile.logic.reverseRouteLongName

// Port of utils/destinations.ts.

class Destination(val name: String?, val routes: List<NetworkRoute>)

fun stationDestinations(index: NetworkIndex, stopId: Int): List<Destination> {
    val platforms = index.platforms(stopId).mapTo(HashSet()) { it.stopId }
    val out = LinkedHashMap<String?, MutableList<NetworkRoute>>()
    val leaving = HashSet<NetworkRoute>()
    val ending = ArrayList<NetworkRoute>()
    for (route in index.look(stopId)?.routes.orEmpty()) {
        for (trip in route.trips) {
            val at = trip.stopIds.indexOfFirst { it in platforms }
            if (at < 0) continue
            if (at == trip.stopIds.size - 1) {
                if (route !in ending) ending += route
                continue
            }
            leaving += route
            val name = route.timetable?.routeLongName?.ifEmpty { null } ?: route.longName
            val routes = out.getOrPut(routeDestination(if (trip.tripId.endsWith(OUTGOING_SUFFIX)) name else reverseRouteLongName(name))) { ArrayList() }
            if (route !in routes) routes += route
        }
    }
    ending.filter { it !in leaving }.takeIf { it.isNotEmpty() }?.let { out[null] = it.toMutableList() }
    return out.map { (name, routes) -> Destination(name, routes) }
}
