package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

// Share of the text height the marker covers.
private const val PAPER_TOP = 0.56f
private const val PAPER_BOTTOM = 0.92f
private const val CHALK_TOP = 0.8f
private const val CHALK_BOTTOM = 0.95f

val MarkerOverhang = 4.dp
private val Round = 3.dp

fun Modifier.markerUnderline(): Modifier = composed {
    val ink = LocalDoodle.current ?: return@composed Modifier
    val top = if (ink.chalk) CHALK_TOP else PAPER_TOP
    val bottom = if (ink.chalk) CHALK_BOTTOM else PAPER_BOTTOM
    Modifier.drawBehind {
        val out = MarkerOverhang.toPx()
        drawRoundRect(
            ink.marker,
            topLeft = Offset(-out, size.height * top),
            size = Size(size.width + out * 2, size.height * (bottom - top)),
            cornerRadius = CornerRadius(Round.toPx()),
        )
    }
}
