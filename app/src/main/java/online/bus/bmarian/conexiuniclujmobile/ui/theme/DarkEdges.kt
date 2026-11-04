package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

private val RingColor = Color.White.copy(alpha = 0.06f)

// The FAB's bounds are its 48dp touch target.
private val SmallFabContainer = 40.dp

fun Modifier.darkEdge(shape: Shape, dx: Dp = 0.dp, dy: Dp = 0.dp): Modifier = composed {
    if (!LocalDarkTheme.current) return@composed this
    val color = MaterialTheme.colorScheme.outlineVariant
    drawBehind {
        translate(dx.toPx(), dy.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
    }
}

fun Modifier.darkRing(shape: Shape, container: Dp = Dp.Unspecified): Modifier = composed {
    if (!LocalDarkTheme.current) return@composed this
    drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val full = if (container.isSpecified) Size(container.toPx(), container.toPx()) else size
        val box = Size(full.width - stroke, full.height - stroke)
        translate((size.width - box.width) / 2, (size.height - box.height) / 2) {
            drawOutline(shape.createOutline(box, layoutDirection, this), RingColor, style = Stroke(stroke))
        }
    }
}

fun Modifier.darkSmallFabRing(): Modifier = composed { darkRing(FloatingActionButtonDefaults.smallShape, SmallFabContainer) }
