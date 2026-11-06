package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.HomeSections
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as utils/__tests__/homeSections.spec.ts. */
class HomeSectionsTest {
    @Test
    fun fallsBackToTheDefaultOrder() {
        assertEquals(HomeSections.ALL, HomeSections.order(emptyList()))
    }

    @Test
    fun keepsASavedOrderAndDropsUnknownOrRepeatedIds() {
        assertEquals(
            listOf("recents", "stops", "lines", "groups", "places", "reminders"),
            HomeSections.order(listOf("recents", "stops", "nope", "stops", "lines", "groups", "places", "reminders")),
        )
    }

    @Test
    fun putsAMissingSectionAfterItsDefaultNeighbour() {
        assertEquals(
            listOf("recents", "lines", "groups", "stops", "places", "reminders"),
            HomeSections.order(listOf("recents", "lines", "stops", "places", "reminders")),
        )
        assertEquals(listOf("reminders", "stops", "places", "recents", "lines", "groups"), HomeSections.order(listOf("stops", "lines")))
    }

    @Test
    fun movesOnlyTheShownSections() {
        assertEquals(
            listOf("reminders", "recents", "groups", "lines", "places", "stops"),
            HomeSections.withShownOrder(HomeSections.ALL, listOf("recents", "lines", "stops")),
        )
    }
}
