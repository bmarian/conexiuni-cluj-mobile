package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerUnderline

@Composable
internal fun DoodleLineHeader(shortName: String, color: String, kind: String, title: String, from: String?, badgeModifier: Modifier) {
    Row(
        Modifier.fillMaxWidth().padding(start = DoodleMetrics.Side, end = DoodleMetrics.Side, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LineBadge(
            shortName, color, badgeModifier,
            height = DoodleMetrics.LineBadgeHeight, minWidth = DoodleMetrics.LineBadgeWidth,
            corner = DoodleMetrics.LineBadgeCorner, fontSize = DoodleMetrics.LineBadgeText,
        )
        Column(Modifier.weight(1f)) {
            Text(kind, fontSize = DoodleMetrics.KindText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(title, fontSize = DoodleMetrics.LineTitleText, lineHeight = DoodleMetrics.LineTitleLine, fontWeight = FontWeight.Bold)
            from?.let {
                Text(
                    it,
                    Modifier.padding(top = 4.dp).markerUnderline(),
                    fontSize = DoodleMetrics.KindText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun DoodleStopsHead(stops: String, hint: String, departures: String) {
    val pencil = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(start = DoodleMetrics.Side, end = DoodleMetrics.Side, top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stops, Modifier.weight(1f), fontSize = DoodleMetrics.ListHeadText, color = pencil)
            Text(departures.uppercase(), fontSize = DoodleMetrics.ListHeadText, letterSpacing = DoodleMetrics.ListHeadSpacing, color = pencil)
        }
        Text(hint, fontSize = DoodleMetrics.ListHeadText, lineHeight = DoodleMetrics.ListHeadLine, color = pencil)
    }
}
