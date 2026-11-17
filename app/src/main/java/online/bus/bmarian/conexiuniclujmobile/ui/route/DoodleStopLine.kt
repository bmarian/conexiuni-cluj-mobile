package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.Ticker
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Icon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.InkThick
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.chalkEffect
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerUnderline
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

private const val SWAY = 1f
private val MarkIcon = 14.dp

@Composable
internal fun DoodleTimeColumns(times: List<Pair<String, Boolean>>, header: Boolean, uTurnAt: Int) {
    Row {
        times.forEachIndexed { i, (label, live) ->
            val first = i == 0 && !header
            val color = if (first) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.width(DoodleMetrics.TimeColumn),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (i == uTurnAt) Icon(Icons.Rounded.UTurnLeft, null, Modifier.size(12.dp), tint = color)
                Text(
                    label,
                    fontSize = if (header) DoodleMetrics.ListHeadText else DoodleMetrics.StopTimeText,
                    fontWeight = when {
                        first && live -> FontWeight.ExtraBold
                        first || live -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.road(color: Color, width: Float, top: Float, bottom: Float) {
    val x = size.width / 2
    val sway = SWAY.dp.toPx()
    val span = bottom - top
    val path = Path().apply {
        moveTo(x, top)
        cubicTo(x - sway, top + span / 3, x + sway, top + span * 2 / 3, x, bottom)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Butt))
}

@Composable
internal fun DoodleStopLine(
    ink: DoodleInk,
    name: String,
    mark: StopMark,
    favorite: Boolean,
    first: Boolean,
    last: Boolean,
    times: List<Pair<String, Boolean>>,
    uTurnAt: Int,
    trailingText: String?,
    trailingHighlight: Boolean,
    paint: (() -> ClosedFloatingPointRange<Float>?)?,
    lineColor: Color,
    alerting: Boolean,
    reminded: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val sheet = MaterialTheme.colorScheme.surface
    val marked = mark == StopMark.From || mark == StopMark.Nearest
    Row(
        Modifier.fillMaxWidth().height(DoodleMetrics.StopRowHeight).squishClickable(onLongClick = onLongClick, onClick = onClick)
            .alpha(if (dimmed) 0.45f else 1f).padding(start = DoodleMetrics.Side, end = DoodleMetrics.Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(DoodleMetrics.RailWidth).fillMaxHeight()) {
            val top = if (first) size.height / 2 else 0f
            val bottom = if (last) size.height / 2 else size.height
            paint?.invoke()?.let { part ->
                road(ink.marker, DoodleMetrics.Highlight.toPx(), part.start * size.height, part.endInclusive * size.height)
            }
            road(ink.rim, DoodleMetrics.RoadRim.toPx(), top, bottom)
            road(lineColor, DoodleMetrics.Road.toPx(), top, bottom)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = DoodleMetrics.StopDot.toPx()
            drawCircle(if (marked) ink.marker else sheet, radius, center)
            val outline = Stroke(InkThick.toPx(), pathEffect = if (ink.chalk) chalkEffect(density) else null)
            drawCircle(ink.outline, radius, center, style = outline)
        }
        Row(
            Modifier.weight(1f).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Ticker(
                name,
                modifier = Modifier.weight(1f, fill = false).then(if (marked) Modifier.markerUnderline() else Modifier),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = DoodleMetrics.StopNameText),
                fontWeight = if (mark == StopMark.Plain || mark == StopMark.Favorite) FontWeight.Medium else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val tint = MaterialTheme.colorScheme.onSurface
            when (mark) {
                StopMark.From -> Icon(Icons.Rounded.Place, null, Modifier.size(MarkIcon), tint = tint)
                StopMark.Nearest -> Icon(Icons.Rounded.MyLocation, null, Modifier.size(MarkIcon), tint = tint)
                else -> {}
            }
            if (favorite) Icon(Icons.Rounded.Favorite, null, Modifier.size(MarkIcon), tint = Palette.Rose)
            if (alerting) Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(MarkIcon), tint = tint)
            if (reminded) Icon(Icons.Rounded.Alarm, stringResource(R.string.remind_saved), Modifier.size(MarkIcon), tint = tint)
        }
        if (trailingText != null) {
            Text(
                trailingText,
                Modifier.then(if (trailingHighlight) Modifier.markerUnderline() else Modifier),
                fontSize = DoodleMetrics.StopTimeText,
                fontWeight = FontWeight.Bold,
                color = if (trailingHighlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DoodleTimeColumns(times, header = false, uTurnAt = uTurnAt)
        }
    }
}
