package online.bus.bmarian.conexiuniclujmobile.ui.settings

import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleChevron
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMenu
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerFill
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import online.bus.bmarian.conexiuniclujmobile.ui.theme.standardScheme

private val SwitchWidth = 54.dp
private val SwitchHeight = 30.dp
private val Knob = 20.dp
private val KnobInset = 5.dp
private val KnobLine = 2.dp
private val RowMin = 48.dp
private val DescriptionText = 13.sp
private val DescriptionLine = 17.sp
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
internal fun DoodleSwitch(checked: Boolean, onChange: (Boolean) -> Unit, ink: DoodleInk) {
    val shape = remember { WobblyShape(SwitchHeight / 2, 3) }
    val x by animateDpAsState(if (checked) SwitchWidth - KnobInset - Knob else KnobInset, label = "knob")
    Box(
        Modifier.size(SwitchWidth, SwitchHeight)
            .markerFill(shape, checked, ink)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier.offset(x = x, y = KnobInset).size(Knob)
                .inkOutline(CircleShape, if (checked) ink.onMarker else ink.outline, KnobLine, ink.chalk)
                .clip(CircleShape)
                .background(if (checked) ink.onMarker else MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
internal fun DoodleSettingRow(title: String, description: String?, onClick: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.squishClickable(onClick = onClick) else Modifier)
            .heightIn(min = RowMin).padding(horizontal = DoodleMetrics.Side, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = DoodleMetrics.FieldText, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, fontSize = DescriptionText, lineHeight = DescriptionLine, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}


@Composable
internal fun DoodleLookTiles(look: Look, onChange: (Look) -> Unit, labels: Map<Look, String>, ink: DoodleInk) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DoodleMetrics.Side, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        for ((i, option) in Look.entries.withIndex()) {
            val chosen = option == look
            val shape = remember(i) { WobblyShape(TileCorner, i + 7) }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth()
                        .inkOutline(
                            shape, if (chosen) ink.marker else MaterialTheme.colorScheme.onSurfaceVariant, if (chosen) TileChosenLine else TileLine, ink.chalk,
                        )
                        .clip(shape)
                        .squishClickable { onChange(option) }
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (option == Look.Doodle) DoodlePreview(ink) else StandardPreview()
                    Text(
                        labels.getValue(option),
                        fontSize = TileText,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = BadgeOut, y = -BadgeOut).size(Badge).clip(CircleShape).background(ink.marker),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, null, Modifier.size(BadgeIcon), tint = ink.onMarker) }
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
            Character(Mode.BUS, ink.marker, "", Mood.Happy, Modifier.width(PreviewBus))
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

@Composable
internal fun DoodleLanguageRow(title: String, locale: String, languages: List<Pair<String, String>>, onPick: (String) -> Unit, ink: DoodleInk) {
    var open by remember { mutableStateOf(false) }
    Box {
        DoodleSettingRow(title, null, onClick = { open = true }) {
            val name = languages.firstOrNull { it.first == locale }?.second.orEmpty()
            Text(name, Modifier.align(Alignment.CenterVertically), fontSize = DoodleMetrics.RowSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DoodleChevron()
        }
        DoodleMenu(expanded = open, onDismissRequest = { open = false }, offset = DpOffset(DoodleMetrics.Side, 0.dp)) {
            for ((tag, name) in languages) {
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onPick(tag)
                })
            }
        }
    }
}

