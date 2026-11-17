package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
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
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.PlanOption
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.RealtimeIcon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.Icon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.WobblyShape
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerFill
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.markerSwipe
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk
import androidx.compose.ui.res.stringResource
import online.bus.bmarian.conexiuniclujmobile.logic.formatDuration

private val CardCorner = 18.dp
private val TimeText = 17.sp
private val DurationText = 18.sp
private val LegBadgeHeight = 24.dp
private val LegBadgeWidth = 34.dp
private val Walk = 20.dp
private const val SHOWN_LINES = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoodlePlanCard(
    vm: PlanViewModel,
    plan: PlanOption,
    destination: String,
    selected: Boolean,
    times: String,
    later: String?,
    live: Boolean,
    expanded: Set<String>,
    toggle: (String) -> Unit,
    ink: DoodleInk,
    onClick: () -> Unit,
) {
    val shape = remember(plan.key) { WobblyShape(CardCorner, plan.key.hashCode()) }
    val pencil = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DoodleMetrics.Side - 12.dp, vertical = 2.dp)
            .then(if (selected) Modifier.markerFill(shape, false, ink) else Modifier)
            .squishClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    times,
                    Modifier.then(if (selected) Modifier.markerSwipe(ink.marker).padding(horizontal = 5.dp) else Modifier),
                    color = if (selected) ink.onMarker else MaterialTheme.colorScheme.onSurface,
                    fontSize = TimeText,
                    fontWeight = FontWeight.Bold,
                )
                if (live) RealtimeIcon(MaterialTheme.colorScheme.onSurface, size = DoodleMetrics.Arcs)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (plan.rides.isEmpty()) Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(Walk))
                plan.rides.forEachIndexed { i, leg ->
                    val key = "list-${plan.key}-$i"
                    val open = key in expanded
                    for (id in if (open) leg.lines else leg.lines.take(SHOWN_LINES)) {
                        val line = vm.line(id) ?: continue
                        LineBadge(
                            line.shortName, line.color,
                            height = LegBadgeHeight, minWidth = LegBadgeWidth, corner = DoodleMetrics.BadgeCorner, fontSize = DoodleMetrics.RailBadgeText,
                        )
                    }
                    if (leg.lines.size > SHOWN_LINES) {
                        Text(if (open) "«" else "+${leg.lines.size - SHOWN_LINES}", Modifier.clickable { toggle(key) }, color = pencil)
                    }
                    if (i < plan.rides.lastIndex) Text("›", color = pencil)
                }
                if (plan.transfers > 0) {
                    val changes = stringResource(if (plan.transfers == 1) R.string.w_plan_change else R.string.w_plan_changes)
                    Text("${plan.transfers} $changes", Modifier.padding(start = 4.dp), fontSize = DoodleMetrics.RowSubtitle, color = pencil)
                }
            }
            Text(
                listOfNotNull("→ $destination", later).joinToString("  ·  "),
                fontSize = DoodleMetrics.RowSubtitle,
                color = pencil,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(Math.round(plan.minutes).toDouble()), fontSize = DurationText, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
