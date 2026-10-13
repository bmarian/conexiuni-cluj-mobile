package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

@Serializable
data class NewsItem(val url: String = "", val date: String = "", val title: String = "")

object NewsApi {
    // The endpoint returns null when the scrape found nothing.
    suspend fun fetch(): List<NewsItem> = Api.getFresh<List<NewsItem>?>("news").orEmpty()
}

private val NEWS_DATE_FORMATS: List<DateTimeFormatter> = listOf(
    "d MMM yyyy" to Locale.ENGLISH,
    "d MMMM yyyy" to Locale.ENGLISH,
    "d MMM yyyy" to Locale.forLanguageTag("ro"),
    "d MMMM yyyy" to Locale.forLanguageTag("ro"),
    "d.M.yyyy" to Locale.ROOT,
).map { (pattern, locale) -> DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(locale) }

/** The date as CTP writes it ("9 Oct 2026", "09.10.2026", ISO); null when it can't be read. */
fun parseNewsDate(text: String): LocalDate? {
    val clean = text.trim()
    runCatching { return LocalDate.parse(clean.take(10)) }
    for (format in NEWS_DATE_FORMATS) runCatching { return LocalDate.parse(clean, format) }
    return null
}
