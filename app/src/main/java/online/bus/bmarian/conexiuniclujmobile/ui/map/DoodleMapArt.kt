package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.logic.Wobble
import online.bus.bmarian.conexiuniclujmobile.ui.theme.doodleInk
import kotlin.math.roundToInt
import kotlin.random.Random

private const val WAVES_W_DP = 56f
private const val WAVES_H_DP = 40f
private const val WAVE_DP = 5f
private const val WAVE_LIFT = 0.8f
private const val WAVE_LINE_DP = 1.8f
private const val PAPER_DP = 96f
private const val SPECKS = 46
private const val SPECK_MIN_DP = 0.5f
private const val SPECK_MAX_DP = 1.3f
private const val PAPER_SEED = 7
private const val GROVE_DP = 120f
private const val TREE_W_DP = 16f
private const val TREE_LINE_DP = 1.4f
private const val TRUNK = 1.4f
private const val TREE_ASPECT = 1.2f

private const val INK_PERIOD_DP = 160f
private const val INK_CORE_DP = 2.3f
private const val INK_SWELL_DP = 0.45f
private const val INK_DRIFT_DP = 0.9f
private const val INK_RIM_DP = 0.85f
private const val INK_RIM_SWELL_DP = 0.35f
internal const val INK_LINE_DP = 2 * (INK_CORE_DP + INK_SWELL_DP + INK_DRIFT_DP + INK_RIM_DP + INK_RIM_SWELL_DP)
private const val DRIFT_WAVES = 3
private const val SWELL_WAVES = 7
private const val RIM_WAVES = 13
private const val PENCIL_SEED = 11
private const val FILL_STROKES = 9
private const val FILL_OVERLAP = 1.25f
private const val FILL_MIN_ALPHA = 0.4f
private const val GRAPHITE_PASSES = 2
private const val GRAPHITE_SEED = 10
private const val GRAPHITE_SPREAD = 0.6f
private const val GRAPHITE_WIDTH = 0.75f
private const val GRAPHITE_ALPHA = 0.8f
private const val GRAIN_SPECKS = 420
private const val GRAIN_MIN_DP = 0.15f
private const val GRAIN_MAX_DP = 0.4f
private const val GRAIN_MIN_ALPHA = 0.35f

private class GroveTree(val xFraction: Float, val yFraction: Float, val grow: Float)

private val Grove = listOf(GroveTree(0.23f, 0.25f, 1f), GroveTree(0.72f, 0.74f, 0.85f), GroveTree(0.77f, 0.17f, 0.7f))

// Same colours as PAL and DRAWN in tools/map-style/make_styles.py.
private val PaperGround = Color(0xFFFBF6EA)
private val ChalkGround = Color(0xFF151A19)
private val PaperSpeck = Color(0xFFE6DCC6)
private val ChalkSpeck = Color(0xFF252D2B)
private val PaperGrassInk = Color(0xFF6F9F55)
private val ChalkGrassInk = Color(0xFF6F9F7A)
private val PaperCanopy = Color(0xFFB0D58F)
private val ChalkCanopy = Color(0xFF2F5039)
private val PaperWaterInk = Color(0xFF3F7FA3)
private val ChalkWaterInk = Color(0xFF86B8CE)

internal fun MapImages.waves(dark: Boolean) = draw(WAVES_W_DP, WAVES_H_DP) {
    val wave = WAVE_DP.dp.toPx()
    val start = Offset(size.width / 2 - wave * 2, size.height / 2)
    drawPath(squiggle(start, wave), if (dark) ChalkWaterInk else PaperWaterInk, style = Stroke(WAVE_LINE_DP.dp.toPx(), cap = StrokeCap.Round))
}

private fun squiggle(start: Offset, wave: Float) = Path().apply {
    moveTo(start.x, start.y)
    quadraticTo(start.x + wave, start.y - wave * WAVE_LIFT, start.x + wave * 2, start.y)
    quadraticTo(start.x + wave * 3, start.y + wave * WAVE_LIFT, start.x + wave * 4, start.y)
}

