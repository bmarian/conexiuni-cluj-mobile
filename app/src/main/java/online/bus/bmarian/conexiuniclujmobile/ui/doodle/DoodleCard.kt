package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle
import kotlin.random.Random

private val CardCorner = 12.dp
val PanelCorner = 28.dp

/** [fallback] in the standard look. */
@Composable
fun drawnShape(corner: Dp, fallback: Shape): Shape {
    val seed = remember { Random.nextInt() }
    return if (LocalDoodle.current != null) inkShape(corner, seed) else fallback
}

@Composable
fun cardShape(): Shape = drawnShape(CardCorner, CardDefaults.shape)

/** [color] in the standard look. */
@Composable
fun cardFill(color: Color): Color = if (LocalDoodle.current != null) Color.Transparent else color

/** Solid, so dialogs and menus stay readable. */
@Composable
fun panelFill(color: Color): Color = LocalDoodle.current?.paper ?: color
