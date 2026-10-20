package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Port of utils/stopClusters.ts.

class StopPoint(val id: Int, val lat: Double, val lon: Double, val station: Int, val modes: Int, val rank: Int, val favorite: Boolean)

class StopGroup(val leader: Int) {
    var key = ""
    val points = ArrayList<Int>()
    val stations = HashSet<Int>()
    var modes = 0
    var favorite = false
}

/** In dp, the anchor measured from the top left. */
class ClusterBox(val width: Float, val height: Float, val anchorX: Float, val anchorY: Float)

data class GroupName(val name: String, val covered: Int)

data class Expansion(val split: Double, val apart: Double)

// MapLibre's world is 512 dp wide at zoom 0.
private const val TILE_DP = 512.0

fun lonPerDp(zoom: Double) = 360.0 / (TILE_DP * 2.0.pow(zoom))

fun modeMask(routeTypes: Iterable<Int>): Int = routeTypes.fold(0) { mask, type -> mask or (1 shl modeOf(type).ordinal) }

fun modesIn(mask: Int): List<Mode> = Mode.entries.filter { mask and (1 shl it.ordinal) != 0 }

/** Bigger modes first, then the station with more lines. */
fun stationRank(routeTypes: List<Int>): Int {
    val best = routeTypes.minOfOrNull { modeOf(it).ordinal } ?: Mode.BUS.ordinal
    return (Mode.entries.size - best) * 10_000 + routeTypes.size
}

private val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

private val bracketed = Regex("""\s*\([^)]*\)""")

private fun plainWords(name: String) = name.replace(bracketed, "").split(' ').filter { it.isNotEmpty() }

/** The start of the leader's name that the most other stations share, when it is longer than what any of them adds; bracketed qualifiers don't count. */
fun groupName(leader: String, others: List<String>): GroupName {
    val words = plainWords(leader)
    val split = others.map(::plainWords)
    var best = GroupName(leader, 0)
    for (k in 1..words.size) {
        val root = words.take(k).joinToString(" ")
        if (root.length <= words.drop(k).joinToString(" ").length) continue
        val covered = split.count { w ->
            w.size >= k && (0 until k).all { collator.compare(words[it], w[it]) == 0 } && root.length > w.drop(k).joinToString(" ").length
        }
        if (covered > 0 && covered >= best.covered) best = GroupName(root, covered)
    }
    return best
}