internal fun MapImages.paper(dark: Boolean) = draw(PAPER_DP, PAPER_DP) {
    drawRect(if (dark) ChalkGround else PaperGround)
    val random = Random(PAPER_SEED)
    repeat(SPECKS) {
        val radius = (SPECK_MIN_DP + random.nextFloat() * (SPECK_MAX_DP - SPECK_MIN_DP)).dp.toPx()
        drawCircle(if (dark) ChalkSpeck else PaperSpeck, radius, Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
    }
}

internal fun MapImages.trees(dark: Boolean) = draw(GROVE_DP, GROVE_DP) {
    val ink = if (dark) ChalkGrassInk else PaperGrassInk
    val canopy = if (dark) ChalkCanopy else PaperCanopy
    val line = TREE_LINE_DP.dp.toPx()
    for (tree in Grove) {
        val w = TREE_W_DP.dp.toPx() * tree.grow
        val h = w * TREE_ASPECT
        translate(size.width * tree.xFraction - w / 2, size.height * tree.yFraction - h / 2) {
            drawLine(ink, Offset(w / 2, h * 0.55f), Offset(w / 2, h - line), line * TRUNK, StrokeCap.Round)
            val crown = Path().apply {
                moveTo(w * 0.22f, h * 0.62f)
                cubicTo(w * 0.02f, h * 0.6f, w * 0.04f, h * 0.3f, w * 0.26f, h * 0.3f)
                cubicTo(w * 0.26f, h * 0.06f, w * 0.72f, h * 0.04f, w * 0.74f, h * 0.28f)
                cubicTo(w * 0.98f, h * 0.28f, w * 0.98f, h * 0.62f, w * 0.76f, h * 0.62f)
                close()
            }
            drawPath(crown, canopy)
            drawPath(crown, ink, style = Stroke(line, cap = StrokeCap.Round))
        }
    }
}

internal fun MapImages.inkLine(color: Color, dark: Boolean) = draw(INK_PERIOD_DP, INK_LINE_DP) {
    val w = size.width
    val h = size.height
    val count = w.roundToInt()
    fun wave(x: Float, waves: Int, seed: Int) = Wobble.noise(x / w * waves, waves, seed)
    val xs = FloatArray(count + 1) { it * w / count }
    val centre = FloatArray(xs.size) { h / 2 + INK_DRIFT_DP.dp.toPx() * wave(xs[it], DRIFT_WAVES, 1) }
    val half = FloatArray(xs.size) { (INK_CORE_DP + INK_SWELL_DP * wave(xs[it], SWELL_WAVES, 2)).dp.toPx() }
    val rim = FloatArray(xs.size) { (INK_RIM_DP + INK_RIM_SWELL_DP * wave(xs[it], RIM_WAVES, 3)).dp.toPx() }
    val random = Random(PENCIL_SEED)
    for (i in 0 until FILL_STROKES) {
        val across = (i + 0.5f) / FILL_STROKES * 2 - 1
        val alpha = FILL_MIN_ALPHA + random.nextFloat() * (1 - FILL_MIN_ALPHA)
        val stroke = Stroke(2 * half.average().toFloat() / FILL_STROKES * FILL_OVERLAP)
        drawPath(trace(xs) { centre[it] + half[it] * across }, color.copy(alpha = alpha), style = stroke)
    }
    val ink = doodleInk(dark).rim
    for (side in listOf(-1, 1)) {
        for (pass in 0 until GRAPHITE_PASSES) {
            val seed = GRAPHITE_SEED + side * GRAPHITE_PASSES + pass
            val edge = trace(xs) { centre[it] + side * (half[it] + rim[it] / 2) + rim[it] * GRAPHITE_SPREAD * wave(xs[it], RIM_WAVES, seed) }
            drawPath(edge, ink.copy(alpha = GRAPHITE_ALPHA), style = Stroke(rim.average().toFloat() * GRAPHITE_WIDTH, cap = StrokeCap.Round))
        }
    }
    repeat(GRAIN_SPECKS) {
        val radius = (GRAIN_MIN_DP + random.nextFloat() * (GRAIN_MAX_DP - GRAIN_MIN_DP)).dp.toPx()
        val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val paper = Color.Black.copy(alpha = GRAIN_MIN_ALPHA + random.nextFloat() * (1 - GRAIN_MIN_ALPHA))
        for (shift in listOf(-w, 0f, w)) drawCircle(paper, radius, at + Offset(shift, 0f), blendMode = BlendMode.DstOut)
    }
}

private fun trace(xs: FloatArray, y: (Int) -> Float) = Path().apply {
    moveTo(xs[0], y(0))
    for (i in 1 until xs.size) lineTo(xs[i], y(i))
}
