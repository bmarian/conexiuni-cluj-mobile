package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex

// Port of utils/lineGroups.ts.

@Serializable
data class GroupLine(val routeId: Int, val direction: String)

data class LegLine(val routeId: Int, val direction: String, val stopId: Int) {
    val line get() = GroupLine(routeId, direction)
}

data class Leg(val lines: List<LegLine>, val alight: List<LegLine>? = null)

data class Departure(val routeId: Int, val at: Long, val live: Boolean, val vehicleId: Int? = null)

data class LegTimes(val shown: List<Departure>, val missed: Departure? = null, val caught: Departure? = null, val arrival: Long? = null)

data class ChainLine(val name: String, val color: String)

data class ChainLeg(val lines: List<ChainLine>, val change: Boolean)

private const val INTORS = ".intors"

fun tripOf(line: GroupLine) = "${line.routeId}_${line.direction}"

fun groupKey(lines: List<GroupLine>) = lines.joinToString(",") { "${it.routeId}/${it.direction}" }

fun NetworkIndex.writeLines(lines: List<GroupLine>): String = lines.joinToString(",") { l ->
    val slug = route(l.routeId)?.slug?.takeIf { it.isNotEmpty() } ?: "~${l.routeId}"
    if (l.direction == "1") "$slug$INTORS" else slug
}

fun NetworkIndex.readLines(raw: String): List<GroupLine> {
    val lines = mutableListOf<GroupLine>()
    for (part in raw.split(',')) {
        val direction = if (part.endsWith(INTORS)) "1" else "0"
        val slug = if (direction == "1") part.dropLast(INTORS.length) else part
        val routeId = (if (slug.startsWith("~")) slug.drop(1).toIntOrNull() else lineNamed(slug)) ?: continue
        if (route(routeId) == null || lines.any { it.routeId == routeId }) continue
        lines += GroupLine(routeId, direction)
    }
    return lines
}

fun NetworkIndex.readKey(key: String): List<GroupLine> = key.split(',').mapNotNull { part ->
    val (id, direction) = part.split('/').takeIf { it.size == 2 } ?: return@mapNotNull null
    val routeId = id.toIntOrNull()?.takeIf { route(it) != null } ?: return@mapNotNull null
    GroupLine(routeId, direction).takeIf { direction == "0" || direction == "1" }
}

fun NetworkIndex.stopsOf(line: GroupLine): List<Int> =
    route(line.routeId)?.trips?.firstOrNull { it.tripId == tripOf(line) }?.stopIds?.toList().orEmpty()

fun NetworkIndex.nearestBoarding(stops: List<Int>, lat: Double?, lon: Double?): Int {
    val boardable = stops.dropLast(1)
    if (lat == null || lon == null) return boardable.first()
    return boardable.minByOrNull { id -> stop(id)?.let { haversineMeters(lat, lon, it.lat, it.lon) } ?: Double.MAX_VALUE } ?: boardable.first()
}

/** Changes are found from each line's first stop, so where you are never hides one. */
fun NetworkIndex.legsNear(lines: List<GroupLine>, lat: Double?, lon: Double?): List<Leg> {
    val plain = planLegs(lines) { stops, _ -> stops.first() }
    val leftAt = plain.drop(1).flatMap { it.alight.orEmpty() }.associate { it.routeId to stationId(it.stopId) }
    return planLegs(lines) { stops, line ->
        val at = leftAt[line.routeId]?.let { station -> stops.indexOfFirst { stationId(it) == station } } ?: -1
        nearestBoarding(if (at > 0) stops.take(at + 1) else stops, lat, lon)
    }
}

private data class Meet(val rode: Int, val station: Int, val board: Int)

/** Where the line leaves the leg's way after boarding, else where it first meets it. */
private fun NetworkIndex.change(leg: Leg, stops: List<Int>): Pair<Int, List<LegLine>>? {
    var best: Meet? = null
    for (prev in leg.lines) {
        val prevStops = stopsOf(prev.line)
        val from = prevStops.indexOf(prev.stopId)
        val position = mutableMapOf<Int, Int>()
        for (i in from + 1 until prevStops.size) position.putIfAbsent(stationId(prevStops[i]), i)
        var found: Meet? = null
        for (i in from + 1 until prevStops.size) {
            val station = stationId(prevStops[i])
            val at = stops.indices.firstOrNull { k -> k < stops.lastIndex && stationId(stops[k]) == station } ?: continue
            val meet = Meet(i - from, station, stops[at])
            if (found == null) found = meet
            val next = position[stationId(stops[at + 1])]
            if (next != null && next > i) continue
            found = meet
            break
        }
        if (found != null && (best == null || found.rode < best.rode)) best = found
    }
    val (_, station, board) = best ?: return null
    val alight = leg.lines.mapNotNull { prev ->
        val prevStops = stopsOf(prev.line)
        prevStops.drop(prevStops.indexOf(prev.stopId) + 1).firstOrNull { stationId(it) == station }?.let { LegLine(prev.routeId, prev.direction, it) }
    }
    return board to alight
}

