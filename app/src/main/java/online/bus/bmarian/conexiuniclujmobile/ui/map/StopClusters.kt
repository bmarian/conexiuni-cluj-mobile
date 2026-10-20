package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Port of utils/stopClusters.ts.

class StopPoint(val id: Int, val lat: Double, val lon: Double, val station: Int, val modes: Int, val rank: Int, val favorite: Boolean)

class StopGroup(val key: String, val leader: Int, val points: List<Int>, val stations: Set<Int>, val modes: Int, val favorite: Boolean)

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

// Groups are named on the clustering thread and on the main one.
private fun sameWord(a: String, b: String) = synchronized(collator) { collator.compare(a, b) == 0 }

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
            w.size >= k && (0 until k).all { sameWord(words[it], w[it]) } && root.length > w.drop(k).joinToString(" ").length
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
    @Volatile
    private var levels: List<List<StopGroup>> = emptyList()
    private var pending: List<StopGroup>? = null
    private val boxes = HashMap<String, ClusterBox>()
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
        levels.getOrNull(level)?.let { return it }
        synchronized(this) { while (levels.size <= level) advance() }
        return levels[level]
    }

    fun ready(zoom: Double) = levels.size > levelOf(zoom)

    /** One sweep of the next level and the regrouping it calls for; built levels stay readable from any thread meanwhile. */
    @Synchronized
    fun advance() {
        val level = levels.size
        val groups = pending ?: if (level > 0) levels[level - 1] else points.indices.map { group(it, listOf(it)) }
        val kept = sweep(groups, TILE_DP * 2.0.pow(zoomOf(level)))
        pending = if (kept.size == groups.size) null else assign(groups, kept)
        if (pending == null) levels = levels + listOf(groups)
    }

    fun at(group: StopGroup): LatLon = points[group.leader].let { LatLon(it.lat, it.lon) }

    fun members(group: StopGroup): List<LatLon> = group.points.map { LatLon(points[it].lat, points[it].lon) }

    /** The zooms at which the group first breaks up, and at which each of its stations stands alone; null when no zoom breaks it up. */
    fun expansion(group: StopGroup, zoom: Double): Expansion? {
        val built = levels
        var split: Double? = null
        for (level in min(levelOf(zoom), built.size) - 1 downTo 0) {
            val owner = ownerAt(built, level)
            val groups = built[level]
            val first = owner[group.points[0]]
            if (split == null && group.points.any { owner[it] != first }) split = zoomOf(level)
            if (group.points.all { groups[owner[it]].stations.size == 1 }) return Expansion(split ?: zoomOf(level), zoomOf(level))
        }
        return split?.let { Expansion(it, maxZoom) }
    }

    private fun levelOf(zoom: Double) = max(0, ceil((maxZoom - zoom) / step - 1e-9).toInt())

    private fun zoomOf(level: Int) = maxZoom - level * step

    private fun ownerAt(built: List<List<StopGroup>>, level: Int): IntArray = owners.getOrPut(level) {
        IntArray(points.size).also { out -> built[level].forEachIndexed { g, group -> group.points.forEach { out[it] = g } } }
    }

    private fun rect(group: StopGroup, dp: Double): Rect {
        val box = boxes.getOrPut(group.key) { boxOf(group) }
        val left = xs[group.leader] * dp - box.anchorX
        val top = ys[group.leader] * dp - box.anchorY
        return Rect(left, top, left + box.width, top + box.height)
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

    /** Leaders only ever drop out, so only the stops of dropped groups need their nearest remaining leader. */
    private fun assign(groups: List<StopGroup>, kept: List<Int>): List<StopGroup> {
        val cell = sqrt(span / kept.size).takeIf { it > 0 } ?: 1.0
        val grid = HashMap<Long, MutableList<Int>>()
        for (l in kept) grid.getOrPut(cellKey(floor(xs[l] / cell).toInt(), floor(ys[l] / cell).toInt())) { ArrayList() } += l
        val alive = kept.toHashSet()
        val gained = HashMap<Int, MutableList<Int>>()
        for (group in groups) {
            if (group.leader in alive) continue
            for (i in group.points) gained.getOrPut(nearest(i, grid, cell)) { ArrayList() } += i
        }
        val byLeader = groups.associateBy { it.leader }
        return kept.map { l ->
            val group = byLeader.getValue(l)
            val extra = gained[l]
            if (extra == null) group else group(l, (group.points + extra).sorted())
        }
    }

    private fun nearest(i: Int, grid: Map<Long, List<Int>>, cell: Double): Int {
        val x = xs[i]
        val y = ys[i]
        val cx = floor(x / cell).toInt()
        val cy = floor(y / cell).toInt()
        var best = -1
        var bestD = Double.POSITIVE_INFINITY
        var ring = 0
        while (best < 0 || (ring - 1) * cell < sqrt(bestD)) {
            for (dx in -ring..ring) for (dy in -ring..ring) {
                if (max(abs(dx), abs(dy)) != ring) continue
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
        return best
    }

    private fun group(leader: Int, members: List<Int>): StopGroup {
        val stations = HashSet<Int>()
        var modes = 0
        var favorite = false
        for (i in members) {
            val p = points[i]
            stations += p.station
            modes = modes or p.modes
            favorite = favorite || p.favorite
        }
        return StopGroup("$leader:${members.size}:${members.fold(0) { h, i -> h * 31 + i }}", leader, members, stations, modes, favorite)
    }
}
