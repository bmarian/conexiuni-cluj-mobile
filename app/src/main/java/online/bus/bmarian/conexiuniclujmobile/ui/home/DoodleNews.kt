package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Character
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Icon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Mood
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.dottedRule
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerFill
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.modeColor
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val TitleText = 30.sp
private val Waver = 96.dp
private val ItemTitle = 17.sp
private val ItemMeta = 13.sp
private val ItemMetaLine = 17.sp
private val CardCorner = 18.dp
private val External = 13.dp

@Composable
internal fun DoodleNewsHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DoodleMetrics.Side),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, Modifier.padding(bottom = 6.dp), fontSize = TitleText, fontWeight = FontWeight.ExtraBold)
        Character(Mode.BUS, modeColor(Mode.BUS), "", Mood.Wave, Modifier.width(Waver))
    }
}

@Composable
internal fun DoodleFeedRow(
    title: String,
    meta: String,
    isNew: Boolean,
    external: Boolean,
    enabled: Boolean,
    ink: DoodleInk,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)?,
) {
    val shape = remember(title) { WobblyShape(CardCorner, title.hashCode()) }
    val pencil = MaterialTheme.colorScheme.onSurfaceVariant
    val frame = if (isNew) {
        Modifier.padding(horizontal = DoodleMetrics.Side, vertical = 6.dp).markerFill(shape, false, ink).padding(horizontal = 14.dp, vertical = 12.dp)
    } else {
        Modifier.dottedRule(pencil).padding(horizontal = DoodleMetrics.Side, vertical = 12.dp)
    }
    Row(
        Modifier.fillMaxWidth().squishClickable(enabled = enabled, onClick = onClick).then(frame),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        badge?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = ItemTitle, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meta, fontSize = ItemMeta, lineHeight = ItemMetaLine, color = pencil)
                if (external) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(External), tint = pencil)
            }
        }
    }
}
