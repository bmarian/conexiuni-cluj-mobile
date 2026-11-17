package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

private val RowMin = 54.dp
private val RowGap = 14.dp
private val Chevron = 20.dp
private val IconLine = 2.dp
private const val ICON_SHARE = 0.58f

@Composable
fun DoodleListRow(
    title: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    lines: (@Composable () -> Unit)? = null,
    titleSize: TextUnit = DoodleMetrics.RowTitle,
    background: Color = Color.Transparent,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().background(background).squishClickable(onClick = onClick).heightIn(min = RowMin)
            .padding(start = DoodleMetrics.Side, end = DoodleMetrics.Side, top = 8.dp, bottom = 8.dp),
        verticalAlignment = if (lines == null) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        icon?.invoke()
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, fontSize = titleSize, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            subtitle?.let {
                val pencil = MaterialTheme.colorScheme.onSurfaceVariant
                Text(it, fontSize = DoodleMetrics.RowSubtitle, color = pencil, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            lines?.let { Box(Modifier.padding(top = if (title == null) 0.dp else 6.dp)) { it() } }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun RowScope.DoodleChevron(modifier: Modifier = Modifier) {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        null,
        modifier.size(Chevron).align(Alignment.CenterVertically),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun DoodleRowIcon(icon: ImageVector, size: Dp = DoodleMetrics.RowIcon, emoji: String? = null) {
    val ink = LocalDoodle.current ?: return
    val shape = remember { WobblyShape(size / 2, 4) }
    Box(Modifier.size(size).inkOutline(shape, ink.outline, IconLine, ink.chalk), contentAlignment = Alignment.Center) {
        val drawn = emoji?.let(::placeDrawing)
        if (emoji != null && drawn == null) Text(emoji, fontSize = DoodleMetrics.RowTitle)
        else Icon(drawn ?: icon, null, Modifier.size(size * ICON_SHARE), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DoodleField(label: String) {
    Text(label, Modifier.padding(start = DoodleMetrics.Side, top = 12.dp), fontSize = DoodleMetrics.FieldText, fontWeight = FontWeight.SemiBold)
}
