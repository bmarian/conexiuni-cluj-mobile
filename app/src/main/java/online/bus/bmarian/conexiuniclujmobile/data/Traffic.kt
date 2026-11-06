package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrafficStretch(val path: String, val band: Int)

@Serializable
data class Jam(
    @SerialName("from_stop_id") val fromStopId: Int,
    @SerialName("to_stop_id") val toStopId: Int,
    val band: Int,
    @SerialName("delay_sec") val delaySec: Int,
)

@Serializable
data class Traffic(val stretches: List<TrafficStretch> = emptyList(), val jams: List<Jam> = emptyList())

object LiveTraffic {
    private val streams = HashMap<Any, Map<String, Traffic>>()
    private val merged = MutableStateFlow<Map<String, Traffic>>(emptyMap())
    val all: StateFlow<Map<String, Traffic>> = merged.asStateFlow()

    @Synchronized
    fun publish(stream: Any, traffic: Map<String, Traffic>?) {
        if (traffic != null) streams[stream] = traffic else streams.remove(stream)
        merged.value = streams.values.fold(emptyMap()) { all, one -> all + one }
    }
}
