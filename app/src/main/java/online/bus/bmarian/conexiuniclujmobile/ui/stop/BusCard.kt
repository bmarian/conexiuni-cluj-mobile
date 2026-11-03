package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.liveColor
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

@Composable
fun BusCard(
    live: Boolean,
    destination: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    badge: @Composable () -> Unit,
    flag: @Composable () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    times: @Composable () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).squish(press)
            .clip(CardDefaults.shape)
            .combinedClickable(interactionSource = press, indication = ripple(), onLongClick = onLongClick, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (favorite) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (favorite) BorderStroke(1.dp, Palette.Rose.copy(alpha = 0.45f)) else null,
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.width(4.dp).fillMaxHeight().padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (live) liveColor() else Color.Transparent),
            )
            badge()
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (live) LiveBadge()
                    Text(
                        "→ $destination",
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    flag()
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                times()
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
