package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.map.SpiderLeg
import online.bus.bmarian.conexiuniclujmobile.ui.map.spiderLegs
import online.bus.bmarian.conexiuniclujmobile.ui.map.spiderRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun measuresTheReachOfTheLongestLeg() {
        assertEquals(44, spiderRadius(spiderLegs(2)))
        assertTrue(spiderRadius(spiderLegs(20)) > spiderRadius(spiderLegs(9)))
        assertEquals(0, spiderRadius(emptyList()))
    }
}
