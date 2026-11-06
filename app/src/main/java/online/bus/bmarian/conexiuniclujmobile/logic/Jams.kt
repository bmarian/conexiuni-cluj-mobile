package online.bus.bmarian.conexiuniclujmobile.logic

import online.bus.bmarian.conexiuniclujmobile.data.Jam
import online.bus.bmarian.conexiuniclujmobile.data.Traffic
import kotlin.math.roundToInt

fun worstJam(traffic: Traffic?, stopIds: List<Int>, fromStopId: Int? = null): Jam? {
    val from = if (fromStopId == null) 0 else stopIds.indexOf(fromStopId)
    if (traffic == null || from < 0) return null
    return traffic.jams.filter { stopIds.indexOf(it.fromStopId) >= from }.maxByOrNull { it.delaySec }
}

fun jamMinutes(jam: Jam): Int = maxOf(1, (jam.delaySec / 60.0).roundToInt())