private class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun overlaps(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

private fun cellKey(cx: Int, cy: Int) = cx.toLong() * 0x4000000L + cy

/** Markers drop out, lowest ranked first, where they would overlap; each stop then goes to its nearest remaining marker. */
class StopClusters(
    val points: List<StopPoint>,
    private val boxOf: (StopGroup) -> ClusterBox,
    private val maxZoom: Double,
    private val step: Double,
) {
    private val levels = ArrayList<List<StopGroup>>()
    private val owners = HashMap<Int, IntArray>()
    private val xs = DoubleArray(points.size) { mercatorX(points[it].lon) }
    private val ys = DoubleArray(points.size) { mercatorY(points[it].lat) }
    private val span: Double = run {
        fun extent(v: DoubleArray) = if (v.isEmpty()) 0.0 else v.max() - v.min()
        max(extent(xs), extent(ys)).pow(2)
    }
    val indexOf: Map<Int, Int> = points.withIndex().associate { (i, p) -> p.id to i }

    fun at(zoom: Double): List<StopGroup> {
        val level = levelOf(zoom)
        while (levels.size <= level) build()
        return levels[level]
    }

    fun at(group: StopGroup): LatLon = points[group.leader].let { LatLon(it.lat, it.lon) }

    fun members(group: StopGroup): List<LatLon> = group.points.map { LatLon(points[it].lat, points[it].lon) }

    /** The zooms at which the group first breaks up, and at which each of its stations stands alone; null when no zoom breaks it up. */
    fun expansion(group: StopGroup, zoom: Double): Expansion? {
        var split: Double? = null
        for (level in levelOf(zoom) - 1 downTo 0) {
            val owner = ownerAt(level)
            val groups = levels[level]
            val first = owner[group.points[0]]
            if (split == null && group.points.any { owner[it] != first }) split = zoomOf(level)
            if (group.points.all { groups[owner[it]].stations.size == 1 }) return Expansion(split ?: zoomOf(level), zoomOf(level))
        }
        return split?.let { Expansion(it, maxZoom) }
    }

    private fun levelOf(zoom: Double) = max(0, ceil((maxZoom - zoom) / step - 1e-9).toInt())

    private fun zoomOf(level: Int) = maxZoom - level * step

    private fun ownerAt(level: Int): IntArray = owners.getOrPut(level) {
        IntArray(points.size).also { out -> levels[level].forEachIndexed { g, group -> group.points.forEach { out[it] = g } } }
    }

    private fun rect(group: StopGroup, dp: Double): Rect {
        val box = boxOf(group)
        val left = xs[group.leader] * dp - box.anchorX
        val top = ys[group.leader] * dp - box.anchorY
        return Rect(left, top, left + box.width, top + box.height)
    }

    private fun build() {
        val level = levels.size
        val dp = TILE_DP * 2.0.pow(zoomOf(level))
        var groups = if (level > 0) levels[level - 1] else assign(points.indices.toList())
        while (true) {
            val kept = sweep(groups, dp)
            if (kept.size == groups.size) break
            groups = assign(kept)
        }
        levels += groups
    }

    /** The leaders whose markers fit, highest ranked first. */
    private fun sweep(groups: List<StopGroup>, dp: Double): List<Int> {
        val order = groups.sortedWith(compareByDescending<StopGroup> { points[it.leader].rank }.thenByDescending { it.points.size }.thenBy { it.leader })
        val rects = order.map { rect(it, dp) }
        val cell = rects.maxOf { max(it.right - it.left, it.bottom - it.top) }
        val grid = HashMap<Long, MutableList<Rect>>()
        val kept = ArrayList<Int>()
        order.forEachIndexed { i, group ->
            val r = rects[i]
            val x0 = floor(r.left / cell).toInt()
            val y0 = floor(r.top / cell).toInt()
            val clear = (x0 - 1..floor(r.right / cell).toInt()).all { cx ->
                (y0 - 1..floor(r.bottom / cell).toInt()).all { cy -> grid[cellKey(cx, cy)]?.none { it.overlaps(r) } ?: true }
            }
            if (clear) {
                kept += group.leader
                grid.getOrPut(cellKey(x0, y0)) { ArrayList() } += r
            }
        }
        return kept
    }

    /** Every stop goes to its nearest leader. */
    private fun assign(leaders: List<Int>): List<StopGroup> {
        val cell = sqrt(span / leaders.size).takeIf { it > 0 } ?: 1.0
        val grid = HashMap<Long, MutableList<Int>>()
        for (l in leaders) grid.getOrPut(cellKey(floor(xs[l] / cell).toInt(), floor(ys[l] / cell).toInt())) { ArrayList() } += l
        val owner = IntArray(points.size) { -1 }
        for (l in leaders) owner[l] = l
        for (i in points.indices) {
            if (owner[i] >= 0) continue
            val x = xs[i]
            val y = ys[i]
            val cx = floor(x / cell).toInt()
            val cy = floor(y / cell).toInt()
            var best = -1
            var bestD = Double.POSITIVE_INFINITY
            var ring = 0
            while (best < 0 || (ring - 1) * cell < sqrt(bestD)) {
                for (dx in -ring..ring) for (dy in -ring..ring) {
                    if (max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != ring) continue
                    for (l in grid[cellKey(cx + dx, cy + dy)] ?: continue) {
                        val d = (xs[l] - x).pow(2) + (ys[l] - y).pow(2)
                        if (d < bestD) {
                            bestD = d
                            best = l
                        }
                    }
                }
                ring++
            }
            owner[i] = best
        }
        val byLeader = LinkedHashMap<Int, StopGroup>()
        for (l in leaders) byLeader[l] = StopGroup(l)
        for (i in points.indices) {
            val group = byLeader.getValue(owner[i])
            val p = points[i]
            group.points += i
            group.stations += p.station
            group.modes = group.modes or p.modes
            group.favorite = group.favorite || p.favorite
        }
        for (group in byLeader.values) group.key = "${group.leader}:${group.points.size}:${group.points.fold(0) { h, i -> h * 31 + i }}"
        return byLeader.values.toList()
    }
}
