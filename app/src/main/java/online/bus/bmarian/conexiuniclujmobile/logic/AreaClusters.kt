package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Port of utils/areaClusters.ts.

@Serializable
class Area(val name: String, val lat: Double, val lon: Double, val place: String)

private val PLACES = listOf("city", "town", "village", "suburb", "quarter", "neighbourhood")

private fun nearestOf(x: Double, y: Double, among: List<Int>, xs: DoubleArray, ys: DoubleArray): Int {
    var best = -1
    var bestD = Double.POSITIVE_INFINITY
    for (k in among) {
        val d = (xs[k] - x).pow(2) + (ys[k] - y).pow(2)
        if (d < bestD) {
            bestD = d
            best = k
        }
    }
    return best
}

class AreaClusters(private val stations: StopClusters, areas: List<Area>, private val boxOf: (StopGroup) -> ClusterBox) {
    val areas: List<Area>

    @Volatile
    private var levels: List<List<StopGroup>> = emptyList()
    private val boxes = ConcurrentHashMap<String, ClusterBox>()
    private val points = stations.points
    private val sx = DoubleArray(points.size) { mercatorX(points[it].lon) }
    private val sy = DoubleArray(points.size) { mercatorY(points[it].lat) }
    private val ax: DoubleArray
    private val ay: DoubleArray
    private val home: IntArray
    private val rank: LongArray

    init {
        val known = areas.filter { it.place in PLACES }
        val kx = DoubleArray(known.size) { mercatorX(known[it].lon) }
        val ky = DoubleArray(known.size) { mercatorY(known[it].lat) }
        val all = known.indices.toList()
        val nearest = IntArray(points.size) { nearestOf(sx[it], sy[it], all, kx, ky) }
        val owned = IntArray(known.size)
        for (k in nearest) if (k >= 0) owned[k]++
        val kept = all.filter { owned[it] > 0 }
        val renumber = kept.withIndex().associate { (i, k) -> k to i }
        this.areas = kept.map { known[it] }
        ax = DoubleArray(kept.size) { kx[kept[it]] }
        ay = DoubleArray(kept.size) { ky[kept[it]] }
        home = IntArray(points.size) { renumber[nearest[it]] ?: -1 }
        rank = LongArray(kept.size) { (PLACES.size - PLACES.indexOf(known[kept[it]].place)).toLong() * points.size + owned[kept[it]] }
    }

    fun at(zoom: Double): List<StopGroup> {
        val level = stations.levelOf(zoom)
        levels.getOrNull(level)?.let { return it }
        synchronized(this) { while (levels.size <= level) advance() }
        return levels[level]
    }

    fun ready(zoom: Double) = levels.size > stations.levelOf(zoom)

    @Synchronized
    fun advance() {
        val level = levels.size
        var groups = if (level > 0) levels[level - 1] else initial()
        val dp = TILE_DP * 2.0.pow(stations.zoomOf(level))
        var kept = sweep(groups, dp)
        while (kept.size < groups.size) {
            groups = merge(groups, kept)
            kept = sweep(groups, dp)
        }
        levels = levels + listOf(groups)
    }

    fun at(group: StopGroup): LatLon = areas[group.area!!].let { LatLon(it.lat, it.lon) }

    fun box(group: StopGroup): ClusterBox = boxes.getOrPut(group.key) { boxOf(group) }

    fun name(group: StopGroup): String = areas[group.area!!].name

    private fun initial(): List<StopGroup> {
        val members = List(areas.size) { ArrayList<Int>() }
        home.forEachIndexed { i, k -> if (k >= 0) members[k] += i }
        return members.mapIndexed { k, points -> group(k, points) }
    }

    private fun rect(group: StopGroup, dp: Double): Rect {
        return boxAt(ax[group.area!!] * dp, ay[group.area] * dp, box(group))
    }

