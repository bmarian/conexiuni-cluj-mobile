package online.bus.bmarian.conexiuniclujmobile.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** What runs through a timeline row above or below its dot: a ride in its line's colour, or a walk. */
sealed interface Rail {
    data class Ride(val color: Color) : Rail
    data object Walk : Rail
}

/** A stop you get on or off at (or the trip's ends), a stop ridden past, or none: a walk or a change. */
enum class TimelineDot { Station, Stop, None }

/**
 * One row of a trip drawn top to bottom, as the planner and the navigation screen show it: every
 * dot on the same line, rides solid in their colours, walks dotted. [dimmed] greys out what is
 * already behind you.
 */
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
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp).alpha(if (dimmed) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(20.dp).fillMaxHeight()) {
            val x = size.width / 2
            val mid = size.height / 2
            top?.let { drawRail(it, 0f, mid, x, walk) }
            bottom?.let { drawRail(it, mid, size.height, x, walk) }
            when (dot) {
                TimelineDot.Station -> {
                    drawCircle(surface, 7.dp.toPx(), Offset(x, mid))
                    drawCircle(color, 7.dp.toPx(), Offset(x, mid), style = Stroke(2.5.dp.toPx()))
                    drawCircle(color, 3.dp.toPx(), Offset(x, mid))
                }
                TimelineDot.Stop -> {
                    drawCircle(surface, 4.dp.toPx(), Offset(x, mid))
                    drawCircle(stop, 4.dp.toPx(), Offset(x, mid), style = Stroke(1.5.dp.toPx()))
                }
                TimelineDot.None -> {}
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, top = if (compact) 4.dp else 10.dp, bottom = if (compact) 4.dp else 10.dp),
            content = content,
        )
        trailing?.invoke()
    }
}

private fun DrawScope.drawRail(rail: Rail, from: Float, to: Float, x: Float, walk: Color) {
    when (rail) {
        is Rail.Ride -> drawLine(rail.color, Offset(x, from), Offset(x, to), strokeWidth = 4.dp.toPx())
        Rail.Walk -> {
            // Dots, like a walk on the map.
            val step = 7.dp.toPx()
            var y = from + step / 2
            while (y < to) {
                drawCircle(walk, 1.8.dp.toPx(), Offset(x, y))
                y += step
            }
        }
    }
}
