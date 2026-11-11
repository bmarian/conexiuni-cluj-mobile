package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.DestinationSummary
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.Stop

/** Port of utils/searchExamples.ts. */
class SearchExamples(val line: NetworkRoute?, val stop: Stop?, val place: String?) {
    val isEmpty get() = line == null && stop == null && place == null
}

private fun <T> busiest(items: Iterable<T>, score: (T) -> Int): T? = items.filter { score(it) > 0 }.maxByOrNull(score)

/** The city's own busiest line, station and neighbourhood; outside neighbourhoods the biggest area is the city itself. */
fun searchExamples(index: NetworkIndex, areas: List<DestinationSummary>) = SearchExamples(
    line = busiest(index.bundle.routes) { it.timetable?.weekdays?.entries?.size ?: 0 },
    stop = busiest(index.bundle.stops) { index.look(it.stopId)?.routes?.size ?: 0 },
    place = busiest(areas.filter { it.district }) { it.stops }?.name,
)
