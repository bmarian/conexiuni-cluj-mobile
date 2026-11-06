package online.bus.bmarian.conexiuniclujmobile.ui.home

import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.FavoritePlan
import online.bus.bmarian.conexiuniclujmobile.data.FavoriteRoute
import online.bus.bmarian.conexiuniclujmobile.data.SavedGroup

/** Port of useFavoriteDrag's undo. */
object FavoriteRemoval {
    private class Saved(val routes: List<FavoriteRoute>, val stops: List<Int>, val plans: List<FavoritePlan>, val groups: List<SavedGroup>)

    private var heldFrom: Saved? = null

    private fun savedNow() = Saved(Services.favorites.routes.value, Services.favorites.stops.value, Services.favorites.plans.value, Services.groups.saved.value)

    // Taken at the start: the drag reorders the list on its way to the bar.
    fun holdStart() {
        heldFrom = savedNow()
    }

    fun dropped(title: String, undo: String, remove: () -> Unit) {
        val before = heldFrom ?: return
        heldFrom = null
        remove()
        offerUndo(before, title, undo)
    }

    private fun offerUndo(before: Saved, title: String, undo: String) {
        val favorites = Services.favorites
        val routes = before.routes.size != favorites.routes.value.size
        val stops = before.stops.size != favorites.stops.value.size
        val plans = before.plans.size != favorites.plans.value.size
        val groups = before.groups.size != Services.groups.saved.value.size
        if (!routes && !stops && !plans && !groups) return
        Services.settings.showToast(title, actionLabel = undo) {
            if (routes) favorites.reorderRoutes(before.routes)
            if (stops) favorites.reorderStops(before.stops)
            if (plans) favorites.reorderPlans(before.plans)
            if (groups) Services.groups.reorder(before.groups)
        }
    }
}
