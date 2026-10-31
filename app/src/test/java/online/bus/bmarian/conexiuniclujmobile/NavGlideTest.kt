package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.journey.Glide
import online.bus.bmarian.conexiuniclujmobile.journey.NavGlide
import online.bus.bmarian.conexiuniclujmobile.journey.WALK_ACCEL
import online.bus.bmarian.conexiuniclujmobile.logic.ACCEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class NavGlideTest {
    private fun noisy(speed: Double, accel: Double): List<Pair<Double, Glide>> {
        val random = Random(1)
        val glide = NavGlide(100_000.0, accel)
        var wander = 0.0
        return (0 until 200).map { i ->
            wander = wander * 0.7 + random.nextGaussian() * 2.1
            val real = i * speed
            real to glide.update(real + wander, 5.0, i * 1000L)
        }
    }

    @Test
    fun itNeverGoesBack() {
        noisy(1.4, WALK_ACCEL).zipWithNext { (_, a), (_, b) -> assertTrue(b.along >= a.along) }
    }

    @Test
    fun itWalksStraightThroughTheNoise() {
        val seen = noisy(1.4, WALK_ACCEL).drop(20).map { it.second.along }
        val raw = noisy(1.4, 100.0).drop(20).map { it.second.along }
        fun jolts(along: List<Double>) = along.zipWithNext { a, b -> abs(b - a - 1.4) }.average()
        assertTrue(jolts(seen) < jolts(raw) / 2)
    }

    @Test
    fun itStaysWithTheBus() {
        noisy(9.0, ACCEL).drop(20).forEach { (real, glide) -> assertEquals(real, glide.along, 15.0) }
    }

    @Test
    fun itStopsWhenTheyStop() {
        val glide = NavGlide(10_000.0, ACCEL)
        for (i in 0..30) glide.update(i * 9.0, 5.0, i * 1000L)
        var last = glide.update(270.0, 5.0, 31_000)
        for (i in 32..45) last = glide.update(270.0, 5.0, i * 1000L)
        assertEquals(last.along, last.keys.last(), 1.0)
    }

    @Test
    fun itGoesNoFurtherThanTheLine() {
        val glide = NavGlide(100.0, ACCEL)
        var last = glide.update(0.0, 5.0, 0)
        for (i in 1..20) last = glide.update(i * 9.0, 5.0, i * 1000L)
        assertTrue(last.keys.all { it <= maxOf(100.0, last.along) })
    }

    @Test
    fun afterAGapItIsWhereTheFixSaysAtOnce() {
        val glide = NavGlide(10_000.0, ACCEL)
        glide.update(100.0, 5.0, 0)
        assertEquals(1_500.0, glide.update(1_500.0, 5.0, 120_000).along, 0.1)
    }

    @Test
    fun keysAreEvenlySpacedOverTheAnimation() {
        val glide = NavGlide(10_000.0, ACCEL)
        for (i in 0..10) glide.update(i * 9.0, 5.0, i * 1000L)
        val last = glide.update(99.0, 5.0, 11_000)
        assertEquals(2_000L, last.ms)
        val steps = (listOf(last.along) + last.keys).zipWithNext { a, b -> b - a }
        steps.forEach { assertEquals(steps.first(), it, 1e-6) }
    }
}
