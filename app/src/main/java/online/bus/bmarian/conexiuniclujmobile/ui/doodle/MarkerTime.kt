package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.RealtimeIcon
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

private const val SWIPE_TOP_LEFT = 0.2f
private const val SWIPE_TOP_RIGHT = 0.08f
private const val SWIPE_BOTTOM_RIGHT = 0.86f
private const val SWIPE_BOTTOM_LEFT = 0.95f

fun Modifier.markerSwipe(color: androidx.compose.ui.graphics.Color): Modifier = drawBehind {
    val swipe = Path().apply {
        moveTo(0f, size.height * SWIPE_TOP_LEFT)
        lineTo(size.width, size.height * SWIPE_TOP_RIGHT)
        lineTo(size.width, size.height * SWIPE_BOTTOM_RIGHT)
        lineTo(0f, size.height * SWIPE_BOTTOM_LEFT)
        close()
    }
    drawPath(swipe, color)
}

@Composable
fun MarkerTime(text: String, live: Boolean, modifier: Modifier = Modifier) {
    val ink = LocalDoodle.current ?: return
    if (!live) {
        val pencil = MaterialTheme.colorScheme.onSurfaceVariant
        Text(text, modifier, fontSize = DoodleMetrics.TimeText, fontWeight = FontWeight.Medium, color = pencil, maxLines = 1)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Top) {
        Text(
            text,
            Modifier.markerSwipe(ink.marker).padding(horizontal = 7.dp, vertical = 1.dp),
            fontSize = DoodleMetrics.TimeText,
            fontWeight = FontWeight.ExtraBold,
            color = ink.onMarker,
            maxLines = 1,
        )
        RealtimeIcon(MaterialTheme.colorScheme.onSurface, size = DoodleMetrics.Arcs)
    }
}
