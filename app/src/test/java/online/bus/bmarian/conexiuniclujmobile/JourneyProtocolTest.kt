package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.journey.Journey
import online.bus.bmarian.conexiuniclujmobile.journey.simplifyPath
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyProtocolTest {
    @Test
    fun aPlanWithoutPathsStillReads() {
        // Plan from before paths and walks.
        val json = """{"id":"a","destination":{"id":-1,"name":"Unirii","lat":46.77,"lon":23.59},
            "legs":[{"routeIds":[5],"tripIds":["5_0"],"lines":["5"],"colors":["#ff0000"],
            "stops":[{"id":1,"name":"A","lat":46.78,"lon":23.62},{"id":2,"name":"B","lat":46.77,"lon":23.6}]}]}"""
        val journey = Api.json.decodeFromString<Journey>(json)
        assertTrue(journey.legs.single().path.isEmpty())
        assertTrue(journey.walks.isEmpty())
    }

    @Test
    fun pathsAreThinnedButKeepTheirEnds() {
        val dense = (0..1000).map { LatLon(46.77 + it * 0.000009, 23.6) }
        val thin = simplifyPath(dense, minMeters = 12.0)
        assertTrue(thin.size in 60..100)
        assertEquals(dense.first().lat, thin.first().lat, 1e-5)
        assertEquals(dense.last().lat, thin.last().lat, 1e-5)
        thin.zipWithNext().dropLast(1).forEach { (a, b) -> assertTrue(haversineMeters(a.lat, a.lon, b.lat, b.lon) >= 11.0) }
    }
}
