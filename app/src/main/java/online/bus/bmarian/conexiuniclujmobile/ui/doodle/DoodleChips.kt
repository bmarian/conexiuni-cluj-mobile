package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import online.bus.bmarian.conexiuniclujmobile.ui.ChipAction
import online.bus.bmarian.conexiuniclujmobile.ui.PopIcon
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private const val PRESSED = 0.94f

// No Surface: its 48 dp touch area outgrows the marker fill.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoodleChips(actions: List<ChipAction>, ink: DoodleInk, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = DoodleMetrics.Side),
        horizontalArrangement = Arrangement.spacedBy(DoodleMetrics.ChipGap),
        verticalArrangement = Arrangement.spacedBy(DoodleMetrics.ChipGap),
    ) {
        actions.forEachIndexed { i, action -> key(i) {
            val shape = remember(i) { WobblyShape(DoodleMetrics.ChipHeight / 2, i) }
            val press = remember { MutableInteractionSource() }
            val text = if (action.selected) ink.onMarker else MaterialTheme.colorScheme.onSurface
            Row(
                Modifier.animateContentSize().squish(press, PRESSED)
                    .markerFill(shape, action.selected, ink)
                    .clickable(interactionSource = press, indication = ripple(), onClick = action.onClick)
                    .height(DoodleMetrics.ChipHeight)
                    .padding(start = DoodleMetrics.ChipStart, end = DoodleMetrics.ChipEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DoodleMetrics.ChipInner),
            ) {
                PopIcon(action.icon, action.selected, Modifier.size(DoodleMetrics.ChipIcon), tint = if (action.tint == Color.Unspecified) text else action.tint)
                Text(action.label, color = text, fontSize = DoodleMetrics.ChipText, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } }
    }
}
