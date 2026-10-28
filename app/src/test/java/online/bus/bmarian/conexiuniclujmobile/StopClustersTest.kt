package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.ClusterBox
import online.bus.bmarian.conexiuniclujmobile.logic.GroupName
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.StopClusters
import online.bus.bmarian.conexiuniclujmobile.logic.StopGroup
import online.bus.bmarian.conexiuniclujmobile.logic.StopPoint
import online.bus.bmarian.conexiuniclujmobile.logic.convexHull
import online.bus.bmarian.conexiuniclujmobile.logic.groupName
import online.bus.bmarian.conexiuniclujmobile.logic.mercatorX
import online.bus.bmarian.conexiuniclujmobile.logic.mercatorY
import online.bus.bmarian.conexiuniclujmobile.logic.modeMask
import online.bus.bmarian.conexiuniclujmobile.logic.modesIn
import online.bus.bmarian.conexiuniclujmobile.logic.roundedHull
import online.bus.bmarian.conexiuniclujmobile.logic.stationRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow

// Port of utils/__tests__/stopClusters.spec.ts.
class StopClustersTest {
    private val icon = ClusterBox(24f, 24f, 12f, 12f)
    private val pill = ClusterBox(100f, 40f, 50f, 15f)
    private val box = { group: StopGroup -> if (group.stations.size == 1) icon else pill }

    // 0.0001° of longitude is ~37 dp at zoom 18 and ~5 dp at zoom 15.
    private fun point(id: Int, lon: Double, station: Int = id, rank: Int = 1, lat: Double = 44.43) =
        StopPoint(id, lat, 26.1 + lon, station, modeMask(listOf(3)), rank, false)

    private fun sizes(clusters: StopClusters, zoom: Double) = clusters.at(zoom).map { it.points.size }.sorted()

    @Test
    fun keepsStationsApartWhileTheirIconsDoNotTouch() {
        val clusters = StopClusters(listOf(point(1, 0.0), point(2, 0.0002)), box, 19.0, 0.25)
        assertEquals(listOf(1, 1), sizes(clusters, 18.0))
        assertEquals(listOf(2), sizes(clusters, 15.0))
    }

    @Test
    fun sitsOnTheHighestRankedStation() {
        val clusters = StopClusters(listOf(point(1, 0.0), point(2, 0.0001, rank = 50_000)), box, 19.0, 0.25)
        val group = clusters.at(14.0).single()
        assertEquals(2, clusters.points[group.leader].id)
    }

    @Test
    fun countsPlatformsOfOneStationOnce() {
        val clusters = StopClusters(listOf(point(1, 0.0, station = 7), point(2, 0.00005, station = 7)), box, 19.0, 0.25)
        assertEquals(1, clusters.at(17.0).single().stations.size)
    }

    @Test
    fun absorbsWhatItsOwnPillAndLabelCover() {
        val clusters = StopClusters(listOf(point(1, 0.0), point(2, 0.00005), point(3, 0.0004)), box, 19.0, 0.25)
        assertEquals(listOf(1, 2), sizes(clusters, 17.0))
        assertEquals(listOf(3), sizes(clusters, 16.0))
    }

    @Test
    fun expandsToWhereTheGroupBreaksUp() {
        val clusters = StopClusters(listOf(point(1, 0.0), point(2, 0.0002)), box, 19.0, 0.25)
        val group = clusters.at(14.0).single()
        val e = clusters.expansion(group, 14.0)!!
        assertTrue(e.split > 14.0)
        assertTrue(e.apart >= e.split)
        assertTrue(clusters.at(e.apart).all { it.stations.size == 1 })
    }

    @Test
    fun hasNoExpansionForStationsThatShareASpot() {
        val clusters = StopClusters(listOf(point(1, 0.0), point(2, 0.000005)), box, 19.0, 0.25)
        val group = clusters.at(14.0).single()
        assertEquals(2, group.stations.size)
        assertNull(clusters.expansion(group, 14.0))
        assertNull(clusters.expansion(clusters.at(19.0).single(), 19.0))
    }

