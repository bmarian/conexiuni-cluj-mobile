package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Port of utils/destinationPages.ts.

@Serializable
data class DestinationSummary(val slug: String, val name: String, val district: Boolean = false, val stops: Int = 0, val lines: Int = 0)

@Serializable
data class Ride(
    @SerialName("route_id") val routeId: Int,
    val direction: Int = 0,
    val board: Int = 0,
    val alight: Int = 0,
    val minutes: List<Int> = emptyList(),
    @SerialName("every_min") val everyMin: Int = 0,
    val first: String = "",
    val last: String = "",
    @SerialName("day_type") val dayType: String = "",
)

@Serializable
data class RideOrigin(val slug: String, val name: String, val rides: List<Ride> = emptyList())

@Serializable
data class DestinationPage(
    val slug: String,
    val name: String,
    val district: Boolean = false,
    val lat: Double,
    val lon: Double,
    @SerialName("stop_ids") val stopIds: List<Int> = emptyList(),
    @SerialName("route_ids") val routeIds: List<Int> = emptyList(),
    val from: List<RideOrigin> = emptyList(),
)

fun rideMinutes(minutes: List<Int>): String {
    val fastest = minutes.firstOrNull() ?: return ""
    val slowest = minutes.lastOrNull() ?: fastest
    return if (fastest == slowest) "$fastest min" else "$fastest–$slowest min"
}
