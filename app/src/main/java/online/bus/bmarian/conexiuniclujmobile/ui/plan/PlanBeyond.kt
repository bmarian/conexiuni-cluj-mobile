package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.PlanBeyond
import online.bus.bmarian.conexiuniclujmobile.logic.formatMeters
import online.bus.bmarian.conexiuniclujmobile.ui.home.InfoRow

@Composable
fun PlanBeyondRow(beyond: PlanBeyond) {
    val end = beyond.to ?: beyond.from ?: return
    InfoRow(
        stringResource(if (beyond.to != null) R.string.w_plan_beyond_to else R.string.w_plan_beyond_from),
        stringResource(R.string.w_plan_beyond_stop, formatMeters(end.meters.toDouble())),
    )
}
