package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Dot = 1.dp
private val DotGap = 6.dp
private val DotWidth = 1.6.dp

fun Modifier.dottedRule(color: Color, inset: Dp = DoodleMetrics.Side): Modifier = drawBehind {
    val y = size.height - InkThin.toPx()
    val side = inset.toPx()
    drawLine(
        color, Offset(side, y), Offset(size.width - side, y), DotWidth.toPx(), StrokeCap.Round,
        PathEffect.dashPathEffect(floatArrayOf(Dot.toPx(), DotGap.toPx())),
    )
}
