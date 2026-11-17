package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.logic.Mode

// Glyphs centred on 0,0 in a 22-wide box.
private const val GLYPH_BOX = 22f
private const val GLYPH_LINE = 1.7f
private const val GLYPH_GROW = 1.2f
private const val BODY = "M-6 4.5 V-4 C-6 -6.5 -4.5 -7.5 0 -7.5 C4.5 -7.5 6 -6.5 6 -4 V4.5 Z M-6 -0.5 H6"
private val glyphs: Map<Mode, Path> by lazy {
    fun p(d: String) = PathParser().parsePathString(d).toPath()
    mapOf(
        Mode.BUS to p("$BODY M-4.2 4.5 V6.8 M4.2 4.5 V6.8"),
        Mode.TROLLEYBUS to p("$BODY M-2 -7.5 L-4.5 -11 M2 -7.5 L-0.5 -11 M-4.2 4.5 V6.8 M4.2 4.5 V6.8"),
        Mode.TRAM to p("$BODY M0 -7.5 V-10 M-3 -10.5 H3 M-3.5 4.5 L-5 7 M3.5 4.5 L5 7"),
        Mode.METRO to p("M-6 5 V-5 C-6 -6.5 -5 -7.5 -3.5 -7.5 H3.5 C5 -7.5 6 -6.5 6 -5 V5 Z M-6 0 H6 M-3 5 L-5 8 M3 5 L5 8"),
        Mode.RAIL to p("M-6 5 V-4 C-6 -6.5 -4.5 -7.5 0 -7.5 C4.5 -7.5 6 -6.5 6 -4 V5 Z M-6 0 H6 M-3 2.5 H-2 M2 2.5 H3 M-3 5 L-5 8 M3 5 L5 8"),
    )
}
private val heartPath by lazy {
    PathParser().parsePathString(
        "M0 8.5 C-5 4.8 -7.2 2.2 -7.2 -0.8 C-7.2 -3 -5.5 -4.6 -3.4 -4.6 C-1.9 -4.6 -0.7 -3.8 0 -2.5 C0.7 -3.8 1.9 -4.6 3.4 -4.6 C5.5 -4.6 7.2 -3 7.2 -0.8 C7.2 2.2 5 4.8 0 8.5 Z",
    ).toPath()
}

private const val RIM = 2.2f / 25f
private const val PIN_LINE = 1.6f

internal fun DrawScope.drawnStop(center: Offset, diameter: Float, fill: Color, glyph: Float, mode: Mode, heart: Boolean, path: Path?, rim: Color) {
    val r = diameter / 2
    val line = diameter * RIM
    drawCircle(fill, r - line / 2, center)
    drawCircle(rim, r - line / 2, center, style = Stroke(line))
    if (path != null) {
        translate(center.x - glyph / 2, center.y - glyph / 2) { scale(glyph / 24f, glyph / 24f, pivot = Offset.Zero) { drawPath(path, Color.White) } }
        return
    }
    translate(center.x, center.y) {
        val k = glyph / GLYPH_BOX * GLYPH_GROW
        scale(k, k, pivot = Offset.Zero) {
            if (heart) drawPath(heartPath, Color.White)
            else drawPath(glyphs.getValue(mode), Color.White, style = Stroke(GLYPH_LINE, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal fun DrawScope.drawnPin(tip: Offset, color: Color, ink: Color, paper: Color) {
    val w = 20.dp.toPx()
    val h = 28.dp.toPx()
    translate(tip.x - w / 2, tip.y - h) {
        scale(w / 20f, h / 28f, pivot = Offset.Zero) {
            drawPath(Glyphs.pin, color)
            drawPath(Glyphs.pin, ink, style = Stroke(PIN_LINE, join = StrokeJoin.Round))
            drawCircle(paper, 3.5f, Offset(10f, 10f))
            drawCircle(ink, 3.5f, Offset(10f, 10f), style = Stroke(PIN_LINE))
        }
    }
}
