package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.map.CameraView
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapController
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapShape
import online.bus.bmarian.conexiuniclujmobile.ui.map.StopHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapControllerTest {
    private val line = MapShape("35_0", "35", "#ff0000", listOf(LatLon(46.77, 23.60), LatLon(46.78, 23.60)))

    private fun MapController.showLine(owner: Any) {
        claim(owner)
        showShapes(listOf(line))
        highlightedStops = mapOf(1 to StopHighlight.Green)
        directionArrowAtStart = true
    }

    @Test
    fun aCancelledBackGestureGivesTheMapBack() {
        val map = MapController()
        val route = Any()
        val stop = Any()
        map.showLine(route)
        map.claim(stop)
        map.selectedStopId = 7
        map.release(stop)
        assertEquals(listOf(line), map.shapes)
        assertEquals("no refit", map.shapesVersion, map.handledShapes)
        assertEquals(mapOf(1 to StopHighlight.Green), map.highlightedStops)
        assertTrue(map.directionArrowAtStart)
        assertNull(map.selectedStopId)
    }

    @Test
    fun aCancelledBackGestureGivesTheCameraBack() {
        val map = MapController()
        val route = Any()
        val stop = Any()
        val atStop = CameraView(LatLon(46.77, 23.60), 16.0, 0.0, null)
        map.showLine(stop)
        map.resting = atStop
        map.claim(route)
        map.resting = CameraView(LatLon(46.76, 23.58), 12.5, 0.0, null)
        map.release(route)
        assertSame(atStop, map.cameraBack?.first)
    }

    @Test
    fun theNextScreenKeepsTheMapWhenTheLastOneLeaves() {
        val map = MapController()
        val route = Any()
        val stop = Any()
        map.showLine(route)
        map.claim(stop)
        map.selectedStopId = 7
        map.release(route)
        assertNull(map.cameraBack)
        assertTrue(map.shapes.isEmpty())
        assertEquals(7, map.selectedStopId)
        map.release(stop)
        assertNull(map.selectedStopId)
    }
}
