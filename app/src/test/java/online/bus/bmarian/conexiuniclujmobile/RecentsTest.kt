package online.bus.bmarian.conexiuniclujmobile

import kotlinx.serialization.json.Json
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.Favorites
import online.bus.bmarian.conexiuniclujmobile.data.RecentPlan
import online.bus.bmarian.conexiuniclujmobile.data.RecentRoute
import online.bus.bmarian.conexiuniclujmobile.data.RecentStop
import online.bus.bmarian.conexiuniclujmobile.data.Route
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.ui.home.recentItems
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    private val plan = FavoritePlan("Iulius Mall", lat = 46.77, lon = 23.63)

    @Test
    fun savedRecentsReadBackInTheirOrder() {
        val recents = listOf(RecentRoute(13), RecentStop(8), RecentPlan(plan))
        assertEquals(recents, Favorites.parseRecents(Favorites.recentsJson(recents)))
    }

    @Test
    fun readsWhatTheWebStores() {
        val raw = Json.parseToJsonElement("""[{"routeId":13},{"stopId":8},{"name":"Iulius Mall","lat":46.77,"lon":23.63},{"name":"no position"}]""")
        assertEquals(listOf(RecentRoute(13), RecentStop(8), RecentPlan(plan)), Favorites.parseRecents(raw))
    }

    @Test
    fun whatCannotOpenIsLeftOut() {
        val recents = listOf(RecentRoute(13), RecentStop(8), RecentPlan(plan))
        assertEquals(emptyList<Any>(), recentItems(recents, emptyList<Stop>(), emptyList<Route>(), listOf(plan), online = true, shown = 8))
        assertEquals(emptyList<Any>(), recentItems(listOf(RecentPlan(plan)), emptyList(), emptyList(), emptyList(), online = false, shown = 8))
    }
}
