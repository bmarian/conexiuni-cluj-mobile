package online.bus.bmarian.conexiuniclujmobile.ui.map

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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

fun spiderLegs(count: Int): List<SpiderLeg> {
    if (count <= CIRCLE_MAX) {
        val r = max(MIN_RADIUS_DP, count * FOOT_DP / (2 * PI))
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

fun spiderRadius(legs: List<SpiderLeg>): Int = ceil(legs.maxOfOrNull { hypot(it.x.toDouble(), it.y.toDouble()) } ?: 0.0).toInt()
