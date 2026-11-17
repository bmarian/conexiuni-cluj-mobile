package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.lineNumberShadow
import online.bus.bmarian.conexiuniclujmobile.logic.Mode

/** Drawn in a [WIDTH] x [HEIGHT] box, facing right. */
object Characters {
    const val WIDTH = 240f
    const val HEIGHT = 184f
    const val TOP = -34f
    private const val LINE = 4f

    class Colors(val body: Color, val ink: Color, val window: Color, val wheel: Color, val blob: Color, val face: Color)

    /** In box units, for a character facing right. */
    class Number(val x: Float, val baseline: Float, val size: Float)

    private class Art(
        val fills: List<Pair<Path, Part>>,
        val lines: Path?,
        val wheels: List<Triple<Float, Float, Float>>,
        val face: Offset,
        val number: Number,
    )

    private enum class Part { Body, Window, Wheel }

    private fun p(d: String) = PathParser().parsePathString(d).toPath()

    private val busBody by lazy {
        listOf(
            p("M40 40 C40 24 52 16 70 16 L188 18 C210 19 224 36 226 60 L228 112 C228 122 220 127 208 127 L56 127 C45 127 38 120 39 110 Z") to Part.Body,
            p("M56 30 L84 29 L85 54 L57 55 Z M96 29 L124 29 L124 54 L96 54 Z M136 29 L164 30 L164 54 L136 54 Z") to Part.Window,
            p("M178 28 C198 29 211 40 214 56 L214 64 L178 64 Z") to Part.Window,
        )
    }

    private val arts: Map<Mode, Art> by lazy {
        val busWheels = listOf(Triple(80f, 128f, 14f), Triple(190f, 128f, 14f))
        val bogies = { a: Float, b: Float -> listOf(Triple(a, 129f, 9f), Triple(a + 24f, 129f, 9f), Triple(b, 129f, 9f), Triple(b + 24f, 129f, 9f)) }
        mapOf(
            Mode.BUS to Art(busBody, p("M222 108 L227 107"), busWheels, Offset(196f, 64f), Number(132f, 110f, 46f)),
            Mode.TROLLEYBUS to Art(
                busBody, p("M104 17 L60 -26 M118 17 L74 -26 M54 -28 h12 M68 -28 h12 M222 108 L227 107"), busWheels, Offset(196f, 64f), Number(132f, 110f, 46f),
            ),
            Mode.TRAM to Art(
                listOf(
                    p("M30 36 C30 24 38 18 52 18 L204 18 C220 18 228 28 230 42 L232 112 C232 120 226 125 216 125 L40 125 C30 125 26 119 26 110 Z") to Part.Body,
                    p("M42 30 h22 v24 h-22 Z M74 30 h22 v24 h-22 Z M106 30 h22 v24 h-22 Z M138 30 h22 v24 h-22 Z M170 30 h18 v24 h-18 Z") to Part.Window,
                    p("M198 28 C214 28 222 38 224 52 L224 64 L198 64 Z") to Part.Window,
                ),
                p("M112 18 L126 -4 L140 18 M126 -4 L118 -18 H134"), bogies(58f, 174f), Offset(211f, 64f), Number(118f, 110f, 44f),
            ),
            Mode.METRO to Art(
                listOf(
                    p("M24 40 C24 26 34 20 48 20 L176 20 C204 20 226 42 232 78 L234 112 C234 121 228 125 218 125 L36 125 C28 125 24 119 24 110 Z") to Part.Body,
                    p("M36 32 L168 32 C186 32 200 42 208 60 L36 60 Z") to Part.Window,
                    p("M50 125 h44 v7 h-44 Z M160 125 h44 v7 h-44 Z") to Part.Wheel,
                ),
                p("M80 32 V60 M124 32 V60"), emptyList(), Offset(192f, 60f), Number(118f, 112f, 44f),
            ),
            Mode.RAIL to Art(
                listOf(
                    p("M60 20 V10 H112 V20") to Part.Body,
                    p("M16 40 C16 26 24 20 38 20 L168 20 C192 20 210 32 220 54 L232 100 C235 114 228 125 214 125 L28 125 C20 125 16 119 16 110 Z") to Part.Body,
                    p("M28 32 h22 v22 h-22 Z M58 32 h22 v22 h-22 Z M126 32 h22 v22 h-22 Z M92 32 h22 v86 h-22 Z") to Part.Window,
                    p("M166 30 C188 30 202 40 210 56 L214 64 L166 64 Z") to Part.Window,
                ),
                null, bogies(44f, 176f), Offset(190f, 64f), Number(168f, 110f, 38f),
            ),
        )
    }