    @Test
    fun givesEveryStopToItsNearestMarkerAndNoMarkersOverlap() {
        var seed = 7L
        fun random(): Double {
            seed = (seed * 16807) % 2147483647
            return seed / 2147483647.0
        }
        val points = (0 until 300).map { i -> point(i, random() * 0.05, lat = 44.43 + random() * 0.03, rank = (random() * 5).toInt() * 10_000) }
        val clusters = StopClusters(points, box, 19.0, 0.25)
        for (zoom in listOf(12.0, 13.5, 15.0, 16.75, 18.0)) {
            val groups = clusters.at(zoom)
            fun x(i: Int) = mercatorX(points[i].lon)
            fun y(i: Int) = mercatorY(points[i].lat)
            for (group in groups) for (i in group.points) {
                fun d(l: Int) = (x(l) - x(i)).pow(2) + (y(l) - y(i)).pow(2)
                assertEquals(groups.minOf { d(it.leader) }, d(group.leader), 1e-15)
            }
            val dp = 512 * 2.0.pow(19 - ceil((19 - zoom) / 0.25 - 1e-9) * 0.25)
            val rects = groups.map { g ->
                val b = box(g)
                val left = x(g.leader) * dp - b.anchorX
                val top = y(g.leader) * dp - b.anchorY
                doubleArrayOf(left, top, left + b.width, top + b.height)
            }
            rects.forEachIndexed { i, a ->
                rects.drop(i + 1).forEach { b -> assertFalse(a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) }
            }
        }
    }

    @Test
    fun buildsTheSameLevelsAStepAtATime() {
        var seed = 3L
        fun random(): Double {
            seed = (seed * 16807) % 2147483647
            return seed / 2147483647.0
        }
        val points = (0 until 300).map { i -> point(i, random() * 0.05, lat = 44.43 + random() * 0.03) }
        val stepped = StopClusters(points, box, 19.0, 0.25)
        while (!stepped.ready(12.0)) stepped.advance()
        assertFalse(stepped.ready(11.75))
        val direct = StopClusters(points, box, 19.0, 0.25)
        for (zoom in listOf(18.0, 15.0, 12.0)) assertEquals(direct.at(zoom).map { it.key }.sorted(), stepped.at(zoom).map { it.key }.sorted())
    }

    @Test
    fun ranksBiggerModesFirstThenMoreLines() {
        assertTrue(stationRank(listOf(1)) > stationRank(listOf(3, 3, 3, 3)))
        assertTrue(stationRank(listOf(3, 3)) > stationRank(listOf(3)))
        assertEquals(listOf(Mode.METRO, Mode.TRAM, Mode.BUS), modesIn(modeMask(listOf(3, 1, 0))))
    }

    @Test
    fun usesTheRootTheStationsShare() {
        assertEquals(GroupName("C-tin Brâncoveanu", 1), groupName("C-tin Brâncoveanu Est", listOf("C-tin Brâncoveanu Vest")))
        assertEquals(GroupName("Piața Unirii", 2), groupName("Piața Unirii 1 (M1/3)", listOf("Piata Unirii 2", "Piața Unirii", "Piața Romană")))
        assertEquals(GroupName("Mărăști", 1), groupName("Mărăști Nord", listOf("Mărăști Sud")))
        assertEquals(GroupName("Eroilor", 2), groupName("Eroilor 1 (M1/3)", listOf("Eroilor 2", "Eroilor (Bragadiru)")))
    }

    @Test
    fun keepsTheFullNameWhenTheSharedPartSaysLessThanTheRest() {
        assertEquals(GroupName("Calea Moților", 0), groupName("Calea Moților", listOf("Calea Turzii")))
        assertEquals(GroupName("Piața Romană", 0), groupName("Piața Romană", listOf("Piața Victoriei 1 (M1)")))
        assertEquals(GroupName("Universitate", 0), groupName("Universitate", listOf("Colțea")))
    }

    @Test
    fun hullDropsThePointsInside() {
        val hull = convexHull(listOf(0.0 to 0.0, 2.0 to 0.0, 1.0 to 1.0, 2.0 to 2.0, 0.0 to 2.0, 1.0 to 0.0))
        assertEquals(4, hull.size)
        assertFalse((1.0 to 1.0) in hull)
    }

    @Test
    fun givesTwoStopsAClosedShapeAroundBoth() {
        val ring = roundedHull(listOf(23.59 to 46.77, 23.6 to 46.77), 0.001)
        assertEquals(ring.first(), ring.last())
        assertEquals(23.589, ring.minOf { it.first }, 1e-6)
        assertEquals(23.601, ring.maxOf { it.first }, 1e-6)
        assertEquals(0.001 * cos(Math.toRadians(46.77)), ring.maxOf { it.second } - 46.77, 1e-6)
    }
}
