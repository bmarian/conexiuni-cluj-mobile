package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val Round = 54.dp
private val SmallRound = 44.dp
private val SelectHeight = 38.dp
private val SelectIcon = 20.dp
private val SelectChevron = 18.dp
private val SelectText = 15.sp
private val HeaderGap = 12.dp
private const val DISABLED = 0.38f

@Composable
fun DoodleRoundButton(
    icon: ImageVector,
    label: String?,
    ink: DoodleInk,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val size = if (small) SmallRound else Round
    val shape = remember(size) { WobblyShape(size / 2, if (small) 2 else 1) }
    Box(
        modifier.size(size).alpha(if (enabled) 1f else DISABLED)
            .inkOutline(shape, ink.outline, InkThick, ink.chalk).clip(shape).background(MaterialTheme.colorScheme.surface)
            .squishClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, iconModifier, tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun <T> DoodleSelect(value: String, options: List<Pair<T, String>>, icon: ImageVector?, ink: DoodleInk, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = remember { WobblyShape(SelectHeight / 2, 6) }
    Box(modifier) {
        Row(
            Modifier.height(SelectHeight).markerFill(shape, false, ink).squishClickable { open = true }.padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            icon?.let { Icon(it, null, Modifier.size(SelectIcon)) }
            Text(value, fontSize = SelectText, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(SelectChevron))
        }
        DoodleMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((option, text) in options) {
                DropdownMenuItem(text = { Text(text, maxLines = 1) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}

@Composable
fun DoodlePageHeader(icon: @Composable () -> Unit, kicker: String, title: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = DoodleMetrics.Side, end = DoodleMetrics.Side, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(kicker, fontSize = DoodleMetrics.KindText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            title()
        }
    }
}

@Composable
fun DoodlePageTitle(text: String) {
    Text(text, fontSize = DoodleMetrics.TitleText, lineHeight = DoodleMetrics.TitleLine, fontWeight = FontWeight.Bold)
}
