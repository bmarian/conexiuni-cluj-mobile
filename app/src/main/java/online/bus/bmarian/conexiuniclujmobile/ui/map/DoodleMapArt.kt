package online.bus.bmarian.conexiuniclujmobile.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private const val HATCH_DP = 7f
private const val HATCH_LINE_DP = 1.6f
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
private const val TREE_W_DP = 20f
private const val TREE_H_DP = 24f
private const val TREE_LINE_DP = 1.6f
private const val TRUNK = 1.4f

// Same colours as PAL and DRAWN in tools/map-style/make_styles.py.
private val PaperGround = Color(0xFFFBF6EA)
private val ChalkGround = Color(0xFF151A19)
private val PaperSpeck = Color(0xFFE6DCC6)
private val ChalkSpeck = Color(0xFF252D2B)
private val PaperGrassInk = Color(0xFF86B36E)
private val ChalkGrassInk = Color(0xFF5C8768)
private val PaperCanopy = Color(0xFFBEDDA2)
private val ChalkCanopy = Color(0xFF2E4A37)
private val PaperWaterInk = Color(0xFF3F7FA3)
private val ChalkWaterInk = Color(0xFF86B8CE)

internal fun MapImages.hatch(dark: Boolean) = draw(HATCH_DP, HATCH_DP) {
    val ink = if (dark) ChalkGrassInk else PaperGrassInk
    val width = HATCH_LINE_DP.dp.toPx()
    for (shift in -1..1) {
        val x = shift * size.width
        drawLine(ink, Offset(x, size.height), Offset(x + size.width, 0f), width, StrokeCap.Square)
    }
}

internal fun MapImages.waves(dark: Boolean) = draw(WAVES_W_DP, WAVES_H_DP) {
    val wave = WAVE_DP.dp.toPx()
    val start = Offset(size.width / 2 - wave * 2, size.height / 2)
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(start.x + wave, start.y - wave * WAVE_LIFT, start.x + wave * 2, start.y)
        quadraticTo(start.x + wave * 3, start.y + wave * WAVE_LIFT, start.x + wave * 4, start.y)
    }
    drawPath(path, if (dark) ChalkWaterInk else PaperWaterInk, style = Stroke(WAVE_LINE_DP.dp.toPx(), cap = StrokeCap.Round))
}

internal fun MapImages.paper(dark: Boolean) = draw(PAPER_DP, PAPER_DP) {
    drawRect(if (dark) ChalkGround else PaperGround)
    val random = Random(PAPER_SEED)
    repeat(SPECKS) {
        val radius = (SPECK_MIN_DP + random.nextFloat() * (SPECK_MAX_DP - SPECK_MIN_DP)).dp.toPx()
        drawCircle(if (dark) ChalkSpeck else PaperSpeck, radius, Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
    }
}

internal fun MapImages.tree(dark: Boolean) = draw(TREE_W_DP, TREE_H_DP) {
    val ink = if (dark) ChalkGrassInk else PaperGrassInk
    val line = TREE_LINE_DP.dp.toPx()
    val w = size.width
    val h = size.height
    drawLine(ink, Offset(w / 2, h * 0.55f), Offset(w / 2, h - line), line * TRUNK, StrokeCap.Round)
    val canopy = Path().apply {
        moveTo(w * 0.22f, h * 0.62f)
        cubicTo(w * 0.02f, h * 0.6f, w * 0.04f, h * 0.3f, w * 0.26f, h * 0.3f)
        cubicTo(w * 0.26f, h * 0.06f, w * 0.72f, h * 0.04f, w * 0.74f, h * 0.28f)
        cubicTo(w * 0.98f, h * 0.28f, w * 0.98f, h * 0.62f, w * 0.76f, h * 0.62f)
        close()
    }
    drawPath(canopy, if (dark) ChalkCanopy else PaperCanopy)
    drawPath(canopy, ink, style = Stroke(line, cap = StrokeCap.Round))
}