/** A line through the boarding station is another way to ride, not a change. */
fun NetworkIndex.meetingLines(line: LegLine): List<Pair<LegLine, Int>> {
    val stops = stopsOf(line.line)
    val boarding = stationId(line.stopId)
    val position = mutableMapOf<Int, Int>()
    stops.drop(stops.indexOf(line.stopId) + 1).forEachIndexed { i, id -> position.putIfAbsent(stationId(id), i + 1) }
    val found = linkedMapOf<Int, Pair<LegLine, Int>>()
    for (route in bundle.routes) {
        if (route.routeId == line.routeId || route.trips.any { t -> t.stopIds.any { stationId(it) == boarding } }) continue
        for (trip in route.trips) {
            val ids = trip.stopIds
            for (k in 0 until ids.size - 1) {
                val rode = position[stationId(ids[k])] ?: continue
                val next = position[stationId(ids[k + 1])]
                if (next != null && next > rode) continue
                val known = found[route.routeId]
                if (known != null && known.second <= rode) continue
                found[route.routeId] = LegLine(route.routeId, if (trip.tripId.endsWith("_1")) "1" else "0", ids[k]) to rode
            }
        }
    }
    return found.values.sortedBy { it.second }
}

fun NetworkIndex.planLegs(lines: List<GroupLine>, startStop: (List<Int>, GroupLine) -> Int): List<Leg> {
    val legs = mutableListOf<Leg>()
    for (line in lines) {
        val stops = stopsOf(line)
        if (stops.size < 2) continue
        val leg = legs.lastOrNull()
        if (leg != null) {
            val first = leg.lines.first()
            val firstStops = stopsOf(first.line)
            val boardAt = firstStops.indexOf(first.stopId)
            val here = stops.indices.firstOrNull { k -> k < stops.lastIndex && stationId(stops[k]) == stationId(first.stopId) }
            if (here != null) {
                val sameWay = boardAt >= 0 && boardAt < firstStops.lastIndex && stationId(stops[here + 1]) == stationId(firstStops[boardAt + 1])
                val joined = LegLine(line.routeId, line.direction, stops[here])
                if (sameWay) legs[legs.lastIndex] = leg.copy(lines = leg.lines + joined) else legs += Leg(listOf(joined))
                continue
            }
            val changed = change(leg, stops)
            if (changed != null) {
                legs += Leg(listOf(LegLine(line.routeId, line.direction, changed.first)), changed.second)
                continue
            }
        }
        legs += Leg(listOf(LegLine(line.routeId, line.direction, startStop(stops, line))))
    }
    return legs
}

fun timeLegs(
    legs: List<Leg>,
    departures: (LegLine) -> List<Departure>,
    arrivals: (LegLine) -> List<Departure>,
    walkMs: (Int, Int) -> Long,
    shown: Int,
): List<LegTimes> {
    val out = mutableListOf<LegTimes>()
    legs.forEachIndexed { i, leg ->
        val all = leg.lines.flatMap(departures).sortedBy { it.at }
        val before = out.getOrNull(i - 1)?.caught
        val alight = before?.let { b -> leg.alight?.firstOrNull { it.routeId == b.routeId } }
        if (before == null || alight == null) {
            out += LegTimes(all.take(shown), caught = all.firstOrNull())
            return@forEachIndexed
        }
        val coming = arrivals(alight).filter { it.at > before.at }
        val reached = before.vehicleId?.let { v -> coming.firstOrNull { it.vehicleId == v } } ?: coming.firstOrNull()
        if (reached == null) {
            out += LegTimes(all.take(shown))
            return@forEachIndexed
        }
        fun ready(d: Departure) = reached.at + walkMs(alight.stopId, leg.lines.first { it.routeId == d.routeId }.stopId)
        val catchable = all.filter { it.at >= ready(it) }
        out += LegTimes(catchable.take(shown), all.lastOrNull { it.at < ready(it) }, catchable.firstOrNull(), reached.at)
    }
    return out
}

fun NetworkIndex.chainOf(legs: List<Leg>, color: (String, String) -> String): List<ChainLeg> = legs.map { leg ->
    ChainLeg(
        leg.lines.mapNotNull { l -> route(l.routeId)?.let { ChainLine(it.shortName, color(it.shortName, it.color)) } },
        leg.alight != null,
    )
}

fun chainText(chain: List<ChainLeg>) = chain.mapIndexed { i, leg ->
    (if (i == 0) "" else if (leg.change) " → " else " + ") + leg.lines.joinToString(" / ") { it.name }
}.joinToString("")
