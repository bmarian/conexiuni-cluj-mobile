package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal class MapImages(context: Context) {
    private val density = Density(context)
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)

    private fun draw(widthDp: Float, heightDp: Float, block: DrawScope.() -> Unit): Bitmap {
        val w = ceil(widthDp * density.density).toInt().coerceAtLeast(1)
        val h = ceil(heightDp * density.density).toInt().coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), block)
        return image.asAndroidBitmap().apply { this.density = (this@MapImages.density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt() }
    }

    fun stop(dark: Boolean) = draw(28f, 28f) {
        stopMarker(center, 24.dp.toPx(), if (dark) MapColors.StopDark else MapColors.StopLight, 14.dp.toPx())
    }

    fun highlight(h: StopHighlight) = draw(28f, 28f) { stopMarker(center, 24.dp.toPx(), MapColors.highlight(h), 14.dp.toPx(), shadow = 1.2f) }

    fun selectedStop() = draw(36f, 36f) { stopMarker(center, 32.dp.toPx(), MapColors.SelectedStop, 16.dp.toPx(), shadow = 1.6f) }

    fun pin(color: Color) = draw(22f, 28f) { pinMarker(Offset(11.dp.toPx(), 28.dp.toPx()), color) }

    fun liftedPin(color: Color) = draw(22f, 66f) {
        pinMarker(Offset(11.dp.toPx(), 28.dp.toPx()), color)
        drawRect(Color(0x8C0F172A), Offset(10.dp.toPx(), 30.dp.toPx()), Size(2.dp.toPx(), 24.dp.toPx()))
        val dot = Offset(11.dp.toPx(), 59.dp.toPx())
        drawCircle(Color.Black.copy(alpha = 0.25f), 6.dp.toPx(), dot + Offset(0f, 1.dp.toPx()))
        drawCircle(Color.White, 5.dp.toPx(), dot)
        drawCircle(MapColors.InkDark, 3.dp.toPx(), dot)
    }

    fun end(color: String, dark: Boolean) = draw(30f, 30f) {
        val at = Offset(15.dp.toPx(), 14.dp.toPx())
        val ring = if (dark) MapColors.InkDark else Color.White
        drawCircle(Color.Black.copy(alpha = 0.18f), 13.dp.toPx(), at + Offset(0f, 2.dp.toPx()))
        drawCircle(ring, 12.dp.toPx(), at)
        drawCircle(if (color.isEmpty()) MapColors.ShapeFallback else routeColor(color), 9.dp.toPx(), at)
        val sq = 6.dp.toPx()
        drawRoundRect(ring, Offset(at.x - sq / 2, at.y - sq / 2), Size(sq, sq), CornerRadius(2.dp.toPx()))
    }

    fun badge(routes: List<Pair<String, String>>, dark: Boolean): Bitmap {
        val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
        val layouts = routes.map { measurer.measure(it.first, style) }
        val d = density.density
        val h = 28f
        val gap = 2f
        val widths = layouts.map { max(h, it.size.width / d + 16f) }
        val total = widths.sum() + gap * (widths.size - 1)
        return draw(total + 2f, h + 3f) {
            var left = 1.dp.toPx()
            val hp = h.dp.toPx()
            val ring = if (dark) MapColors.InkDark else Color.White
            routes.forEachIndexed { i, (_, color) ->
                val w = widths[i].dp.toPx()
                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(left, 2.dp.toPx()), Size(w, hp), CornerRadius(hp / 2))
                drawRoundRect(ring, Offset(left, 0f), Size(w, hp), CornerRadius(hp / 2))
                val inset = 3.dp.toPx()
                drawRoundRect(routeColor(color), Offset(left + inset, inset), Size(w - inset * 2, hp - inset * 2), CornerRadius(hp / 2 - inset))
                val layout = layouts[i]
                drawText(layout, topLeft = Offset(left + (w - layout.size.width) / 2, (hp - layout.size.height) / 2))
                left += w + gap.dp.toPx()
            }
        }
    }

    fun arrowBubble(dark: Boolean) = draw(26f, 30f) {
        val c = Offset(13.dp.toPx(), 13.dp.toPx())
        val ring = if (dark) MapColors.InkDark else Color.White
        drawCircle(Color.Black.copy(alpha = 0.2f), 12.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
        val tail = Path().apply {
            moveTo(c.x - 5.dp.toPx(), c.y + 10.dp.toPx())
            lineTo(c.x + 5.dp.toPx(), c.y + 10.dp.toPx())
            lineTo(c.x, c.y + 16.dp.toPx())
            close()
        }
        drawPath(tail, if (dark) MapColors.Paper else MapColors.InkDark)
        drawCircle(ring, 11.dp.toPx(), c)
        drawCircle(if (dark) MapColors.Paper else MapColors.Ink, 9.dp.toPx(), c)
    }

    fun arrowGlyph(dark: Boolean) = draw(12f, 12f) {
        val s = 12.dp.toPx()
        scale(s / 24f, s / 24f, pivot = Offset.Zero) { drawPath(Glyphs.directionArrow, if (dark) MapColors.Ink else Color.White) }
    }

    fun vehicle(color: String, label: String) = draw(40f, 42f) {
        val at = Offset(20.dp.toPx(), 20.dp.toPx())
        val r = 18.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.18f), r + 1.dp.toPx(), at + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White, r, at)
        drawCircle(routeColor(color), r - 2.dp.toPx(), at)
        val size = when {
            label.length >= 4 -> 8.sp
            label.length >= 3 -> 9.sp
            else -> 11.sp
        }
        val layout = measurer.measure(label, TextStyle(fontSize = size, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.025f).em))
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }

    /** MapLibre's own location arrow, with the arrow in the line's colour. */
    fun navArrow(color: String) = draw(75f, 75f) {
        val k = 1.dp.toPx()
        drawCircle(Color(0xFF263D57).copy(alpha = 0.16f), 37.5f * k, center)
        drawCircle(Color.White, 28.5f * k, center)
        scale(k, k, pivot = Offset.Zero) { drawPath(Glyphs.locationArrow, routeColor(color)) }
    }

    fun heading() = draw(19f, 19f) {
        val c = center
        val br = 8.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.15f), br + 1.dp.toPx(), c)
        drawCircle(Color.White, br, c)
        drawCircle(MapColors.Ink.copy(alpha = 0.85f), br - 1.dp.toPx(), c)
        val arrow = 10.dp.toPx()
        translate(c.x - arrow / 2, c.y - arrow / 2) {
            scale(arrow / 24f, arrow / 24f, pivot = Offset.Zero) { drawPath(Glyphs.navArrow, Color.White) }
        }
    }
}

private val Float.em get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
