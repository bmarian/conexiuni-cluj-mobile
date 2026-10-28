package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.Area
import online.bus.bmarian.conexiuniclujmobile.logic.AreaClusters
import online.bus.bmarian.conexiuniclujmobile.logic.ClusterBox
import online.bus.bmarian.conexiuniclujmobile.logic.StopClusters
import online.bus.bmarian.conexiuniclujmobile.logic.StopGroup
import online.bus.bmarian.conexiuniclujmobile.logic.StopPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Port of utils/__tests__/areaClusters.spec.ts.
class AreaClustersTest {
    private val box = { _: StopGroup -> ClusterBox(100f, 40f, 50f, 15f) }

    // 1 km of longitude here is ~0.0132°.
    private val km = 0.0132

    private fun stop(id: Int, lat: Double, lon: Double) = StopPoint(id, lat, lon, id, 1, 1, false)

    private fun clustersOf(areas: List<Area>, points: List<StopPoint>) = AreaClusters(StopClusters(points, box, 19.0, 0.25), areas, box)

    private fun names(clusters: AreaClusters, zoom: Double) = clusters.at(zoom).map(clusters::name)

    private val areas = listOf(
        Area("Cluj-Napoca", 46.770, 23.590, "city"),
        Area("Mănăștur", 46.755, 23.557, "suburb"),
        Area("Florești", 46.744, 23.485, "village"),
    )
    private val points = areas.flatMapIndexed { i, a -> (0 until 3).map { k -> stop(i * 10 + k, a.lat + k * 0.0005, a.lon + k * 0.0005) } }

    @Test
    fun startsWithEveryAreaOnItsOwnStops() {
        assertEquals(listOf("Cluj-Napoca", "Mănăștur", "Florești"), names(clustersOf(areas, points), 19.0))
    }

    @Test
    fun namesTheWholeMapAfterTheBiggestTownFromFarOut() {
        assertEquals(listOf("Cluj-Napoca"), names(clustersOf(areas, points), 8.0))
    }

    @Test
    fun neverLetsASmallerPlaceNameTheStopsOfABiggerOne() {
        val line = listOf(
            Area("City", 46.77, 23.59, "city"),
            Area("Suburb", 46.77, 23.59 - km, "suburb"),
            Area("Neighbourhood", 46.77, 23.59 - 2.4 * km, "neighbourhood"),
        )
        // The suburb's stops lie nearer the neighbourhood than the city.
        val lons = listOf(23.59, 23.59 - 1.6 * km, 23.59 - 2.4 * km)
        val clusters = clustersOf(line, lons.flatMapIndexed { i, lon -> (0 until 3).map { k -> stop(i * 3 + k, 46.77, lon) } })
        val zoom = (0 until 45).map { 19 - it * 0.25 }.first { "Suburb" !in names(clusters, it) }
        assertTrue("Neighbourhood" in names(clusters, zoom))
        assertTrue(clusters.at(zoom).first { clusters.name(it) == "City" }.points.containsAll(listOf(3, 4, 5)))
    }
}
