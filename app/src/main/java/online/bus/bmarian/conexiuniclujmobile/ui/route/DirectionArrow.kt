package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import online.bus.bmarian.conexiuniclujmobile.ui.map.Snappy
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Port of utils/directionArrow.ts and components/ui/DirectionArrow.vue.

private const val TAPS = 2
private const val TAP_MS = 240
private const val TAP_DP = 5f
private const val ORBIT_MS = 1000
private const val ORBIT_DP = 6.0
const val ARROW_MS = TAPS * TAP_MS + ORBIT_MS
private const val ARROW_BACK_MS = 320
// Waits out the page's slide-in.
private const val ARROW_OPEN_DELAY_MS = 500L

/** x and y in dp. */
class ArrowPose(val x: Float, val y: Float, val heading: Double)

/** Where the arrow is at [ms]: two taps along compass [rest], then a spiral landing on [heading]. */
fun arrowPose(rest: Double, heading: Double, ms: Float): ArrowPose {
    if (ms < TAPS * TAP_MS) {
        val k = ms % TAP_MS / TAP_MS
        val d = TAP_DP * if (k < 0.3f) Snappy.transform(k / 0.3f) else 1 - EaseInOutSine.transform((k - 0.3f) / 0.7f)
        val a = Math.toRadians(rest)
        return ArrowPose(d * sin(a).toFloat(), -d * cos(a).toFloat(), rest)
    }
    val turn = if (sin(Math.toRadians(rest)) > 0) -1 else 1
    // Leaving radially along rest and arriving radially from the far side ends on heading.
    val base = ((turn * (heading + 180 - rest)) % 360 + 360) % 360
    val sweep = Math.toRadians(turn * if (base < 270) base + 360 else base)
    val v = EaseInOutSine.transform(((ms - TAPS * TAP_MS) / ORBIT_MS).coerceAtMost(1f)).toDouble()
    val phi = Math.toRadians(rest) + sweep * v
    val r = ORBIT_DP * sin(PI * v)
    val facing = phi + atan2(sin(PI * v) * sweep, PI * cos(PI * v))
    return ArrowPose((r * sin(phi)).toFloat(), (-r * cos(phi)).toFloat(), Math.toDegrees(facing))
}

@Composable
fun DirectionArrow(icon: ImageVector, rest: Double, heading: Double?, on: Boolean) {
    // Saved with the list item, so scrolling back doesn't replay it.
    var opened by rememberSaveable { mutableStateOf(false) }
    val run = remember { Animatable(if (on && !opened) 0f else 1f) }
    var shown by remember { mutableStateOf(on) }
    var leaving by remember { mutableStateOf(ArrowPose(0f, 0f, rest)) }
    fun pose(): ArrowPose {
        val k = run.value
        return when {
            heading == null -> ArrowPose(0f, 0f, rest)
            shown -> arrowPose(rest, heading, k * ARROW_MS)
            else -> ArrowPose(leaving.x * (1 - k), leaving.y * (1 - k), leaving.heading + (((rest - leaving.heading) % 360 + 540) % 360 - 180) * k)
        }
    }
    LaunchedEffect(heading != null) {
        if (heading == null || !shown || opened) return@LaunchedEffect
        delay(ARROW_OPEN_DELAY_MS)
        run.animateTo(1f, tween(ARROW_MS, easing = LinearEasing))
        opened = true
    }
    LaunchedEffect(on) {
        if (on == shown) return@LaunchedEffect
        opened = true
        leaving = pose()
        shown = on
        run.snapTo(0f)
        run.animateTo(1f, if (on) tween(ARROW_MS, easing = LinearEasing) else tween(ARROW_BACK_MS))
    }
    Icon(
        icon,
        null,
        Modifier.size(16.dp).graphicsLayer {
            val p = pose()
            translationX = p.x.dp.toPx()
            translationY = p.y.dp.toPx()
            rotationZ = (p.heading - rest).toFloat()
        },
    )
}
