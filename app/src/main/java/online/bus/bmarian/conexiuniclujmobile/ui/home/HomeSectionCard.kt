package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.ReorderState
import online.bus.bmarian.conexiuniclujmobile.ui.dragToReorder
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable

private val CARD_SHAPE = RoundedCornerShape(20.dp)

@Composable
fun HomeSectionCard(
    id: String,
    title: String,
    collapsed: Boolean,
    arranging: Boolean,
    reorder: ReorderState<String>,
    onToggle: () -> Unit,
    onArrange: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lifted = reorder.dragging == id
    val card = if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    Column(modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().background(card, CARD_SHAPE)) {
        SectionHeader(id, title, collapsed, arranging, reorder, onToggle, onArrange)
        AnimatedVisibility(!collapsed && !arranging, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) { content() }
        }
    }
}

@Composable
private fun SectionHeader(
    id: String,
    title: String,
    collapsed: Boolean,
    arranging: Boolean,
    reorder: ReorderState<String>,
    onToggle: () -> Unit,
    onArrange: () -> Unit,
) {
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    val state = stringResource(if (collapsed) R.string.home_section_collapsed else R.string.home_section_expanded)
    val gesture = if (arranging) {
        Modifier.dragToReorder(reorder, id)
    } else {
        Modifier.squishClickable(pressed = 0.97f, onLongClick = onArrange, onClick = onToggle).semantics { stateDescription = state }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CARD_SHAPE).then(gesture).padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (arranging) {
            Icon(Icons.Rounded.DragIndicator, null, Modifier.size(24.dp), tint = tint)
        } else {
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(24.dp).rotate(turn), tint = tint)
        }
    }
}

@Composable
fun ArrangeBar(onSave: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_sections_arrange),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onSave) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            Text(stringResource(R.string.save), Modifier.padding(start = 8.dp))
        }
    }
}
