package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.City
import online.bus.bmarian.conexiuniclujmobile.data.Credit
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityTest {
    @Test
    fun theWatchGetsOneFavoritesItemPerCity() {
        // Older watch builds read only Cluj's.
        assertEquals("/sync/favorites", WearLink.favoritesPath("cluj"))
        assertEquals("/sync/favorites/iasi", WearLink.favoritesPath("iasi"))
        assertEquals("cluj", WearLink.favoritesCity("/sync/favorites"))
        assertEquals("iasi", WearLink.favoritesCity("/sync/favorites/iasi"))
        assertNull(WearLink.favoritesCity("/sync/language"))
    }

    @Test
    fun followsSavedBeforeCitiesAreCluj() {
        val saved = """{"stopId":2,"stopName":"B","routeId":57,"tripId":"57_0","shortName":"57","color":"#ff0000","destination":"Somewhere"}"""
        assertEquals("cluj", Api.json.decodeFromString<Followed>(saved).city)
        val iasi = Followed(2, "B", 57, "57_0", "57", "#ff0000", "Somewhere", "iasi")
        assertEquals(iasi, Api.json.decodeFromString<Followed>(Api.json.encodeToString(iasi)))
    }

    @Test
    fun creditsNameTheSourcesThenTheOperator() {
        val cluj = City("cluj", "Cluj-Napoca", "CTP Cluj-Napoca", lat = 46.77, lon = 23.59, credits = listOf(Credit("tranzy.ai", "https://tranzy.ai/")))
        assertEquals("© OpenMapTiles © OpenStreetMap | © tranzy.ai, © CTP Cluj-Napoca", cluj.creditsText)
        assertEquals("© OpenMapTiles © OpenStreetMap | © TPBI", City("bucuresti", "București", "TPBI", lat = 44.4, lon = 26.1).creditsText)
    }
}
