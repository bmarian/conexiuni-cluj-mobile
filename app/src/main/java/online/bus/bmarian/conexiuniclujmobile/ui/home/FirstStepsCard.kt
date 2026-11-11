package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.logic.FirstSteps
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationLines

/** Port of components/home/FirstSteps.vue. */
@Composable
fun FirstStepsCard(
    left: List<String>,
    nearest: Stop?,
    nearestLook: StationLook?,
    onPlan: () -> Unit,
    onFindStop: () -> Unit,
    onOpenStop: (Stop) -> Unit,
    onSaveStop: (Stop) -> Unit,
    onLines: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.w_first_steps),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.w_first_steps_count, FirstSteps.ALL.size - left.size, FirstSteps.ALL.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onHide) {
                Icon(Icons.Rounded.Close, stringResource(R.string.w_first_steps_hide), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(bottom = 6.dp)) {
            for (step in left) key(step) {
                when {
                    step == FirstSteps.PLAN -> StepRow(Icons.Rounded.Map, stringResource(R.string.w_plan_title), onPlan)
                    step == FirstSteps.STOP && nearest != null -> FavoriteRow(
                        modifier = Modifier,
                        icon = { StationIcon(nearestLook, size = 30.dp) },
                        title = nearest.name,
                        kicker = stringResource(R.string.w_nearest_stop),
                        lines = { StationLines(nearestLook) },
                        onClick = { onOpenStop(nearest) },
                        background = Color.Transparent,
                        trailing = { TextButton(onClick = { onSaveStop(nearest) }) { Text(stringResource(R.string.save)) } },
                    )
                    step == FirstSteps.STOP -> StepRow(Icons.Rounded.Place, stringResource(R.string.w_first_step_stop), onFindStop)
                    else -> StepRow(Icons.Rounded.DirectionsBus, stringResource(R.string.w_first_step_line), onLines)
                }
            }
        }
    }
}

@Composable
private fun StepRow(icon: ImageVector, title: String, onClick: () -> Unit) = FavoriteRow(
    modifier = Modifier,
    icon = { RowIcon(icon, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 30.dp) },
    title = title,
    kicker = null,
    onClick = onClick,
    background = Color.Transparent,
    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) },
)
