package online.bus.bmarian.conexiuniclujmobile.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.LocalSheet
import online.bus.bmarian.conexiuniclujmobile.ui.SheetSnap
import online.bus.bmarian.conexiuniclujmobile.ui.SheetState

/** Port of useStretchLabel in composables/useGroupBuilder.ts. */
@Composable
fun stretchLabel(from: String?, to: String?): String = when {
    from != null && to != null -> "$from → $to"
    from != null -> stringResource(R.string.w_from, from)
    to != null -> stringResource(R.string.group_until, to)
    else -> stringResource(R.string.group_whole_line)
}

@Composable
fun GroupTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

/** How much of the page is below the screen, down to the sheet's half height. Read in layout or draw. */
private fun SheetState?.hiddenPx() = this?.run { minOf(offsetPx, anchored.anchors.positionOf(SheetSnap.Half).takeUnless { it.isNaN() } ?: offsetPx) } ?: 0f

/** Lets the end of a list scroll into what the sheet shows. */
@Composable
fun HiddenSpace() {
    val sheet = LocalSheet.current
    Spacer(
        Modifier.layout { measurable, constraints ->
            val height = sheet.hiddenPx().roundToInt()
            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(placeable.width, height) { placeable.place(0, 0) }
        },
    )
}

/** Stays at the bottom of what the sheet shows. */
@Composable
fun GroupBar(content: @Composable RowScope.() -> Unit) {
    val sheet = LocalSheet.current
    val navigationBar = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        Modifier.fillMaxWidth().offset { IntOffset(0, -sheet.hiddenPx().roundToInt()) }
            // Covers the list down to the screen's edge.
            .drawBehind { drawRect(surface, size = size.copy(height = size.height + sheet.hiddenPx() + navigationBar)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
