package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// What /api/plan returns: finished options, timed on the server. Times are unix seconds.

const val WALK_SPEED = 80.0 // m/min

@Serializable
data class PlanRide(
    @SerialName("route_id") val routeId: Int,
    @SerialName("trip_id") val tripId: String,
    val depart: Long,
    val arrive: Long,
    val live: Boolean = false,
    @SerialName("vehicle_id") val vehicleId: Int = 0,
)

@Serializable
data class PlanRun(
    val depart: Long,
    val arrive: Long,
    val rides: List<PlanRide> = emptyList(),
)

@Serializable
data class PlanLeg(
    val kind: String,
    val from: Int = 0,
    val to: Int = 0,
    val passed: List<Int> = emptyList(),
    val meters: Double = 0.0,
    val seconds: Long = 0,
    val path: String = "",
    val lines: List<Int> = emptyList(),
) {
    val isRide get() = kind == "ride"
}

@Serializable
data class PlanOption(
    val key: String,
    val transfers: Int = 0,
    @SerialName("walk_m") val walkM: Double = 0.0,
    val legs: List<PlanLeg> = emptyList(),
    val runs: List<PlanRun> = emptyList(),
) {
    val rides get() = legs.filter { it.isRide }
    val firstRide get() = runs.firstOrNull()?.rides?.firstOrNull()
    val laterRides get() = runs.drop(1).mapNotNull { it.rides.firstOrNull() }
    val minutes get() = runs.firstOrNull()?.let { (it.arrive - it.depart) / 60.0 } ?: 0.0

    /** The trip each of a ride leg's lines runs on, from the timed runs. */
    fun legTrips(rideIndex: Int): Map<Int, String> {
        val trips = LinkedHashMap<Int, String>()
        for (run in runs) run.rides.getOrNull(rideIndex)?.let { trips.putIfAbsent(it.routeId, it.tripId) }
        return trips
    }
}

@Serializable
data class PlanPlace(val name: String = "", val lat: Double = 0.0, val lon: Double = 0.0)

@Serializable
data class PlanLine(
    @SerialName("short_name") val shortName: String = "",
    @SerialName("long_name") val longName: String = "",
    val color: String = "#64748b",
    val type: Int = 3,
)

@Serializable
data class PlanResponse(
    val options: List<PlanOption> = emptyList(),
    val stops: Map<String, PlanPlace> = emptyMap(),
    val lines: Map<String, PlanLine> = emptyMap(),
    /** Seconds until the timings change; 0 when they don't. */
    @SerialName("refresh_in") val refreshIn: Long = 0,
)

enum class TimeMode(val key: String) { Now("now"), Leave("leave"), Arrive("arrive"), Last("last") }

fun planQuery(from: Pair<Double, Double>, to: Pair<Double, Double>, mode: TimeMode, time: String, encode: (String) -> String): String {
    val params = StringBuilder("plan?from_lat=${toFixed4(from.first)}&from_lng=${toFixed4(from.second)}&to_lat=${toFixed4(to.first)}&to_lng=${toFixed4(to.second)}")
    when {
        mode == TimeMode.Last -> params.append("&last=true")
        mode != TimeMode.Now && time.isNotEmpty() -> params.append("&time=${encode(time)}&arrive_by=${mode == TimeMode.Arrive}")
    }
    return params.toString()
}

private fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), CLUJ_ZONE)

private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun formatLocalDateTime(ms: Long): String = local(ms).format(LOCAL_FORMAT)

private val PLANNER_DATE_TIME = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$""")

fun parsePlannerDateTime(value: String?): Long? {
    if (value.isNullOrEmpty()) return null
    val m = PLANNER_DATE_TIME.matchEntire(value) ?: return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    val (y, mo, d, hh, mm) = m.destructured
    val ss = m.groupValues[6].ifEmpty { "0" }
    return runCatching {
        LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), hh.toInt(), mm.toInt(), ss.toInt()).atZone(CLUJ_ZONE).toInstant().toEpochMilli()
    }.getOrNull()
}

/** JS toFixed(4). */
fun toFixed4(v: Double): String = BigDecimal(v).setScale(4, RoundingMode.HALF_UP).toPlainString()

fun clampCoord4(v: Double): Double = toFixed4(v).toDouble()

fun formatDuration(m: Double): String {
    val rounded = Math.round(m).toInt()
    if (rounded < 60) return "$rounded min"
    val h = Math.floorDiv(rounded, 60)
    val rem = rounded % 60
    return if (rem == 0) "$h h" else "$h h $rem min"
}

fun decodePolyline(encoded: String, precision: Int = 5): List<Pair<Double, Double>> {
    val factor = Math.pow(10.0, precision.toDouble())
    val coords = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0
        result = 0
        if (index >= encoded.length) break
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        coords += lat / factor to lng / factor
    }
    return coords
}
