package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleInk

private val ButtonHeight = 46.dp
private val ButtonIcon = 20.dp

fun Modifier.markerFill(shape: Shape, filled: Boolean, ink: DoodleInk): Modifier =
    inkOutline(shape, ink.outline, DoodleMetrics.Pen, ink.chalk).clip(shape).background(if (filled) ink.marker else Color.Transparent)

@Composable
fun DoodleButton(text: String, icon: ImageVector, filled: Boolean, ink: DoodleInk, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = remember(text) { WobblyShape(ButtonHeight / 2, text.hashCode()) }
    val content = if (filled) ink.onMarker else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.height(ButtonHeight)
            .markerFill(shape, filled, ink)
            .squishClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(DoodleMetrics.ChipInner, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(ButtonIcon), tint = content)
        Text(text, color = content, fontSize = DoodleMetrics.ChipText, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
