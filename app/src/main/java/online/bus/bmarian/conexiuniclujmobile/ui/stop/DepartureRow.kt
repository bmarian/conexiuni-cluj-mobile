package online.bus.bmarian.conexiuniclujmobile.ui.stop

import online.bus.bmarian.conexiuniclujmobile.ui.doodle.dottedRule
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.squish


@Composable
fun DoodleBusRow(
    shortName: String,
    color: String,
    destination: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    badgeModifier: Modifier = Modifier,
    flag: @Composable () -> Unit = {},
    note: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    times: @Composable () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pencil = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().heightIn(min = DoodleMetrics.RowHeight).squish(press)
            .combinedClickable(interactionSource = press, indication = ripple(), onLongClick = onLongClick, onClick = onClick)
            .dottedRule(pencil)
            .padding(start = DoodleMetrics.Side, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.widthIn(min = DoodleMetrics.BadgeColumn)) {
            LineBadge(
                shortName, color, badgeModifier,
                height = DoodleMetrics.BadgeHeight, minWidth = DoodleMetrics.BadgeWidth, corner = DoodleMetrics.BadgeCorner, fontSize = DoodleMetrics.BadgeText,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    destination,
                    Modifier.weight(1f, fill = false),
                    fontSize = DoodleMetrics.DestinationText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                flag()
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 13.sp, color = pencil, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            note?.invoke()
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) { times() }
        trailing?.invoke()
    }
}
