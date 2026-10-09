package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.buildScene
import online.bus.bmarian.conexiuniclujmobile.ui.map.computeStartBearing
import online.bus.bmarian.conexiuniclujmobile.ui.map.headingBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSceneTest {
    private val north = listOf(LatLon(46.77, 23.60), LatLon(46.78, 23.60), LatLon(46.79, 23.60))
    private val east = listOf(LatLon(46.77, 23.60), LatLon(46.77, 23.61), LatLon(46.77, 23.62))

    @Test
    fun secondShapeOfTheSameColourIsDashed() {
        val scene = buildScene(
            listOf(MapShape("a", "25", "#ff0000", north), MapShape("b", "25", "#ff0000", east)),
            terminals = true,
            arrows = false,
        )
        assertEquals(listOf(0, 1), scene.lines.map { it.dash })
    }

    @Test
    fun differentColoursStaySolid() {
        val scene = buildScene(
            listOf(MapShape("a", "25", "#ff0000", north), MapShape("b", "35", "#0000ff", east)),
            terminals = false,
            arrows = false,
        )
        assertEquals(listOf(0, 0), scene.lines.map { it.dash })
        assertTrue(scene.badges.isEmpty() && scene.ends.isEmpty())
    }

    @Test
    fun theSamePathTwiceIsDrawnOnce() {
        val scene = buildScene(
            listOf(MapShape("a", "25", "#ff0000", north), MapShape("b", "26", "#00ff00", north)),
            terminals = false,
            arrows = false,
        )
        assertEquals(1, scene.lines.size)
        val dashed = buildScene(
            listOf(MapShape("a", "25", "#ff0000", north), MapShape("b", "26", "#ff0000", north)),
            terminals = false,
            arrows = false,
        )
        assertEquals(listOf(0, 1), dashed.lines.map { it.dash })
    }

    @Test
    fun linesStartingTogetherShareOneBadge() {
        val scene = buildScene(
            listOf(MapShape("a", "25", "#ff0000", north), MapShape("b", "35", "#0000ff", east)),
            terminals = true,
            arrows = true,
        )
        assertEquals(1, scene.badges.size)
        assertEquals(listOf("25", "35"), scene.badges.single().routes.map { it.first })
        assertEquals(2, scene.ends.size)
        assertEquals(2, scene.arrows.size)
    }

    @Test
    fun startBearingPointsAlongTheRoute() {
        assertEquals(0.0, computeStartBearing(north)!!, 1.0)
        assertEquals(90.0, computeStartBearing(east)!!, 1.0)
        assertNull(computeStartBearing(listOf(LatLon(46.77, 23.6))))
    }

    @Test
    fun busesTurnTheShortWayRound() {
        assertEquals(0.0, headingBetween(350.0, 10.0, 0.5), 1e-9)
        assertEquals(355.0, headingBetween(10.0, 340.0, 0.5), 1e-9)
        assertEquals(90.0, headingBetween(0.0, 180.0, 0.5), 1e-9)
    }
}
