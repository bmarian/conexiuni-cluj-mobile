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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.inkOutline
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val SwitchWidth = 54.dp
private val SwitchHeight = 30.dp
private val Knob = 20.dp
private val KnobInset = 5.dp
private val KnobLine = 2.dp
private val RowMin = 48.dp
private val DescriptionText = 13.sp
private val DescriptionLine = 17.sp

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

