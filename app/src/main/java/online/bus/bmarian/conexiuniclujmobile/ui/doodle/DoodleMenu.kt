package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

private val MenuCorner = 14.dp

@Composable
fun DoodleMenu(expanded: Boolean, onDismissRequest: () -> Unit, offset: DpOffset = DpOffset(0.dp, 0.dp), content: @Composable ColumnScope.() -> Unit) {
    val ink = LocalDoodle.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = drawnShape(MenuCorner, MenuDefaults.shape),
        containerColor = panelFill(MenuDefaults.containerColor),
        border = ink?.let { BorderStroke(InkThin, it.outline) },
        content = content,
    )
}
