package online.bus.bmarian.conexiuniclujmobile.ui.map

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Port of utils/spider.ts.

/** In dp from the group's spot. */
data class SpiderLeg(val x: Int, val y: Int, val left: Boolean)

private const val CIRCLE_MAX = 8
private const val FOOT_DP = 48.0
private const val MIN_RADIUS_DP = 44.0
private const val SPIRAL_START_DP = 28.0
private const val SPIRAL_FOOT_DP = 48.0
private const val SPIRAL_GROWTH_DP = 10.0

private fun leg(r: Double, angle: Double) = SpiderLeg((r * cos(angle)).roundToInt(), (r * sin(angle)).roundToInt(), cos(angle) < -1e-6)

private fun circleRadius(count: Int) = max(MIN_RADIUS_DP, count * FOOT_DP / (2 * PI))

fun spiderLegs(count: Int): List<SpiderLeg> {
    if (count <= CIRCLE_MAX) {
        val r = circleRadius(count)
        return List(count) { leg(r, 2 * PI * it / count) }
    }
    val legs = ArrayList<SpiderLeg>(count)
    var r = SPIRAL_START_DP
    var angle = 0.0
    for (i in 0 until count) {
        angle += SPIRAL_FOOT_DP / r + i * 0.0005
        legs += leg(r, angle)
        r += 2 * PI * SPIRAL_GROWTH_DP / angle
    }
    return legs
}

fun spiderLegsToward(screenAngles: List<Double>): List<SpiderLeg> {
    val count = screenAngles.size
    if (count > CIRCLE_MAX) return spiderLegs(count)
    val order = screenAngles.indices.sortedBy { screenAngles[it] }
    val step = 2 * PI / count
    var x = 0.0
    var y = 0.0
    order.forEachIndexed { k, i ->
        x += cos(screenAngles[i] - k * step)
        y += sin(screenAngles[i] - k * step)
    }
    val turn = atan2(y, x)
    val r = circleRadius(count)
    val slot = order.withIndex().associate { (k, i) -> i to k }
    return screenAngles.indices.map { leg(r, turn + slot.getValue(it) * step) }
}

class SpiderPlace(val stationX: Double, val stationY: Double, val icon: ClusterBox, val labelWidth: Double, val labelHeight: Double)

const val SPIDER_LABEL_GAP_DP = 4f
private const val RINGS = 3

private fun placeBoxes(place: SpiderPlace, leg: SpiderLeg): List<Rect> {
    val icon = boxAt(leg.x.toDouble(), leg.y.toDouble(), place.icon)
    val middle = (icon.top + icon.bottom) / 2
    val start = if (leg.left) icon.left - SPIDER_LABEL_GAP_DP - place.labelWidth else icon.right + SPIDER_LABEL_GAP_DP
    return listOf(icon, Rect(start, middle - place.labelHeight / 2, start + place.labelWidth, middle + place.labelHeight / 2))
}

private fun overlap(a: Rect, b: Rect) =
    max(0.0, min(a.right, b.right) - max(a.left, b.left)) * max(0.0, min(a.bottom, b.bottom) - max(a.top, b.top))

// Port of spiderLegsClear in utils/spider.ts.
internal fun spiderLegsClear(places: List<SpiderPlace>, obstacles: List<Rect>): List<SpiderLeg> {
    val taken = obstacles.toMutableList()
    return places.map { place ->
        val toward = atan2(place.stationY, place.stationX)
        var best: SpiderLeg? = null
        var bestCovered = 0.0
        var bestFar = 0.0
        for (ring in 0 until RINGS) {
            val r = MIN_RADIUS_DP + ring * FOOT_DP
            val step = place.icon.width / r
            var k = 0
            while (k * step <= PI) {
                for (turn in if (k == 0) listOf(0.0) else listOf(k * step, -k * step)) {
                    val l = leg(r, toward + turn)
                    val boxes = placeBoxes(place, l)
                    val covered = taken.sumOf { overlap(boxes[0], it) + overlap(boxes[1], it) }
                    val far = hypot(l.x - place.stationX, l.y - place.stationY)
                    if (best == null || covered < bestCovered || (covered == bestCovered && far < bestFar)) {
                        best = l
                        bestCovered = covered
                        bestFar = far
                    }
                }
                k++
            }
        }
        val chosen = best!!
        taken += placeBoxes(place, chosen)
        chosen
    }
}

fun spiderRadius(legs: List<SpiderLeg>): Int = ceil(legs.maxOfOrNull { hypot(it.x.toDouble(), it.y.toDouble()) } ?: 0.0).toInt()
