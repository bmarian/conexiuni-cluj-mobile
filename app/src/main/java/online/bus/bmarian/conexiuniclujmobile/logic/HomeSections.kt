package online.bus.bmarian.conexiuniclujmobile.logic

/** Port of utils/homeSections.ts. */
object HomeSections {
    const val REMINDERS = "reminders"
    const val LINES = "lines"
    const val GROUPS = "groups"
    const val STOPS = "stops"
    const val PLACES = "places"
    const val RECENTS = "recents"

    val ALL = listOf(REMINDERS, LINES, GROUPS, STOPS, PLACES, RECENTS)

    /** A section missing from the saved order goes after its default predecessor. */
    fun order(saved: List<String>): List<String> {
        val order = saved.distinct().filter { it in ALL }.toMutableList()
        ALL.forEachIndexed { i, id ->
            if (id in order) return@forEachIndexed
            val before = ALL.take(i).lastOrNull { it in order }
            order.add(if (before != null) order.indexOf(before) + 1 else 0, id)
        }
        return order
    }

    /** Hidden sections keep their slots. */
    fun withShownOrder(order: List<String>, shown: List<String>): List<String> {
        val next = shown.iterator()
        return order.map { if (it in shown) next.next() else it }
    }
}
