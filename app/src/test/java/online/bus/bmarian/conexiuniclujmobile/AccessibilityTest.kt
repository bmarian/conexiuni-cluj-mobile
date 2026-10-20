package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.logic.bikeAccess
import online.bus.bmarian.conexiuniclujmobile.logic.wheelchairAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityTest {
    @Test
    fun aFeedThatSaysNothingShowsNothing() {
        val gtfsRealtime = Api.json.decodeFromString<Vehicle>("""{"id":7,"latitude":44.43,"longitude":26.1}""")
        assertNull(wheelchairAccess(gtfsRealtime))
        assertNull(bikeAccess(gtfsRealtime))
        assertNull(wheelchairAccess(Vehicle(wheelchairAccessible = "UNKNOWN")))
        assertNull(bikeAccess(Vehicle(bikeAccessible = "UNKNOWN")))
    }

    @Test
    fun whatTheFeedSaysIsShown() {
        val told = Vehicle(wheelchairAccessible = "WHEELCHAIR_ACCESSIBLE", bikeAccessible = "BIKE_INACCESSIBLE")
        assertEquals(true, wheelchairAccess(told))
        assertEquals(false, bikeAccess(told))
        assertEquals(false, wheelchairAccess(Vehicle(wheelchairAccessible = "WHEELCHAIR_INACCESSIBLE")))
        assertEquals(true, bikeAccess(Vehicle(bikeAccessible = "BIKE_ACCESSIBLE")))
    }
}
