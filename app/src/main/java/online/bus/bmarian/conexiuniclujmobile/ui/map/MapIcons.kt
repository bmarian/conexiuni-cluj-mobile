package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Port of the artwork in utils/mapIcons.ts.
object Glyphs {
    const val BUS_STOP =
        "M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm-10-7V6h11v4H6.5z"
    const val NAV_ARROW = "M12 2L21 21l-9-4-9 4 9-19z"
    const val DIRECTION_ARROW = "M12 3 L20 19 L12 15 L4 19 Z"
    const val PIN = "M10 0C4.48 0 0 4.48 0 10C0 17.5 10 28 10 28C10 28 20 17.5 20 10C20 4.48 15.52 0 10 0Z"

    val busStop: Path by lazy { PathParser().parsePathString(BUS_STOP).toPath() }
    val navArrow: Path by lazy { PathParser().parsePathString(NAV_ARROW).toPath() }
    val locationArrow: Path by lazy {
        PathParser().parsePathString(
            "M39.2,28.46C39.01,27.99 38.54,27.68 38.02,27.69C37.5,27.7 37.02,28.01 36.81,28.49L27.05,45.83C26.83,46.32 26.92,46.89 27.28,47.26C27.65,47.64 28.21,47.75 28.71,47.54L37.07,44.03C37.39,43.89 37.75,43.89 38.06,44.02L46.27,47.34C46.75,47.54 47.33,47.42 47.71,47.03C48.09,46.64 48.21,46.07 48,45.59L39.2,28.46Z",
        ).toPath()
    }
    val directionArrow: Path by lazy { PathParser().parsePathString(DIRECTION_ARROW).toPath() }
    val pin: Path by lazy { PathParser().parsePathString(PIN).toPath() }
    val wheelchairBody: Path by lazy { PathParser().parsePathString("M10 8v6h5l2 5").toPath() }
    val bikeFrame: Path by lazy { PathParser().parsePathString("M12 17.5V13l-3-3 4-3 2 3h3").toPath() }
}

object MapColors {
    val Green = Color(0xFF10B981)
    val Purple = Color(0xFFA855F7)
    val Red = Color(0xFFF43F5E)
    val Amber = Color(0xFFF59E0B)
    val Gray = Color(0xFF64748B)
    val SelectedStop = Color(0xFF00BC7D)
    val StopLight = Color(0xFF62748E)
    val StopDark = Color(0xFF45556C)
    val Walk = Color(0xFF38BDF8)
    val Destination = Color(0xFF0EA5E9)
    val Origin = Color(0xFF22C55E)
    val MeDot = Color(0xFF2B7FFF)
    val MePing = Color(0xFF51A2FF)
    val MeAccuracy = Color(0xFF3B82F6)
    val Ink = Color(0xFF0F172B)
    val InkDark = Color(0xFF0F172A)
    val Paper = Color(0xFFF1F5F9)
    val Strike = Color(0xFFEF4444)
    val ShapeFallback = Color(0xFF94A3B8)

    fun highlight(h: StopHighlight) = when (h) {
        StopHighlight.Green -> Green
        StopHighlight.Purple -> Purple
        StopHighlight.Red -> Red
        StopHighlight.Amber -> Amber
        StopHighlight.Gray -> Gray
    }

    fun zIndex(h: StopHighlight) = when (h) {
        StopHighlight.Green -> 1200
        StopHighlight.Purple -> 1100
        StopHighlight.Amber -> 1050
        StopHighlight.Red -> 1000
        StopHighlight.Gray -> 800
    }
}

fun DrawScope.stopMarker(center: Offset, diameter: Float, fill: Color, glyph: Float, shadow: Float = 1f) {
    val r = diameter / 2
    drawCircle(Color.Black.copy(alpha = 0.18f * shadow), r + 1.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, r, center)
    drawCircle(fill, r - 2.dp.toPx(), center)
    translate(center.x - glyph / 2, center.y - glyph / 2) {
        scale(glyph / 24f, glyph / 24f, pivot = Offset.Zero) { drawPath(Glyphs.busStop, Color.White) }
    }
}

fun DrawScope.pinMarker(tip: Offset, color: Color) {
    val w = 20.dp.toPx()
    val h = 28.dp.toPx()
    val left = tip.x - w / 2
    val top = tip.y - h
    // drop-shadow(1px 2px 2px rgba(0,0,0,.35))
    translate(left + 1.dp.toPx(), top + 2.dp.toPx()) {
        scale(w / 20f, h / 28f, pivot = Offset.Zero) { drawPath(Glyphs.pin, Color.Black.copy(alpha = 0.25f)) }
    }
    translate(left, top) {
        scale(w / 20f, h / 28f, pivot = Offset.Zero) {
            drawPath(Glyphs.pin, color)
            drawPath(Glyphs.pin, Color.White, style = Stroke(1.5f))
            drawCircle(Color.White.copy(alpha = 0.9f), 3.5f, Offset(10f, 10f))
        }
    }
}

fun DrawScope.accessibilityIcon(topLeft: Offset, size: Float, wheelchair: Boolean, ok: Boolean, color: Color) {
    val alpha = if (ok) 1f else 0.4f
    val stroke = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    translate(topLeft.x, topLeft.y) {
        scale(size / 24f, size / 24f, pivot = Offset.Zero) {
            val c = color.copy(alpha = alpha)
            if (wheelchair) {
                drawCircle(c, 2.5f, Offset(12f, 3.5f), style = Fill)
                drawPath(Glyphs.wheelchairBody, c, style = stroke)
                drawCircle(c, 3.5f, Offset(9f, 19.5f), style = stroke)
            } else {
                drawCircle(c, 3.5f, Offset(18.5f, 17.5f), style = stroke)
                drawCircle(c, 3.5f, Offset(5.5f, 17.5f), style = stroke)
                drawCircle(c, 2f, Offset(14f, 4.5f), style = Fill)
                drawPath(Glyphs.bikeFrame, c, style = stroke)
            }
            if (!ok) drawLine(MapColors.Strike, Offset(2f, 2f), Offset(22f, 22f), 4f, cap = StrokeCap.Round)
        }
    }
}
