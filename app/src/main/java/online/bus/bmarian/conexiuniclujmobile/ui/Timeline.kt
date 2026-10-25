package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.ui.map.SWEEP_MS
import online.bus.bmarian.conexiuniclujmobile.ui.map.motionOn

sealed interface Rail {
    data class Ride(val color: Color) : Rail
    data object Walk : Rail
}

enum class TimelineDot { Station, Stop, None }

/** Port of drawTimeline.ts. */
@Stable
class TimelineDraw {
    internal var top by mutableFloatStateOf(0f)
    internal var height by mutableFloatStateOf(0f)
    private val progress = Animatable(1f)

    /** How far down the card is drawn, in root pixels. */
    internal val reach: Float get() = if (progress.value >= 1f) Float.POSITIVE_INFINITY else top + height * progress.value

    suspend fun play() {
        if (!motionOn()) return
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SWEEP_MS.toInt(), easing = EaseInOutQuad))
    }
}

val LocalTimelineDraw = compositionLocalOf<TimelineDraw?> { null }

fun Modifier.timelineDraw(draw: TimelineDraw): Modifier = onGloballyPositioned {
    draw.top = it.positionInRoot().y
    draw.height = it.size.height.toFloat()
}

@Composable
fun TimelineRow(
    top: Rail?,
    bottom: Rail?,
    dot: TimelineDot,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val walk = MaterialTheme.colorScheme.outline
    val stop = MaterialTheme.colorScheme.outlineVariant
    val draw = LocalTimelineDraw.current
    var rowTop by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    val reached by remember { derivedStateOf { draw == null || draw.reach >= rowTop + rowHeight / 2 } }
    val pop = remember { Animatable(1f) }
    val shown = remember { Animatable(1f) }
    LaunchedEffect(reached) {
        if (!reached) {
            pop.snapTo(0f)
            shown.snapTo(0f)
        } else if (shown.value < 1f) {
            launch { pop.animateTo(1f, Bouncy.pop) }
            shown.animateTo(1f, Bouncy.slide)
        }
    }
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp).alpha(if (dimmed) 0.45f else 1f)
            .onGloballyPositioned {
                rowTop = it.positionInRoot().y
                rowHeight = it.size.height.toFloat()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(20.dp).fillMaxHeight()) {
            val x = size.width / 2
            val mid = size.height / 2
            val end = draw?.reach?.minus(rowTop) ?: Float.POSITIVE_INFINITY
            top?.let { drawRail(it, 0f, min(mid, end), x, walk) }
            bottom?.let { drawRail(it, mid, min(size.height, end), x, walk) }
            val k = pop.value
            when (dot) {
                TimelineDot.Station -> {
                    drawCircle(surface, 7.dp.toPx() * k, Offset(x, mid))
                    drawCircle(color, 7.dp.toPx() * k, Offset(x, mid), style = Stroke(2.5.dp.toPx() * k))
                    drawCircle(color, 3.dp.toPx() * k, Offset(x, mid))
                }
                TimelineDot.Stop -> {
                    drawCircle(surface, 4.dp.toPx() * k, Offset(x, mid))
                    drawCircle(stop, 4.dp.toPx() * k, Offset(x, mid), style = Stroke(1.5.dp.toPx() * k))
                }
                TimelineDot.None -> {}
            }
        }
        Row(
            Modifier.weight(1f).graphicsLayer {
                alpha = shown.value.coerceIn(0f, 1f)
                translationX = (1f - shown.value) * -6.dp.toPx()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).padding(start = 12.dp, top = if (compact) 4.dp else 10.dp, bottom = if (compact) 4.dp else 10.dp),
                content = content,
            )
            trailing?.invoke()
        }
    }
}

private fun DrawScope.drawRail(rail: Rail, from: Float, to: Float, x: Float, walk: Color) {
    if (to <= from) return
    when (rail) {
        is Rail.Ride -> drawLine(rail.color, Offset(x, from), Offset(x, to), strokeWidth = 4.dp.toPx())
        Rail.Walk -> {
            val step = 7.dp.toPx()
            var y = from + step / 2
            while (y < to) {
                drawCircle(walk, 1.8.dp.toPx(), Offset(x, y))
                y += step
            }
        }
    }
}
