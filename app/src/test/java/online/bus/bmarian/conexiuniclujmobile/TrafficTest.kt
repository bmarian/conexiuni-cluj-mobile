package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.data.Jam
import online.bus.bmarian.conexiuniclujmobile.data.LiveMerge
import online.bus.bmarian.conexiuniclujmobile.data.Traffic
import online.bus.bmarian.conexiuniclujmobile.data.TrafficStretch
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.jamMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.railParts
import online.bus.bmarian.conexiuniclujmobile.logic.trafficRuns
import online.bus.bmarian.conexiuniclujmobile.logic.worstJam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Port of utils/__tests__/traffic.spec.ts.
class TrafficTest {
    // The backend's polyline encoding, six decimals.
    private fun encode(points: List<LatLon>): String {
        val out = StringBuilder()
        val prev = longArrayOf(0, 0)
        for (point in points) {
            listOf(point.lat, point.lon).forEachIndexed { i, coord ->
                val value = (coord * 1e6).roundToLong()
                var shifted = (value - prev[i]) shl 1
                if (value - prev[i] < 0) shifted = shifted.inv()
                while (shifted >= 0x20) {
                    out.append(((0x20L or (shifted and 0x1f)) + 63).toInt().toChar())
                    shifted = shifted shr 5
                }
                out.append((shifted + 63).toInt().toChar())
                prev[i] = value
            }
        }
        return out.toString()
    }

    private val line = listOf(23.59, 23.6, 23.61, 23.62, 23.63).map { LatLon(46.77, it) }
    private fun leg(from: Int, to: Int) = encode(line.subList(from, to + 1))

    @Test
    fun railsRunAlongEachStretchShareOfTheLine() {
        val runs = trafficRuns(listOf(TrafficStretch(leg(0, 1), 2), TrafficStretch(leg(2, 3), 3)), line)
        assertEquals(listOf(0 to 25, 50 to 75), runs.map { (it.from * 100).roundToInt() to (it.to * 100).roundToInt() })
        assertEquals(listOf(2, 3), runs.map { it.band })
    }

    @Test
    fun railsGrowWithTheLine() {
        val runs = trafficRuns(listOf(TrafficStretch(leg(0, 1), 2), TrafficStretch(leg(2, 3), 3)), line)
        val parts = railParts(line, runs, done = 0.6f)
        assertEquals(listOf(2, 3), parts.map { it.second })
        assertEquals(23.614, parts[1].first.last().lon, 0.001)
        assertTrue(railParts(line, runs, done = 0f).isEmpty())
    }

    private fun jam(from: Int, delay: Int) = Jam(from, from + 1, 2, delay)
    private val traffic = Traffic(jams = listOf(jam(1, 90), jam(3, 200), jam(5, 120)))

    @Test
    fun theWorstJamIsTheOneCostingTheMostOnTheTrip() {
        assertEquals(3, worstJam(traffic, listOf(1, 2, 3, 4, 5, 6))?.fromStopId)
    }

    @Test
    fun onlyJamsFromTheGivenStopOnCount() {
        assertEquals(5, worstJam(traffic, listOf(1, 2, 3, 4, 5, 6), 4)?.fromStopId)
        assertNull(worstJam(traffic, listOf(1, 2, 3, 4, 5, 6), 6))
        assertNull(worstJam(traffic, listOf(1, 2, 3), 9))
        assertNull(worstJam(null, listOf(1, 2, 3)))
    }

    @Test
    fun aJamCostsAtLeastAMinute() {
        assertEquals(1, jamMinutes(jam(1, 20)))
        assertEquals(2, jamMinutes(jam(1, 90)))
        assertEquals(3, jamMinutes(jam(1, 200)))
    }

    @Test
    fun deltasReplaceTheTrafficOnlyWhenTheyCarryIt() {
        val merge = LiveMerge()
        val jams = """{"stretches":[{"path":"x","band":2}],"jams":[{"from_stop_id":1,"to_stop_id":2,"band":2,"delay_sec":130}]}"""
        val first = merge.apply("snapshot", """{"as_of":1,"rows":[],"vehicles":[],"traffic":{"2_0":$jams}}""")!!
        assertEquals(130, first.traffic["2_0"]?.jams?.single()?.delaySec)
        val kept = merge.apply("delta", """{"as_of":2,"rows":[],"vehicles":{"upsert":[],"remove":[]}}""")!!
        assertEquals(first.traffic, kept.traffic)
        val cleared = merge.apply("delta", """{"as_of":3,"rows":[],"vehicles":{"upsert":[],"remove":[]},"traffic":{}}""")!!
        assertTrue(cleared.traffic.isEmpty())
        assertTrue(merge.apply("snapshot", """{"as_of":4}""")!!.traffic.isEmpty())
    }
}
