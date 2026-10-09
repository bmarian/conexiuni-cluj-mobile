package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor

/** Same request and rounding as the website's weather button. */
data class Weather(
    val temp: Int?,
    val apparentTemp: Int?,
    val humidity: Int?,
    val windSpeed: Int?,
    val code: Int?,
    val isDay: Boolean,
    val highTemp: Int?,
    val lowTemp: Int?,
    val precipitationChance: Int?,
    val sunrise: String?,
    val sunset: String?,
    val nextFetchMs: Long,
)

object WeatherApi {
    private const val URL =
        "https://api.open-meteo.com/v1/forecast?latitude=46.77&longitude=23.59&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset&timezone=Europe%2FBucharest&forecast_days=1"
    const val RETRY_MS = 10 * 60 * 1000L
    private val BUCHAREST: ZoneId = ZoneId.of("Europe/Bucharest")

    // JS Math.round: -2.5 becomes -2.
    private fun roundOrNull(value: Double?): Int? = value?.takeIf { it.isFinite() }?.let { floor(it + 0.5).toInt() }

    private fun JsonObject?.number(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonObject?.first(key: String): JsonPrimitive? = (this?.get(key) as? JsonArray)?.firstOrNull() as? JsonPrimitive

    suspend fun fetch(): Weather = withContext(Dispatchers.IO) {
        val body = Api.client.newCall(Request.Builder().url(URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)
            response.body?.string() ?: ""
        }
        val data = Api.json.parseToJsonElement(body).jsonObject
        val current = data["current"] as? JsonObject
        val daily = data["daily"] as? JsonObject
        val time = (current?.get("time") as? JsonPrimitive)?.contentOrNull
        val interval = (current?.get("interval") as? JsonPrimitive)?.intOrNull
        val now = System.currentTimeMillis()
        val next = if (time == null || interval == null || interval == 0) now + RETRY_MS else {
            val dataTime = runCatching { LocalDateTime.parse(time).atZone(BUCHAREST).toInstant().toEpochMilli() }.getOrNull()
            if (dataTime == null) now + RETRY_MS else now + maxOf(dataTime + interval * 1000L - now + 5_000, 30_000)
        }
        Weather(
            temp = roundOrNull(current.number("temperature_2m")),
            apparentTemp = roundOrNull(current.number("apparent_temperature")),
            humidity = roundOrNull(current.number("relative_humidity_2m")),
            windSpeed = roundOrNull(current.number("wind_speed_10m")),
            code = (current.number("weather_code") ?: current.number("weathercode"))?.toInt(),
            isDay = current.number("is_day") == 1.0,
            highTemp = roundOrNull(daily.first("temperature_2m_max")?.doubleOrNull),
            lowTemp = roundOrNull(daily.first("temperature_2m_min")?.doubleOrNull),
            precipitationChance = roundOrNull(daily.first("precipitation_probability_max")?.doubleOrNull),
            sunrise = daily.first("sunrise")?.contentOrNull,
            sunset = daily.first("sunset")?.contentOrNull,
            nextFetchMs = next,
        )
    }

    fun formatTime(value: String?): String {
        if (value == null) return "—"
        return runCatching { LocalDateTime.parse(value).let { "%02d:%02d".format(it.hour, it.minute) } }.getOrDefault("—")
    }

    val LABELS: Map<Int, Pair<String, String>> = mapOf(
        0 to ("Clear sky" to "Cer senin"),
        1 to ("Mostly clear" to "Mai mult senin"),
        2 to ("Partly cloudy" to "Parțial noros"),
        3 to ("Overcast" to "Noros"),
        45 to ("Fog" to "Ceață"),
        48 to ("Rime fog" to "Ceață cu depuneri"),
        51 to ("Light drizzle" to "Burniță slabă"),
        53 to ("Drizzle" to "Burniță"),
        55 to ("Dense drizzle" to "Burniță densă"),
        56 to ("Freezing drizzle" to "Burniță înghețată"),
        57 to ("Dense freezing drizzle" to "Burniță înghețată densă"),
        61 to ("Light rain" to "Ploaie slabă"),
        63 to ("Rain" to "Ploaie"),
        65 to ("Heavy rain" to "Ploaie puternică"),
        66 to ("Freezing rain" to "Ploaie înghețată"),
        67 to ("Heavy freezing rain" to "Ploaie înghețată puternică"),
        71 to ("Light snow" to "Ninsoare slabă"),
        73 to ("Snow" to "Ninsoare"),
        75 to ("Heavy snow" to "Ninsoare abundentă"),
        77 to ("Snow grains" to "Măzăriche"),
        80 to ("Light showers" to "Averse slabe"),
        81 to ("Showers" to "Averse"),
        82 to ("Heavy showers" to "Averse puternice"),
        85 to ("Snow showers" to "Averse de ninsoare"),
        86 to ("Heavy snow showers" to "Averse puternice de ninsoare"),
        95 to ("Thunderstorm" to "Furtună"),
        96 to ("Thunderstorm with hail" to "Furtună cu grindină"),
        99 to ("Strong thunderstorm with hail" to "Furtună puternică cu grindină"),
    )

    val ICONS: Map<Int, Pair<String, String>> = mapOf(
        0 to ("clear-day" to "clear-night"),
        1 to ("clear-day" to "clear-night"),
        2 to ("partly-cloudy-day" to "partly-cloudy-night"),
        3 to ("cloudy" to "overcast-night"),
        45 to ("fog-day" to "fog-night"),
        48 to ("fog-day" to "fog-night"),
        51 to ("partly-cloudy-day-drizzle" to "partly-cloudy-night-drizzle"),
        53 to ("partly-cloudy-day-drizzle" to "partly-cloudy-night-drizzle"),
        55 to ("drizzle" to "partly-cloudy-night-drizzle"),
        56 to ("partly-cloudy-day-sleet" to "partly-cloudy-night-sleet"),
        57 to ("sleet" to "partly-cloudy-night-sleet"),
        61 to ("partly-cloudy-day-rain" to "partly-cloudy-night-rain"),
        63 to ("rain" to "partly-cloudy-night-rain"),
        65 to ("rain" to "partly-cloudy-night-rain"),
        66 to ("partly-cloudy-day-sleet" to "partly-cloudy-night-sleet"),
        67 to ("sleet" to "partly-cloudy-night-sleet"),
        71 to ("partly-cloudy-day-snow" to "partly-cloudy-night-snow"),
        73 to ("snow" to "partly-cloudy-night-snow"),
        75 to ("snow" to "partly-cloudy-night-snow"),
        77 to ("snowflake" to "snowflake"),
        80 to ("partly-cloudy-day-rain" to "partly-cloudy-night-rain"),
        81 to ("rain" to "partly-cloudy-night-rain"),
        82 to ("rain" to "partly-cloudy-night-rain"),
        85 to ("partly-cloudy-day-snow" to "partly-cloudy-night-snow"),
        86 to ("snow" to "partly-cloudy-night-snow"),
        95 to ("thunderstorms-day-rain" to "thunderstorms-night-rain"),
        96 to ("thunderstorms-day-rain" to "thunderstorms-night-rain"),
        99 to ("thunderstorms-day-rain" to "thunderstorms-night-rain"),
    )

    fun slug(weather: Weather): String? {
        val code = weather.code ?: return null
        val pair = ICONS[code] ?: return "not-available"
        return if (weather.isDay) pair.first else pair.second
    }
}

@kotlinx.serialization.Serializable
data class NewsItem(val url: String = "", val date: String = "", val title: String = "")

object NewsApi {
    // The endpoint returns null when the scrape found nothing.
    suspend fun fetch(): List<NewsItem> = Api.getFresh<List<NewsItem>?>("news").orEmpty()
}
