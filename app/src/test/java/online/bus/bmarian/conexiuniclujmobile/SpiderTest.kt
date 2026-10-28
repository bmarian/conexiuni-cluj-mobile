package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.ClusterBox
import online.bus.bmarian.conexiuniclujmobile.logic.Rect
import online.bus.bmarian.conexiuniclujmobile.logic.SpiderLeg
import online.bus.bmarian.conexiuniclujmobile.logic.SpiderPlace
import online.bus.bmarian.conexiuniclujmobile.logic.spiderLegs
import online.bus.bmarian.conexiuniclujmobile.logic.spiderLegsClear
import online.bus.bmarian.conexiuniclujmobile.logic.spiderLegsToward
import online.bus.bmarian.conexiuniclujmobile.logic.spiderRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

// Port of utils/__tests__/spider.spec.ts.
class SpiderTest {
    @Test
    fun putsTwoStationsSideBySide() {
        val (a, b) = spiderLegs(2)
        assertEquals(SpiderLeg(44, 0, left = false), a)
        assertEquals(SpiderLeg(-44, 0, left = true), b)
    }

    @Test
    fun keepsEveryStationAtLeastAnIconApart() {
        for (count in listOf(3, 5, 8, 9, 14, 30)) {
            val legs = spiderLegs(count)
            assertEquals(count, legs.size)
            legs.forEachIndexed { i, a ->
                legs.drop(i + 1).forEach { b -> assertTrue(hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) >= 28) }
            }
        }
    }

    @Test
    fun labelsFaceAwayFromTheCentre() {
        for (leg in spiderLegs(8)) assertEquals(leg.x < 0, leg.left)
    }

    @Test
    fun pointsEachLegToTheSideItsStationIsOn() {
        assertEquals(SpiderLeg(0, -44, left = false), spiderLegsToward(listOf(-PI / 2)).single())
        val (west, east) = spiderLegsToward(listOf(PI - 0.2, 0.1))
        assertTrue(west.left)
        assertTrue(!east.left)
        val legs = spiderLegsToward(listOf(0.3, 0.2, 0.25))
        legs.forEachIndexed { i, a -> legs.drop(i + 1).forEach { b -> assertTrue(hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) >= 28) } }
    }

    @Test
    fun putsAFannedOutStationOnTheClearSpotNearestWhereItIs() {
        val place = SpiderPlace(0.0, -10.0, ClusterBox(24f, 24f, 12f, 12f), 80.0, 13.0)
        assertEquals(SpiderLeg(0, -44, left = false), spiderLegsClear(listOf(place), emptyList()).single())
        val above = Rect(-200.0, -300.0, 200.0, -20.0)
        assertTrue(spiderLegsClear(listOf(place), listOf(above)).single().y > -20 + 12)
        val (a, b) = spiderLegsClear(listOf(place, place), emptyList())
        assertTrue(hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) >= 13)
    }

    @Test
    fun measuresTheReachOfTheLongestLeg() {
        assertEquals(44, spiderRadius(spiderLegs(2)))
        assertTrue(spiderRadius(spiderLegs(20)) > spiderRadius(spiderLegs(9)))
        assertEquals(0, spiderRadius(emptyList()))
    }
}
