package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Same shape as backend/search/answer.go.

@Serializable
data class SearchStop(val id: Int, val rank: Int)

@Serializable
data class SearchBus(
    val label: String,
    @SerialName("route_id") val routeId: Int,
    @SerialName("trip_id") val tripId: String,
    val lat: Double,
    val lon: Double,
)

@Serializable
data class SearchOtherLine(
    val city: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("route_id") val routeId: Int,
    val name: String,
    @SerialName("long_name") val longName: String,
    val color: String = "",
    val slug: String? = null,
)

@Serializable
data class SearchCity(val slug: String, val name: String)

@Serializable
data class SearchEnd(val name: String, val lat: Double, val lon: Double, @SerialName("stop_id") val stopId: Int? = null)

// No from: where the rider is.
@Serializable
data class SearchTrip(val from: SearchEnd? = null, val to: SearchEnd)

@Serializable
data class SearchFar(
    val name: String,
    val town: Boolean = false,
    @SerialName("route_id") val routeId: Int? = null,
    @SerialName("stop_id") val stopId: Int,
    val meters: Int,
)

@Serializable
data class SearchNotice(val kind: String, val name: String? = null, val operator: String? = null, val url: String? = null)

// Best names the list whose first item is the top result.
@Serializable
data class SearchAnswer(
    val best: String? = null,
    val show: String? = null,
    val lines: List<Int> = emptyList(),
    val stops: List<SearchStop> = emptyList(),
    @SerialName("places") val rawPlaces: List<JsonElement> = emptyList(),
    val buses: List<SearchBus> = emptyList(),
    val other: List<SearchOtherLine> = emptyList(),
    val cities: List<SearchCity> = emptyList(),
    val trip: SearchTrip? = null,
    val far: SearchFar? = null,
    val notice: SearchNotice? = null,
) {
    val places: List<Place> by lazy { rawPlaces.mapNotNull(Places::place) }

    companion object {
        const val BEST_LINE = "line"
        const val BEST_STOP = "stop"
        const val BEST_PLACE = "place"
        const val BEST_BUS = "bus"
        const val BEST_OTHER = "other"
        const val BEST_CITY = "city"
        const val BEST_TRIP = "trip"
        const val BEST_FAR = "far"
        const val SHOW_TIMETABLE = "timetable"
        const val NOTICE_CITY_MISSING = "city_missing"
        const val NOTICE_BUS_OFF = "bus_off"
        const val NOTICE_TICKETS = "tickets"
    }
}

object Search {
    private const val CACHED = 64

    private val cache = RecentCache<SearchAnswer>(CACHED)

    suspend fun find(query: String): SearchAnswer? {
        val path = "search?q=${Api.encode(query)}"
        val key = Api.url(path)
        cache[key]?.let { return it }
        return try {
            Api.get<SearchAnswer>(path).also { cache[key] = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
