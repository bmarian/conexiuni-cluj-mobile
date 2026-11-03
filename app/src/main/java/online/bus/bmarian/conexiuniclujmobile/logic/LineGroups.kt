package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords

// Port of utils/lineGroups.ts.

@Serializable
data class GroupLine(val routeId: Int, val direction: String)

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
        if (lines.size == SyncRecords.GROUP_LINES_CAP) break
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

fun NetworkIndex.groupRoutes(lines: List<GroupLine>): List<NetworkRoute> = lines.mapNotNull { route(it.routeId) }

fun groupLabel(routes: List<NetworkRoute>) = routes.joinToString(", ") { it.shortName }

fun NetworkIndex.stopsOf(line: GroupLine): List<Int> =
    route(line.routeId)?.trips?.firstOrNull { it.tripId == tripOf(line) }?.stopIds?.toList().orEmpty()

/** The line's stop nearest you, never its last; its first without a location. */
fun NetworkIndex.boardingStop(line: GroupLine, lat: Double?, lon: Double?): Int? {
    val boardable = stopsOf(line).dropLast(1)
    if (lat == null || lon == null) return boardable.firstOrNull()
    return boardable.minByOrNull { id -> stop(id)?.let { haversineMeters(lat, lon, it.lat, it.lon) } ?: Double.MAX_VALUE }
}
