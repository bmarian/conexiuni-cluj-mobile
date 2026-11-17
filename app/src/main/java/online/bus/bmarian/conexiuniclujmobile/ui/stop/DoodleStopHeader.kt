package online.bus.bmarian.conexiuniclujmobile.ui.stop

import androidx.compose.runtime.Composable
import online.bus.bmarian.conexiuniclujmobile.data.StationLook
import online.bus.bmarian.conexiuniclujmobile.ui.StationIcon
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodleMetrics
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodlePageHeader
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.DoodlePageTitle

@Composable
fun DoodleStopHeader(look: StationLook?, kind: String, code: String?, name: String?, favorite: Boolean?) {
    DoodlePageHeader(
        icon = { StationIcon(look, size = DoodleMetrics.StationIcon, favorite = favorite) },
        kicker = listOfNotNull(kind, code?.takeIf { it.isNotEmpty() }?.let { "#$it" }).joinToString("  "),
        title = { DoodlePageTitle(name ?: "…") },
    )
}