    private fun sweep(groups: List<StopGroup>, dp: Double): List<StopGroup> {
        val kept = ArrayList<StopGroup>()
        val rects = ArrayList<Rect>()
        for (group in groups.sortedByDescending { rank[it.area!!] }) {
            val r = rect(group, dp)
            if (rects.any { it.overlaps(r) }) continue
            kept += group
            rects += r
        }
        return kept
    }

    // A dropped area's stops go to the nearest area left that ranks at least as high, so a small place never names a big one.
    private fun merge(groups: List<StopGroup>, kept: List<StopGroup>): List<StopGroup> {
        val alive = kept.map { it.area!! }
        val members = LinkedHashMap<Int, MutableList<Int>>()
        for (group in kept) members[group.area!!] = group.points.toMutableList()
        val keptKeys = kept.mapTo(HashSet()) { it.key }
        for (group in groups) {
            if (group.key in keptKeys) continue
            val above = alive.filter { rank[it] >= rank[group.area!!] }
            for (i in group.points) members.getValue(nearestOf(sx[i], sy[i], above, ax, ay)) += i
        }
        return members.map { (k, points) -> group(k, points.sorted()) }
    }

    private fun group(k: Int, members: List<Int>): StopGroup {
        val leader = members.maxByOrNull { points[it].rank } ?: -1
        return groupOf(points, leader, members, "a$k", k)
    }
}

// Areas take over once most station groups are too big to show each station.
class MapClusters(val stations: StopClusters, private val areas: AreaClusters?, private val detail: Int) {
    @Volatile
    private var checked = 0

    @Volatile
    private var crossing: Int? = null

    val points: List<StopPoint> get() = stations.points

    fun at(zoom: Double): List<StopGroup> {
        if (far(zoom)) return areas!!.at(zoom)
        val groups = stations.at(zoom)
        return if (far(zoom)) areas!!.at(zoom) else groups
    }

    fun ready(zoom: Double) = if (far(zoom)) areas!!.ready(zoom) else stations.ready(zoom)

    fun advance() {
        settle()
        if (crossing == null) stations.advance() else areas!!.advance()
    }

    fun at(group: StopGroup): LatLon = if (group.area != null) areas!!.at(group) else stations.at(group)

    fun members(group: StopGroup): List<LatLon> = stations.members(group)

    fun box(group: StopGroup): ClusterBox = if (group.area != null) areas!!.box(group) else stations.box(group)

    fun expansion(group: StopGroup, zoom: Double): Expansion? = if (group.area != null) null else stations.expansion(group, zoom)

    fun areaName(group: StopGroup): String? = if (group.area != null) areas!!.name(group) else null

    fun lowestStationZoom(): Double? = crossing?.let { stations.zoomOf(it - 1) }

    // Dp from the group's spot; the other markers as they span the level's zoom range.
    internal fun surroundings(group: StopGroup, zoom: Double): Pair<List<Rect>, (LatLon) -> Pair<Double, Double>> {
        val level = stations.levelOf(zoom)
        val ends = listOf(stations.zoomOf(level), stations.zoomOf(max(0, level - 1)))
        val centre = at(group)
        fun offset(to: LatLon, z: Double): Pair<Double, Double> {
            val dp = TILE_DP * 2.0.pow(z)
            return (mercatorX(to.lon) - mercatorX(centre.lon)) * dp to (mercatorY(to.lat) - mercatorY(centre.lat)) * dp
        }
        val obstacles = at(zoom).filter { it !== group }.map { g ->
            val (a, b) = ends.map { z -> offset(at(g), z).let { (x, y) -> boxAt(x, y, box(g)) } }
            Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
        } + boxAt(0.0, 0.0, box(group))
        return obstacles to { to -> offset(to, ends[0]) }
    }

    private fun far(zoom: Double): Boolean {
        settle()
        val c = crossing
        return c != null && stations.levelOf(zoom) >= c
    }

    @Synchronized
    private fun settle() {
        if (areas == null) return
        while (crossing == null && checked < stations.depth) {
            val groups = stations.at(stations.zoomOf(checked))
            if (2 * groups.count { it.stations.size > detail } > groups.size) crossing = checked
            checked++
        }
    }
}
