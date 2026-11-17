package online.bus.bmarian.conexiuniclujmobile.ui.home

import online.bus.bmarian.conexiuniclujmobile.ui.doodle.placeDrawing
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.inkOutline
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.InkThick
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Icon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerFill
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val CardCorner = 16.dp
private val SwapIcon = 16.dp
private val FromWidth = 110.dp
private val ChipMaxWidth = 260.dp
private const val LINE = 1.2f

@Composable
internal fun DoodleRouteCard(shortName: String, color: String, parts: List<String>, both: Boolean, ink: DoodleInk, modifier: Modifier, onClick: () -> Unit) {
    val shape = remember(shortName) { WobblyShape(CardCorner, shortName.hashCode()) }
    Row(
        modifier.heightIn(min = DoodleMetrics.CardHeight).markerFill(shape, false, ink).squishClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LineBadge(
                shortName, color,
                height = DoodleMetrics.CardBadgeHeight, minWidth = DoodleMetrics.CardBadgeWidth,
                corner = DoodleMetrics.BadgeCorner, fontSize = DoodleMetrics.BadgeText,
            )
            if (both) Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(SwapIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            for (part in parts) {
                val size = if (both) DoodleMetrics.CardSmallText else DoodleMetrics.CardText
                Text(
                    part,
                    lineHeight = size * LINE,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = DoodleMetrics.CardMinText, maxFontSize = size),
                )
            }
        }
    }
}

@Composable
internal fun DoodlePlaceChip(emoji: String?, label: String, from: String?, ink: DoodleInk, onClick: () -> Unit) {
    val shape = remember(label) { WobblyShape(DoodleMetrics.ChipHeight / 2, label.hashCode()) }
    Row(
        Modifier.widthIn(max = ChipMaxWidth).height(DoodleMetrics.ChipHeight).markerFill(shape, false, ink).squishClickable(onClick = onClick)
            .padding(start = DoodleMetrics.ChipStart, end = DoodleMetrics.ChipEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoodleMetrics.ChipInner),
    ) {
        val drawn = emoji?.let(::placeDrawing)
        if (emoji != null && drawn == null) Text(emoji, fontSize = DoodleMetrics.ChipText)
        else Icon(drawn ?: Icons.Rounded.Place, null, Modifier.size(DoodleMetrics.ChipIcon))
        if (from != null) {
            Text(
                "$from →",
                Modifier.widthIn(max = FromWidth),
                fontSize = DoodleMetrics.ChipText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(label, fontSize = DoodleMetrics.ChipText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
