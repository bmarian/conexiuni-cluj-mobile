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

// Shadows vanish on the dark map, so dark mode outlines what sits over it (same as the web).

private val RingColor = Color.White.copy(alpha = 0.06f)

// A small FAB's container; its bounds are the larger touch target.
private val SmallFabContainer = 40.dp

/** A hairline along the side of [shape] that [dx]/[dy] point to, drawn behind the surface. */
fun Modifier.darkEdge(shape: Shape, dx: Dp = 0.dp, dy: Dp = 0.dp): Modifier = composed {
    if (!LocalDarkTheme.current) return@composed this
    val color = MaterialTheme.colorScheme.outlineVariant
    drawBehind {
        translate(dx.toPx(), dy.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
    }
}

/** A faint ring around a control floating over the map, [size] square and centred when the bounds are larger. */
fun Modifier.darkRing(shape: Shape, size: Dp = Dp.Unspecified): Modifier = composed {
    if (!LocalDarkTheme.current) return@composed this
    drawWithContent {
        drawContent()
        val box = if (size.isSpecified) Size(size.toPx(), size.toPx()) else this.size
        translate((this.size.width - box.width) / 2, (this.size.height - box.height) / 2) {
            drawOutline(shape.createOutline(box, layoutDirection, this), RingColor, style = Stroke(1.dp.toPx()))
        }
    }
}

fun Modifier.darkSmallFabRing(): Modifier = composed { darkRing(FloatingActionButtonDefaults.smallShape, SmallFabContainer) }
