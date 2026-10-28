package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.StopTime

/**
 * Port of utils/stopPairs.ts: a stop keeps its key in the other direction at the same station, or across the street: nearer
 * than half the gap to its neighbours.
 */
fun directionKeys(there: List<StopTime>, back: List<StopTime>, stationOf: (StopTime) -> Int): Pair<List<String>, List<String>> {
    val counts = HashMap<Int, Int>()
    val thereKeys = there.map { stop ->
        val station = stationOf(stop)
        val n = counts[station] ?: 0
        counts[station] = n + 1
        "$station-$n"
    }
    fun apart(a: StopTime, b: StopTime) = haversineMeters(a.lat, a.lon, b.lat, b.lon)
    val gaps = back.indices.map { j ->
        listOfNotNull(back.getOrNull(j - 1), back.getOrNull(j + 1)).minOfOrNull { apart(back[j], it) } ?: Double.POSITIVE_INFINITY
    }
    val pairs = back.indices.flatMap { j ->
        there.indices.map { i -> Triple(i, j, if (stationOf(back[j]) == stationOf(there[i])) 0.0 else apart(back[j], there[i])) }
    }
        .filter { (_, j, d) -> d == 0.0 || d < gaps[j] / 2 }
        .sortedBy { it.third }
    val taken = HashSet<Int>()
    val backKeys = arrayOfNulls<String>(back.size)
    for ((i, j) in pairs) {
        if (i in taken || backKeys[j] != null) continue
        taken += i
        backKeys[j] = thereKeys[i]
    }
    return thereKeys to backKeys.mapIndexed { j, key -> key ?: "back-${back[j].stopId}-$j" }
}
