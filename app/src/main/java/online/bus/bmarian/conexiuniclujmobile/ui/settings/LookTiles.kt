package online.bus.bmarian.conexiuniclujmobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.data.Look
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Character
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Icon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Mood
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.inkOutline
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDarkTheme
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle
import online.bus.bmarian.conexiuniclujmobile.ui.theme.doodleInk
import online.bus.bmarian.conexiuniclujmobile.ui.theme.standardScheme

private val TileGap = 14.dp
private val TileCorner = 18.dp
private val TileLine = 2.dp
private val TileChosenLine = 3.dp
private val TileText = 15.sp
private val Badge = 26.dp
private val BadgeIcon = 16.dp
private val BadgeOut = 8.dp
private val PreviewWidth = 74.dp
private val PreviewHeight = 104.dp
private val PreviewPad = 6.dp
private val PreviewGap = 5.dp
private val PreviewBar = 12.dp
private val PreviewCard = 16.dp
private val PreviewCorner = 6.dp
private val PreviewLine = 1.4.dp
private val PreviewBus = 40.dp

@Composable
internal fun LookTiles(look: Look, onChange: (Look) -> Unit, labels: Map<Look, String>, ink: DoodleInk?) {
    val scheme = MaterialTheme.colorScheme
    val accent = ink?.marker ?: scheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (ink != null) DoodleMetrics.Side else 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        for ((i, option) in Look.entries.withIndex()) {
            val chosen = option == look
            val shape = remember(i, ink != null) { if (ink != null) WobblyShape(TileCorner, i + 7) else RoundedCornerShape(TileCorner) }
            val rim = when {
                chosen -> accent
                ink != null -> scheme.onSurfaceVariant
                else -> scheme.outlineVariant
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth()
                        .inkOutline(shape, rim, if (chosen) TileChosenLine else TileLine, ink?.chalk == true)
                        .clip(shape)
                        .squishClickable { onChange(option) }
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (option == Look.Doodle) DoodlePreview(ink ?: doodleInk(LocalDarkTheme.current)) else StandardPreview()
                    Text(
                        labels.getValue(option),
                        fontSize = TileText,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        color = if (chosen) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = BadgeOut, y = -BadgeOut).size(Badge).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, null, Modifier.size(BadgeIcon), tint = ink?.onMarker ?: scheme.onPrimary) }
                }
            }
        }
    }
}

@Composable
private fun StandardPreview() {
    val scheme = standardScheme(LocalDarkTheme.current)
    val part = Modifier.clip(RoundedCornerShape(PreviewCorner))
    Column(
        Modifier.size(PreviewWidth, PreviewHeight).clip(RoundedCornerShape(PreviewCorner * 2))
            .background(scheme.surface).padding(PreviewPad),
        verticalArrangement = Arrangement.spacedBy(PreviewGap),
    ) {
        Box(part.fillMaxWidth().heightIn(min = PreviewBar).background(scheme.surfaceContainerHighest))
        Box(part.fillMaxWidth().weight(1f).background(scheme.surfaceContainer))
        Box(part.fillMaxWidth().heightIn(min = PreviewCard).background(scheme.surfaceContainerHigh))
        Box(part.fillMaxWidth().heightIn(min = PreviewCard).background(scheme.surfaceContainerHigh))
    }
}

@Composable
private fun DoodlePreview(ink: DoodleInk) {
    val frame = remember { WobblyShape(PreviewCorner * 2, 1) }
    Column(
        Modifier.size(PreviewWidth, PreviewHeight).clip(frame).background(ink.paper).padding(PreviewPad),
        verticalArrangement = Arrangement.spacedBy(PreviewGap),
    ) {
        PreviewPart(PreviewBar, 0, ink)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            CompositionLocalProvider(LocalDoodle provides ink) { Character(Mode.BUS, ink.marker, "", Mood.Happy, Modifier.width(PreviewBus)) }
        }
        PreviewPart(PreviewCard, 1, ink)
        PreviewPart(PreviewCard, 2, ink)
    }
}

@Composable
private fun PreviewPart(height: Dp, seed: Int, ink: DoodleInk) {
    val shape = remember(seed) { WobblyShape(height / 2, seed) }
    Box(Modifier.fillMaxWidth().heightIn(min = height).inkOutline(shape, ink.outline, PreviewLine, ink.chalk))
}
