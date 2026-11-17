package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.logic.Wobble

private val Amplitude = 1.1.dp
private val Wavelength = 26.dp
private val Step = 4.dp

private val CornerSkew = floatArrayOf(0.85f, 1.15f, 0.8f, 1.25f)

class WobblyShape(private val corner: Dp, private val seed: Int = 0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val radii = FloatArray(4) { corner.toPx() * CornerSkew[Math.floorMod(it + seed, CornerSkew.size)] }
        val points = Wobble.outline(size.width, size.height, radii, Amplitude.toPx(), Wavelength.toPx(), Step.toPx(), seed)
        Outline.Generic(smoothPath(points))
    }

    override fun equals(other: Any?) = other is WobblyShape && other.corner == corner && other.seed == seed

    override fun hashCode() = corner.hashCode() * 31 + seed
}

internal fun smoothPath(points: FloatArray): Path = Path().apply {
    val n = points.size / 2
    fun x(i: Int) = points[Math.floorMod(i, n) * 2]
    fun y(i: Int) = points[Math.floorMod(i, n) * 2 + 1]
    moveTo((x(-1) + x(0)) / 2, (y(-1) + y(0)) / 2)
    for (i in 0 until n) quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2, (y(i) + y(i + 1)) / 2)
    close()
}