    fun number(mode: Mode): Number = arts.getValue(mode).number

    /** [minLine] keeps small ones from going faint. */
    private val puff by lazy { p("M38 120 c-7 0 -11 -6 -8 -11 c-5 -5 1 -12 7 -9 c2 -6 11 -6 13 0 c6 -1 9 6 4 10 c3 6 -3 11 -9 8 c-2 4 -7 4 -7 2 z") }
    private val speed by lazy { p("M2 62 H18 M-2 84 H14 M4 106 H20") }
    private val trolleySpark by lazy { p("M62 -31 l-4 -6 l5 1 l-3 -7") }
    private val tramSpark by lazy { p("M126 -21 l-4 -6 l5 1 l-3 -7") }

    /** [spin] turns the wheel spokes, in degrees. */
    fun DrawScope.character(mode: Mode, colors: Colors, flip: Boolean, minLine: Float = 0f, mood: Mood = Mood.Happy, spin: Float = 0f) {
        val art = arts.getValue(mode)
        val moving = mood == Mood.Happy || mood == Mood.Wave
        val k = size.width / WIDTH
        val line = Stroke(maxOf(LINE, minLine / k), cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({
            if (flip) scale(-1f, 1f, pivot = center)
            scale(k, k, pivot = Offset.Zero)
            translate(top = -TOP)
        }) {
            if (moving) extras(mode, colors, line)
            for ((path, part) in art.fills) {
                val fill = when (part) {
                    Part.Body -> colors.body
                    Part.Window -> colors.window
                    Part.Wheel -> colors.wheel
                }
                drawPath(path, fill)
                drawPath(path, colors.ink, style = line)
            }
            art.lines?.let { drawPath(it, colors.ink, style = line) }
            face(art.face, colors, line, mood)
            for ((x, y, r) in art.wheels) {
                drawCircle(colors.wheel, r, Offset(x, y))
                drawCircle(colors.ink, r, Offset(x, y), style = line)
                drawCircle(colors.window, r * HUB, Offset(x, y))
                rotate(spin, Offset(x, y)) {
                    drawLine(colors.window, Offset(x, y - r + SPOKE_INSET), Offset(x, y + r - SPOKE_INSET), SPOKE, StrokeCap.Round)
                }
            }
            if (mood == Mood.Sleep) snores(art.face.x - SNORE_BACK, colors.ink, line)
        }
    }

    /** Never mirrored. */
    fun DrawScope.number(measurer: TextMeasurer, mode: Mode, label: String, flip: Boolean, font: FontFamily) {
        val spot = number(mode)
        val k = size.width / WIDTH
        val style = TextStyle(
            fontSize = (spot.size * k / density / fontScale).sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = font,
            color = Color.White,
            shadow = lineNumberShadow(density),
        )
        val layout = measurer.measure(label, style)
        val x = (if (flip) WIDTH - spot.x else spot.x) * k
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, (spot.baseline - TOP) * k - layout.firstBaseline))
    }

    private const val HUB = 0.3f
    private const val SPOKE = 2.6f
    private const val SPOKE_INSET = 3f
    private const val THIN = 0.75f
    private const val SPARK_LINE = 3f

    private val Spark = Color(0xFFF6CB45)

    private fun DrawScope.extras(mode: Mode, colors: Colors, line: Stroke) {
        when (mode) {
            Mode.BUS -> {
                drawPath(puff, colors.window)
                drawPath(puff, colors.ink, style = Stroke(line.width * THIN, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Mode.TROLLEYBUS -> drawPath(trolleySpark, Spark, style = Stroke(SPARK_LINE, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Mode.TRAM -> drawPath(tramSpark, Spark, style = Stroke(SPARK_LINE, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Mode.METRO, Mode.RAIL -> drawPath(speed, colors.ink, style = line)
        }
    }
    private const val DAY_SHADOW = 0.14f
    private const val NIGHT_SHADOW = 0.35f
    private const val SHADOW_INSET = 0.12f
    private const val SHADOW_Y = 139f
    private const val SHADOW_HEIGHT = 10f

    fun DrawScope.shadow(dark: Boolean) {
        val k = size.width / WIDTH
        drawOval(
            Color.Black.copy(alpha = if (dark) NIGHT_SHADOW else DAY_SHADOW),
            topLeft = Offset(size.width * SHADOW_INSET, (SHADOW_Y - TOP) * k),
            size = Size(size.width * (1 - 2 * SHADOW_INSET), SHADOW_HEIGHT * k),
        )
    }
    private const val EYE = 2.4f
    private const val SNORE_BACK = 40f

    private fun DrawScope.face(at: Offset, colors: Colors, line: Stroke, mood: Mood) {
        val (x, bottom) = at
        val blob = Path().apply {
            moveTo(x - 10, bottom)
            cubicTo(x - 12, bottom - 12, x - 8, bottom - 23, x, bottom - 23)
            cubicTo(x + 8, bottom - 23, x + 12, bottom - 16, x + 11, bottom - 8)
            cubicTo(x + 11, bottom - 4, x + 10, bottom - 2, x + 9, bottom)
        }
        if (mood == Mood.Wave) {
            val arm = Path().apply {
                moveTo(x + 8, bottom - 6)
                cubicTo(x + 20, bottom - 12, x + 22, bottom - 26, x + 18, bottom - 40)
            }
            drawPath(arm, colors.ink, style = line)
            drawCircle(colors.blob, 5f, Offset(x + 18, bottom - 45))
            drawCircle(colors.ink, 5f, Offset(x + 18, bottom - 45), style = Stroke(line.width * 0.75f))
        }
        drawPath(blob, colors.blob)
        drawPath(blob, colors.ink, style = Stroke(line.width * 0.75f, cap = StrokeCap.Round))
        val eyes = bottom - 14
        val pen = Stroke(2.6f, cap = StrokeCap.Round)
        when (mood) {
            Mood.Sleep -> for (dx in listOf(-7f, 1f)) {
                drawPath(Path().apply { moveTo(x + dx, eyes); quadraticTo(x + dx + 3, eyes + 2.5f, x + dx + 6, eyes) }, colors.face, style = pen)
            }
            Mood.Look -> {
                drawCircle(colors.face, EYE, Offset(x - 2f, eyes - 1.5f))
                drawCircle(colors.face, EYE, Offset(x + 5f, eyes - 1.5f))
            }
            else -> {
                drawCircle(colors.face, EYE, Offset(x - 3.5f, eyes))
                drawCircle(colors.face, EYE, Offset(x + 3.5f, eyes))
            }
        }
        when (mood) {
            Mood.Happy -> drawPath(Path().apply { moveTo(x - 4, eyes + 5); quadraticTo(x, eyes + 9, x + 4, eyes + 5) }, colors.face, style = pen)
            Mood.Wave -> drawPath(Path().apply { moveTo(x - 4.5f, eyes + 5); quadraticTo(x, eyes + 12, x + 4.5f, eyes + 5); close() }, colors.face)
            Mood.Sleep -> drawCircle(colors.face, 1.6f, Offset(x, eyes + 7))
            Mood.Look -> drawLine(colors.face, Offset(x - 3, eyes + 7), Offset(x + 3, eyes + 7), 2.6f, StrokeCap.Round)
            Mood.Pale -> drawPath(Path().apply { moveTo(x - 4, eyes + 8); quadraticTo(x, eyes + 4, x + 4, eyes + 8) }, colors.face, style = pen)
        }
    }

    private fun DrawScope.snores(from: Float, ink: Color, line: Stroke) {
        for ((i, z) in listOf(12f, 15f, 20f).withIndex()) {
            val left = from + i * 17
            val top = -6f - i * 13 - z
            val path = Path().apply {
                moveTo(left, top)
                lineTo(left + z, top)
                lineTo(left, top + z)
                lineTo(left + z, top + z)
            }
            drawPath(path, ink, style = Stroke(line.width * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

enum class Mood { Happy, Wave, Sleep, Look, Pale }

const val BOB_MS = 720L

// Stepped like a cartoon.
const val BOB_STEP_MS = 48L
const val BOB_STEPS = BOB_MS / BOB_STEP_MS
