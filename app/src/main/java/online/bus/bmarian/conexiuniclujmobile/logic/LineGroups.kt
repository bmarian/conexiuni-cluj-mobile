package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords

// Port of utils/lineGroups.ts.

/** No [from]: the stop nearest you. No [to]: the end of the line. */
@Serializable
data class GroupLine(val routeId: Int, val direction: String, val from: Int? = null, val to: Int? = null)

enum class StretchEnd { From, To }

enum class StopRole { Board, Near, Leave, Pass }

data class StopRow(val stopId: Int, val role: StopRole, val paintedTop: Boolean, val paintedBottom: Boolean, val dimmed: Boolean)

/** [from] is -1 and [to] the last stop where none is chosen, or the chosen one is no longer on the line in that order. */
class Stretch(val stops: List<Int>, val from: Int, val to: Int)

private const val INTORS = ".intors"
private const val STOPS = ':'

fun tripOf(line: GroupLine) = "${line.routeId}_${line.direction}"

fun opposite(direction: String) = if (direction == "0") "1" else "0"

private val GroupLine.hasStops get() = from != null || to != null

fun lineKey(line: GroupLine) = "${line.routeId}/${line.direction}" + if (line.hasStops) "/${line.from ?: ""}/${line.to ?: ""}" else ""

fun groupKey(lines: List<GroupLine>) = lines.joinToString(",", transform = ::lineKey)

fun NetworkIndex.lineStops(line: GroupLine): List<Int> = tripStopIds(tripOf(line))

fun NetworkIndex.stretchOf(line: GroupLine): Stretch {
    val stops = lineStops(line)
    val boards = line.from?.let(stops::indexOf) ?: -1
    val from = if (boards >= stops.lastIndex) -1 else boards
    val leaves = line.to?.let { to -> (maxOf(from, 0) + 1..stops.lastIndex).firstOrNull { stops[it] == to } } ?: -1
    return Stretch(stops, from, if (leaves < 0) stops.lastIndex else leaves)
}

private fun NetworkIndex.settled(line: GroupLine): GroupLine {
    val stretch = stretchOf(line)
    return line.copy(from = stretch.stops.getOrNull(stretch.from), to = stretch.stops.getOrNull(stretch.to).takeIf { stretch.to < stretch.stops.lastIndex })
}

fun NetworkIndex.writeLines(lines: List<GroupLine>): String = lines.joinToString(",") { l ->
    val slug = route(l.routeId)?.slug?.takeIf { it.isNotEmpty() } ?: "~${l.routeId}"
    val stops = if (l.hasStops) "$STOPS${l.from ?: ""}" + (l.to?.let { "$STOPS$it" } ?: "") else ""
    slug + (if (l.direction == "1") INTORS else "") + stops
}

private fun stopNumber(text: String?) = text?.toLongOrNull()?.takeIf { it in SyncRecords.validId }?.toInt()

fun NetworkIndex.readLines(raw: String): List<GroupLine> {
    val lines = mutableListOf<GroupLine>()
    for (part in raw.split(',')) {
        if (lines.size == SyncRecords.GROUP_LINES_CAP) break
        val fields = part.split(STOPS)
        val name = fields[0]
        val direction = if (name.endsWith(INTORS)) "1" else "0"
        val slug = if (direction == "1") name.dropLast(INTORS.length) else name
        val routeId = (if (slug.startsWith("~")) slug.drop(1).toIntOrNull() else lineNamed(slug)) ?: continue
        if (route(routeId) == null || lines.any { it.routeId == routeId }) continue
        lines += settled(GroupLine(routeId, direction, stopNumber(fields.getOrNull(1)), stopNumber(fields.getOrNull(2))))
    }
    return lines
}

fun NetworkIndex.groupRoutes(lines: List<GroupLine>): List<NetworkRoute> = lines.mapNotNull { route(it.routeId) }

fun groupLabel(routes: List<NetworkRoute>) = routes.joinToString(", ") { it.shortName }

/** The chosen stop; else the line's stop nearest you before the get-off stop, its first without a location. */
fun NetworkIndex.boardingStop(line: GroupLine, lat: Double?, lon: Double?): Int? {
    val stretch = stretchOf(line)
    if (stretch.from >= 0) return stretch.stops[stretch.from]
    val boardable = stretch.stops.take(stretch.to.coerceAtLeast(0))
    if (lat == null || lon == null) return boardable.firstOrNull()
    return boardable.minByOrNull { id -> stop(id)?.let { haversineMeters(lat, lon, it.lat, it.lon) } ?: Double.MAX_VALUE }
}

fun NetworkIndex.stretchNames(line: GroupLine): Pair<String?, String?> {
    val chosen = settled(line)
    return chosen.from?.let { stop(it)?.name } to chosen.to?.let { stop(it)?.name }
}

fun NetworkIndex.flipLine(line: GroupLine): GroupLine {
    val chosen = settled(line)
    val other = GroupLine(line.routeId, opposite(line.direction))
    val back = lineStops(other)
    fun across(stopId: Int?) = stopId?.let(::stationId)?.let { station -> back.firstOrNull { stationId(it) == station } }
    return settled(other.copy(from = across(chosen.to), to = across(chosen.from)))
}

fun NetworkIndex.pickStop(line: GroupLine, end: StretchEnd, stopId: Int): Pair<GroupLine, StretchEnd> {
    val stretch = stretchOf(line)
    val chosen = settled(line)
    val last = stretch.stops.lastIndex
    val at = stretch.stops.indexOf(stopId)
    return when {
        at < 0 -> line to end
        end == StretchEnd.From -> when {
            at >= last -> line to end
            at == stretch.from -> chosen.copy(from = null) to end
            else -> chosen.copy(from = stopId, to = chosen.to.takeIf { stretch.to > at }) to StretchEnd.To
        }
        at == stretch.to -> chosen.copy(to = null) to end
        stretch.from >= 0 && at <= stretch.from -> chosen.copy(from = stopId, to = null) to end
        at == 0 -> line to end
        else -> chosen.copy(to = stopId.takeIf { at < last }) to end
    }
}

fun NetworkIndex.stopRows(line: GroupLine, nearest: Int?): List<StopRow> {
    val stretch = stretchOf(line)
    val near = nearest?.let(stretch.stops::indexOf) ?: -1
    val start = if (stretch.from >= 0) stretch.from else maxOf(near, 0)
    val leaves = stretch.to < stretch.stops.lastIndex
    return stretch.stops.mapIndexed { at, stopId ->
        val role = when {
            at == stretch.from -> StopRole.Board
            at == near -> StopRole.Near
            at == stretch.to && leaves -> StopRole.Leave
            else -> StopRole.Pass
        }
        val followed = at in start..stretch.to
        StopRow(stopId, role, paintedTop = followed && at > start, paintedBottom = followed && at < stretch.to, dimmed = !followed)
    }
}
