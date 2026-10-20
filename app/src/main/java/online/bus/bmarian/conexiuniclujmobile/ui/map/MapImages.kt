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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CELL_DP = 24f
private const val PILL_PAD_DP = 3f
private const val PILL_GAP_DP = 2f
private const val PILL_ROW = 3
private const val LABEL_GAP_DP = 2f
private const val LABEL_PAD_DP = 2f
private const val STATION_SLOT_DP = 28f
private const val SPIDER_GAP_DP = 4f
private const val SPIDER_PAD_DP = 16
const val CLUSTER_STATIONS_MAX = 3
const val CLUSTER_NAMES_MAX = 8

val STATION_BOX = ClusterBox(CELL_DP, CELL_DP, CELL_DP / 2, CELL_DP / 2)

sealed interface GroupIcon {
    data class Station(val mode: Mode, val color: String) : GroupIcon

    data object Heart : GroupIcon
}

internal class MapImages(context: Context) {
    private val density = Density(context)
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)

    // groupBox runs on the clustering thread, so it measures with its own.
    private val boxMeasurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
    private val boxLabelSizes = HashMap<String, Pair<Float, Float>>()

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

    fun highlight(h: StopHighlight) = draw(28f, 28f) {
        stopMarker(center, 24.dp.toPx(), MapColors.highlight(h), if (h == StopHighlight.Favorite) 13.dp.toPx() else 14.dp.toPx(), shadow = 1.2f, heart = h == StopHighlight.Favorite)
    }

    fun selectedStop() = draw(36f, 36f) { stopMarker(center, 32.dp.toPx(), MapColors.SelectedStop, 16.dp.toPx(), shadow = 1.6f) }

    fun station(mode: Mode, color: String) = draw(28f, 28f) { stopMarker(center, 24.dp.toPx(), routeColor(color), 14.dp.toPx(), mode = mode) }

    fun selectedStation(mode: Mode, color: String) = draw(36f, 36f) {
        stopMarker(center, 32.dp.toPx(), routeColor(color), 16.dp.toPx(), shadow = 1.6f, mode = mode)
    }

    private val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
    private val labelSizes = HashMap<String, Pair<Float, Float>>()

    private fun pillSize(icons: Int): Pair<Float, Float> {
        val cols = min(icons, PILL_ROW)
        val rows = (icons + PILL_ROW - 1) / PILL_ROW
        return (cols * CELL_DP + (cols - 1) * PILL_GAP_DP + 2 * PILL_PAD_DP) to (rows * CELL_DP + (rows - 1) * PILL_GAP_DP + 2 * PILL_PAD_DP)
    }

    private fun label(name: String, more: Int, dark: Boolean): AnnotatedString = AnnotatedString.Builder().apply {
        pushStyle(SpanStyle(color = if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A)))
        append(name)
        pop()
        if (more > 0) {
            pushStyle(SpanStyle(color = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)))
            append(" +$more")
            pop()
        }
    }.toAnnotatedString()

    /** Port of clusterIconBox in utils/mapIcons.ts: the pill on its centre, the label under it. */
    fun groupBox(icons: Int, name: String, more: Int): ClusterBox {
        val (w, h) = pillSize(icons)
        val (labelWidth, labelHeight) = labelSize(name, more, boxMeasurer, boxLabelSizes)
        val width = max(w, labelWidth)
        return ClusterBox(width, h + LABEL_GAP_DP + labelHeight, width / 2, h / 2)
    }

    private fun labelSize(
        name: String,
        more: Int,
        by: TextMeasurer = measurer,
        sizes: HashMap<String, Pair<Float, Float>> = labelSizes,
    ): Pair<Float, Float> {
        val text = if (more > 0) "$name +$more" else name
        return sizes.getOrPut(text) {
            by.measure(text, labelStyle).size.let { it.width / density.density + 2 * LABEL_PAD_DP to it.height / density.density }
        }
    }

    /** From the pill's centre to the label's. */
    fun labelOffset(icons: Int, name: String, more: Int): Float = pillSize(icons).second / 2 + LABEL_GAP_DP + labelSize(name, more).second / 2

    private fun DrawScope.groupIcon(icon: GroupIcon, at: Offset) {
        val cell = CELL_DP.dp.toPx()
        when (icon) {
            is GroupIcon.Station -> stopMarker(at, cell, routeColor(icon.color), 14.dp.toPx(), mode = icon.mode)
            GroupIcon.Heart -> stopMarker(at, cell, MapColors.Red, 13.dp.toPx(), heart = true)
        }
    }

    private fun DrawScope.groupText(name: String, more: Int, dark: Boolean, topLeft: Offset) {
        // Span colours win over drawText's colour, so the halo is drawn from the plain text.
        val halo = measurer.measure(if (more > 0) "$name +$more" else name, labelStyle)
        drawText(halo, color = if (dark) Color(0xFF0F172A) else Color.White, topLeft = topLeft, drawStyle = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        drawText(measurer.measure(label(name, more, dark), labelStyle), topLeft = topLeft)
    }

    /** Centred on the pill, with room for its shadow on both sides. */
    fun groupPill(icons: List<GroupIcon>, dark: Boolean): Bitmap {
        val (pw, ph) = pillSize(icons.size)
        return draw(pw, ph + 2) {
            val top = 1.dp.toPx()
            val radius = CornerRadius((CELL_DP / 2 + PILL_PAD_DP).dp.toPx())
            val pill = Size(pw.dp.toPx(), ph.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(0f, top + 1.dp.toPx()), pill, radius)
            drawRoundRect(if (dark) Color(0xFF1E293B) else Color.White, Offset(0f, top), pill, radius)
            if (dark) drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), pill, radius, style = Stroke(1.dp.toPx()))
            val cell = CELL_DP.dp.toPx()
            val gap = PILL_GAP_DP.dp.toPx()
            icons.forEachIndexed { i, icon ->
                val row = i / PILL_ROW
                val inRow = min(PILL_ROW, icons.size - row * PILL_ROW)
                val rowWidth = inRow * cell + (inRow - 1) * gap
                groupIcon(icon, Offset(size.width / 2 - rowWidth / 2 + (i % PILL_ROW) * (cell + gap) + cell / 2, top + PILL_PAD_DP.dp.toPx() + row * (cell + gap) + cell / 2))
            }
        }
    }

    fun groupLabel(name: String, more: Int, dark: Boolean): Bitmap {
        val (w, h) = labelSize(name, more)
        return draw(w, h) { groupText(name, more, dark, Offset(LABEL_PAD_DP.dp.toPx(), 0f)) }
    }

    fun spiderItem(icon: GroupIcon, name: String, left: Boolean, dark: Boolean): Bitmap {
        val (w, h) = spiderItemSize(name)
        return draw(w, h) {
            val cell = STATION_SLOT_DP.dp.toPx()
            val iconX = if (left) size.width - cell / 2 else cell / 2
            groupIcon(icon, Offset(iconX, size.height / 2))
            val textX = if (left) 0f else cell + SPIDER_GAP_DP.dp.toPx()
            groupText(name, 0, dark, Offset(textX + LABEL_PAD_DP.dp.toPx(), (size.height - labelSize(name, 0).second.dp.toPx()) / 2))
        }
    }

    private fun spiderItemSize(name: String): Pair<Float, Float> {
        val (lw, lh) = labelSize(name, 0)
        return STATION_SLOT_DP + SPIDER_GAP_DP + lw to max(STATION_SLOT_DP, lh)
    }

    /** From the item's centre to its station's, for an item on the right. */
    fun spiderItemShift(name: String): Float = spiderItemSize(name).first / 2 - STATION_SLOT_DP / 2

    fun spider(legs: List<SpiderLeg>, dark: Boolean): Bitmap {
        val r = spiderRadius(legs) + SPIDER_PAD_DP
        val ink = if (dark) MapColors.Paper else MapColors.InkDark
        return draw(2f * r, 2f * r) {
            val hub = center
            legs.forEach { drawLine(ink.copy(alpha = 0.7f), hub, hub + Offset(it.x.dp.toPx(), it.y.dp.toPx()), 1.5.dp.toPx()) }
            drawCircle(ink, 4.dp.toPx(), hub)
        }
    }

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
