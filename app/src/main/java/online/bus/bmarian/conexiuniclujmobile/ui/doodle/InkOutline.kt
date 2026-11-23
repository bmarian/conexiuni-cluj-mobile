package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

private val ChalkGaps = floatArrayOf(14f, 1.6f, 6f, 1.1f, 21f, 2f, 9f, 1.3f)

val InkThin = 1.6.dp
private val LineWidth = 1.dp
val InkThick = 2.4.dp

fun chalkEffect(density: Float): PathEffect = PathEffect.dashPathEffect(FloatArray(ChalkGaps.size) { ChalkGaps[it] * density })

fun Modifier.inkOutline(shape: Shape, color: Color, width: Dp = 2.dp, chalk: Boolean = false): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val stroke = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (chalk) chalkEffect(density) else null)
    onDrawWithContent {
        drawContent()
        drawPath(path, color, style = stroke)
    }
}

@Composable
fun inkShape(corner: Dp, seed: Int = 0): Shape {
    val doodle = LocalDoodle.current != null
    return remember(doodle, corner, seed) { if (doodle) WobblyShape(corner, seed) else RoundedCornerShape(corner) }
}

fun Modifier.doodleOutline(shape: Shape, width: Dp = 2.dp, rim: Boolean = false): Modifier = composed {
    val ink = LocalDoodle.current ?: return@composed Modifier
    Modifier.inkOutline(shape, if (rim) ink.rim else ink.outline, width, ink.chalk && !rim)
}

fun Modifier.lookOutline(shape: Shape, width: Dp = 2.dp): Modifier = composed {
    if (LocalDoodle.current != null) Modifier.doodleOutline(shape, width) else Modifier.border(LineWidth, MaterialTheme.colorScheme.outlineVariant, shape)
}
