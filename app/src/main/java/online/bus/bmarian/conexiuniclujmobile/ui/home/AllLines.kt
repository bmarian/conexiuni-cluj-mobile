package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.NetworkIndex
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.groupByMode
import online.bus.bmarian.conexiuniclujmobile.logic.hasTimetableEntries
import online.bus.bmarian.conexiuniclujmobile.ui.SubLabel
import online.bus.bmarian.conexiuniclujmobile.ui.cityName
import online.bus.bmarian.conexiuniclujmobile.ui.destinations.AllDestinationsRow
import online.bus.bmarian.conexiuniclujmobile.ui.modeLines
import online.bus.bmarian.conexiuniclujmobile.ui.stop.RouteRow

fun linesByMode(index: NetworkIndex?): List<Pair<Mode, List<NetworkRoute>>> =
    groupByMode(index?.bundle?.routes.orEmpty().filter { hasTimetableEntries(it.timetable) }, { it.type }, { it.shortName })

/** Port of components/home/AllLines.vue. */
fun LazyListScope.allLines(groups: List<Pair<Mode, List<NetworkRoute>>>, onOpen: (NetworkRoute) -> Unit) {
    if (groups.isEmpty()) return
    item(key = "lines-in") {
        Text(
            stringResource(R.string.w_lines_in, cityName()),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
    for ((mode, lines) in groups) {
        item(key = "lines-$mode") { SubLabel(stringResource(modeLines(mode)), Modifier.padding(top = 8.dp)) }
        items(lines, key = { "line-${it.routeId}" }) { route ->
            RouteRow(route.shortName, route.color, route.longName) { onOpen(route) }
        }
    }
    item(key = "all-destinations") { AllDestinationsRow(Modifier.padding(top = 8.dp)) }
}
