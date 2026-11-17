package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val Swell = 2.5.dp
private val Ripple = 1.dp
private val SwellLength = 170.dp
private val RippleLength = 47.dp
private val Step = 4.dp

object SheetEdgeShape : Shape {
    fun edge(width: Float, density: Density): Path = with(density) {
        Path().apply {
            var x = 0f
            moveTo(0f, y(0f, density))
            while (x < width) {
                x = (x + Step.toPx()).coerceAtMost(width)
                lineTo(x, y(x, density))
            }
        }
    }

    private fun y(x: Float, density: Density): Float = with(density) {
        val swell = Swell.toPx() * (1 + sin(2 * PI * x / SwellLength.toPx())).toFloat()
        val ripple = Ripple.toPx() * sin(2 * PI * x / RippleLength.toPx()).toFloat()
        swell + ripple + Ripple.toPx()
    }

    fun handle(width: Float, height: Float): Path = Path().apply {
        moveTo(0f, height / 2)
        cubicTo(width * 0.2f, 0f, width * 0.3f, height, width / 2, height / 2)
        cubicTo(width * 0.7f, 0f, width * 0.8f, height, width, height / 2)
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            edge(size.width, density).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
}

fun Modifier.sheetEdgeInk(ink: DoodleInk): Modifier = drawWithCache {
    val edge = SheetEdgeShape.edge(size.width, this)
    val stroke = Stroke(InkThick.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (ink.chalk) chalkEffect(density) else null)
    onDrawWithContent {
        drawContent()
        drawPath(edge, ink.outline, style = stroke)
    }
}
