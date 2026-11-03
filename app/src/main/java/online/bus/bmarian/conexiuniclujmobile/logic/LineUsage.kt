package online.bus.bmarian.conexiuniclujmobile.logic

import java.time.DayOfWeek
import java.time.Instant
import kotlinx.serialization.Serializable

// Port of utils/lineUsage.ts.

@Serializable
data class Usage(val counts: Map<String, Int> = emptyMap(), val last: Long = 0) {
    val total get() = counts.values.sum()
}

fun usageSlot(atMs: Long): String {
    val time = Instant.ofEpochMilli(atMs).atZone(CLUJ_ZONE)
    val part = when (time.hour) {
        in 0 until 5 -> "night"
        in 5 until 12 -> "morning"
        in 12 until 18 -> "afternoon"
        else -> "evening"
    }
    val weekend = time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY
    return "$part/${if (weekend) "weekend" else "weekday"}"
}

fun Map<String, Usage>.lineUse(routeId: Int): Usage? = listOfNotNull(this["$routeId/0"], this["$routeId/1"]).reduceOrNull { a, b ->
    a.copy(counts = (a.counts.keys + b.counts.keys).associateWith { (a.counts[it] ?: 0) + (b.counts[it] ?: 0) }, last = maxOf(a.last, b.last))
}

fun Usage?.counted(atMs: Long): Usage {
    val usage = this ?: Usage()
    val slot = usageSlot(atMs)
    return usage.copy(counts = usage.counts + (slot to (usage.counts[slot] ?: 0) + 1), last = atMs)
}

fun usageOrder(nowMs: Long): Comparator<Usage?> {
    val slot = usageSlot(nowMs)
    return compareByDescending<Usage?> { it?.counts?.get(slot) ?: 0 }.thenByDescending { it?.total ?: 0 }.thenByDescending { it?.last ?: 0 }
}
