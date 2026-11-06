package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity

private val LineShadow = Color.Black.copy(alpha = 0.8f)
private const val WCAG_AA = 4.5f

fun lineNumberShadow(density: Float) = Shadow(LineShadow, Offset(0f, density), blurRadius = 3f * density)

@Composable
fun lineNumberShadow(): Shadow = lineNumberShadow(LocalDensity.current.density)

fun android.graphics.Paint.lineShadow(density: Float) = apply { setShadowLayer(1.5f * density, 0f, density, LineShadow.toArgb()) }

/** [line], or [content] when [line] is too light to read as text. */
fun lineInk(line: Color, content: Color): Color = if (1.05f / (line.luminance() + 0.05f) >= WCAG_AA) line else content
