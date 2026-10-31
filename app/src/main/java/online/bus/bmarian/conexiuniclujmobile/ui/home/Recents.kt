package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.Place
import online.bus.bmarian.conexiuniclujmobile.data.Recent
import online.bus.bmarian.conexiuniclujmobile.data.RecentPlan
import online.bus.bmarian.conexiuniclujmobile.data.RecentRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentStop
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationIcon
import online.bus.bmarian.conexiuniclujmobile.ui.StationLines

private const val PICKS_SHOWN = 5

class RecentItem(val recent: Recent, val plan: FavoritePlan? = null, val stop: Stop? = null, val route: Route? = null)

/** Port of useRecents. */
fun recentItems(
    recents: List<Recent>,
    stops: List<Stop>,
    routes: List<Route>,
    favoritePlans: List<FavoritePlan>,
    online: Boolean,
    shown: Int,
): List<RecentItem> =
    recents.mapNotNull { recent ->
        when (recent) {
            is RecentStop -> stops.find { it.stopId == recent.stopId }?.let { RecentItem(recent, stop = it) }
            is RecentRoute -> routes.find { it.routeId == recent.routeId }?.let { RecentItem(recent, route = it) }
            is RecentPlan -> recent.plan.takeIf { p -> online && favoritePlans.none { it.samePlace(p) } }?.let { RecentItem(recent, plan = it) }
        }
    }.take(shown)

class PlacePick(val place: Place, val recent: Boolean = false)

private fun pick(lat: Double, lon: Double, label: String, recent: Boolean = false) =
    PlacePick(Place("", lat.toString(), lon.toString(), label), recent)

/** Port of usePlacePicks. */
fun placePicks(query: String, plans: List<FavoritePlan>, recents: List<Recent>, stops: List<Stop>, online: Boolean): List<PlacePick> {
    val typed = query.isNotBlank()
    val saved = (if (typed) matchingPlans(plans, query) else plans.take(PICKS_SHOWN)).map { pick(it.lat, it.lon, it.label ?: it.name) }
    if (typed) return saved
    val picks = recentItems(recents, stops, emptyList(), plans, online, PICKS_SHOWN).mapNotNull { item ->
        item.plan?.let { pick(it.lat, it.lon, it.name, recent = true) } ?: item.stop?.let { pick(it.lat, it.lon, it.name, recent = true) }
    }
    return (saved + picks).distinctBy { it.place.lat to it.place.lon }
}

@Composable
fun RecentRow(
    item: RecentItem,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: () -> Unit,
) {
    val plan = item.plan
    val stop = item.stop
    val route = item.route
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    val look = stop?.let { index?.look(it.stopId) }
    FavoriteRow(
        modifier = modifier,
        icon = {
            when {
                stop != null -> StationIcon(look, size = iconSize)
                route != null -> LineBadge(route.shortName, route.color, height = 28.dp, minWidth = 40.dp, corner = 6.dp)
                else -> RowIcon(
                    Icons.Rounded.Schedule,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    size = iconSize,
                )
            }
        },
        title = plan?.name ?: stop?.name ?: route?.longName.orEmpty(),
        titlePrefix = if (plan?.originName != null) stringResource(R.string.w_plan_to) + ": " else null,
        kicker = plan?.originName?.let { "${stringResource(R.string.w_plan_from)}: $it" },
        lines = if (stop != null) ({ StationLines(look) }) else null,
        removeLabel = stringResource(R.string.w_remove_from_recents),
        onClick = onClick,
        onRemove = { Services.favorites.removeRecent(item.recent) },
        background = background,
    )
}
