package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.logic.HourGroup
import online.bus.bmarian.conexiuniclujmobile.logic.formatAbsoluteMinutes
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.InkThick
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val HourHeight = 50.dp
private val HourWidth = 44.dp
private val MinuteWidth = 44.dp
private val MinuteHeight = 38.dp
private val HourText = 18.sp
private val MinuteText = 17.sp
private val BandCorner = 14.dp
private val BandOut = 6.dp
private val RuleWidth = 4.dp
private val RuleHeight = 40.dp
private val SelectCorner = 10.dp

// Loop middle at (LOOP_X, LOOP_Y) for a 44-wide minute.
private val LOOP = PathParser().parsePathString("M10 22 C8 10 22 4 32 5 C46 6 52 14 50 22 C48 31 34 35 22 34 C12 33 5 26 9 16 L14 9").toPath()
private const val LOOP_X = 30f
private const val LOOP_Y = 20f
private const val CELL = 44f
private const val RULE_LINE = 2f
private val RULE = PathParser().parsePathString("M2 2 C1 14 3 26 2 38").toPath()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoodleHourRow(group: HourGroup, selected: String?, next: String?, ink: DoodleInk, onSelect: (String) -> Unit) {
    val now = selected == null && group.chips.any { it.time == next }
    val gone = group.chips.all { it.isPast }
    val band = remember(group.hour) { WobblyShape(BandCorner, group.hour.hashCode()) }
    val pencil = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = if (now) ink.onMarker else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DoodleMetrics.Side - BandOut).heightIn(min = HourHeight)
            .then(if (now) Modifier.clip(band).background(ink.marker) else Modifier).padding(horizontal = BandOut),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.hour,
            Modifier.width(HourWidth),
            fontSize = HourText,
            fontWeight = FontWeight.ExtraBold,
            color = if (gone || group.isNextDay) pencil else strong,
        )
        Canvas(Modifier.size(RuleWidth, RuleHeight)) {
            scale(density, pivot = Offset.Zero) { drawPath(RULE, if (now) ink.onMarker else pencil, style = Stroke(RULE_LINE, cap = StrokeCap.Round)) }
        }
        FlowRow(Modifier.padding(start = 8.dp)) {
            for (chip in group.chips) {
                val isSelected = selected == chip.time
                val isNext = selected == null && next == chip.time
                val shape = remember(chip.time) { WobblyShape(SelectCorner, chip.time.hashCode()) }
                val loop = if (now) ink.onMarker else MaterialTheme.colorScheme.onSurface
                Box(
                    Modifier.width(MinuteWidth).height(MinuteHeight).squishClickable { onSelect(chip.time) }
                        .then(if (isNext) Modifier.drawBehind {
                            val k = size.width / CELL
                            translate(size.width / 2 - LOOP_X * k, size.height / 2 - LOOP_Y * k) {
                                scale(k, pivot = Offset.Zero) {
                                    drawPath(LOOP, loop, style = Stroke(InkThick.toPx() / k, cap = StrokeCap.Round))
                                }
                            }
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatAbsoluteMinutes(chip.startMin!!).drop(3),
                        Modifier.then(if (isSelected) Modifier.clip(shape).background(ink.outline).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier),
                        fontSize = MinuteText,
                        textAlign = TextAlign.Center,
                        fontWeight = when {
                            isSelected || now -> FontWeight.Bold
                            chip.isPast -> FontWeight.Normal
                            else -> FontWeight.SemiBold
                        },
                        color = when {
                            isSelected -> ink.paper
                            now -> ink.onMarker
                            chip.isPast -> pencil
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
