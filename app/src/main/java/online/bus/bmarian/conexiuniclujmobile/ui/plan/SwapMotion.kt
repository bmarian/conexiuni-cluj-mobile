package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import online.bus.bmarian.conexiuniclujmobile.logic.LONG_PACE
import online.bus.bmarian.conexiuniclujmobile.ui.Bouncy
import online.bus.bmarian.conexiuniclujmobile.ui.Emphasized
import online.bus.bmarian.conexiuniclujmobile.ui.map.motionOn
import kotlin.math.PI
import kotlin.math.sin

private const val TRAVEL_MS = (520 * LONG_PACE).toInt()

/** Port of useSwapMotion.ts. */
@Stable
class SwapMotion {
    private var fromY by mutableFloatStateOf(0f)
    private var toY by mutableFloatStateOf(0f)
    private val travel = Animatable(0f)
    val spin = Animatable(0f)

    suspend fun turn() {
        if (!motionOn()) return
        spin.snapTo(0f)
        spin.animateTo(180f, Bouncy.pop)
        spin.snapTo(0f)
    }

    suspend fun swap(apply: () -> Unit) {
        if (motionOn()) travel.animateTo(1f, tween(TRAVEL_MS, easing = Emphasized))
        apply()
        withFrameNanos {}
        travel.snapTo(0f)
    }

    private fun Modifier.label(place: (Float) -> Unit, sign: Float, under: Boolean): Modifier =
        onGloballyPositioned { if (travel.value == 0f) place(it.positionInRoot().y) }.graphicsLayer {
            val p = travel.value
            val mid = sin(PI * p).toFloat()
            translationY = sign * (toY - fromY) * p
            translationX = sign * mid * size.height / 2
            val scale = if (under) 1f - 0.06f * mid else 1f + 0.04f * mid
            scaleX = scale
            scaleY = scale
            if (under) alpha = 1f - 0.5f * mid
        }

    fun Modifier.fromLabel(): Modifier = label({ fromY = it }, 1f, under = false)

    fun Modifier.toLabel(): Modifier = label({ toY = it }, -1f, under = true)
}
