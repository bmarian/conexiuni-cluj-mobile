package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.Ticker
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

internal enum class StopMark { From, Nearest, Favorite, Terminal, Plain }

@Composable
internal fun TimeColumns(times: List<Pair<String, Boolean>>, header: Boolean = false, uTurnAt: Int = -1) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        times.forEachIndexed { i, (label, live) ->
            val color = when {
                live -> MaterialTheme.colorScheme.primary
                header -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier.width(44.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (i == uTurnAt) Icon(Icons.Rounded.UTurnLeft, null, Modifier.size(12.dp), tint = color)
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = if (live) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

internal val RAIL_START = 16.dp
internal val RAIL_WIDTH = 20.dp

@Composable
internal fun StopLine(
    name: String,
    mark: StopMark,
    favorite: Boolean,
    first: Boolean,
    last: Boolean,
    times: List<Pair<String, Boolean>>,
    uTurnAt: Int = -1,
    trailingText: String?,
    trailingHighlight: Boolean = false,
    paint: (() -> ClosedFloatingPointRange<Float>?)? = null,
    paintColor: Color = Color.Unspecified,
    alerting: Boolean = false,
    reminded: Boolean = false,
    dimmed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val rowTint = when (mark) {
        StopMark.From -> Palette.Emerald.copy(alpha = 0.08f)
        StopMark.Nearest -> Palette.Purple.copy(alpha = 0.08f)
        StopMark.Favorite -> Palette.Rose.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().background(rowTint).squishClickable(onLongClick = onLongClick, onClick = onClick)
            .alpha(if (dimmed) 0.45f else 1f).padding(start = RAIL_START, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RAIL_WIDTH).height(40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 2.dp.toPx()
                drawLine(rail, Offset(x, if (first) size.height / 2 else 0f), Offset(x, if (last) size.height / 2 else size.height), w)
                paint?.invoke()?.let { part ->
                    drawLine(paintColor, Offset(x, part.start * size.height), Offset(x, part.endInclusive * size.height), 4.dp.toPx())
                }
            }
            val color = when (mark) {
                StopMark.From -> Palette.Emerald
                StopMark.Nearest -> Palette.Purple
                StopMark.Favorite -> Palette.Rose
                StopMark.Terminal -> Palette.Slate400
                StopMark.Plain -> null
            }
            if (color != null) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color).border(2.dp, surface, CircleShape))
            } else {
                Box(Modifier.size(9.dp).clip(CircleShape).background(surface).border(2.dp, rail, CircleShape))
            }
        }
        Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ticker(
                name,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = when (mark) {
                    StopMark.From -> FontWeight.Bold
                    StopMark.Nearest, StopMark.Terminal -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                color = when (mark) {
                    StopMark.From -> Palette.Emerald
                    StopMark.Nearest -> Palette.Purple
                    StopMark.Terminal -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            when (mark) {
                StopMark.From -> Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = Palette.Emerald)
                StopMark.Nearest -> Icon(Icons.Rounded.MyLocation, null, Modifier.size(14.dp), tint = Palette.Purple)
                else -> {}
            }
            if (favorite) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = Palette.Rose)
            }
            if (alerting) {
                Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (reminded) {
                Icon(Icons.Rounded.Alarm, stringResource(R.string.remind_saved), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (trailingHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TimeColumns(times, uTurnAt = uTurnAt)
        }
    }
}
