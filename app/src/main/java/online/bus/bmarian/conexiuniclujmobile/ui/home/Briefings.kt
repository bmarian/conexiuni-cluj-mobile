package online.bus.bmarian.conexiuniclujmobile.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NewsApi
import online.bus.bmarian.conexiuniclujmobile.data.NewsItem
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.Weather
import online.bus.bmarian.conexiuniclujmobile.data.WeatherApi
import online.bus.bmarian.conexiuniclujmobile.logic.changeDirection
import online.bus.bmarian.conexiuniclujmobile.logic.changeKindLabels
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import online.bus.bmarian.conexiuniclujmobile.logic.formatChangeDate
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader

/** Today's weather in Cluj, fetched again when the forecast says it changes. */
object WeatherFeed {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    /** Keeps it fresh while collected (the app on screen and the weather switched on). */
    suspend fun run() {
        while (true) {
            val wait = try {
                val fresh = WeatherApi.fetch()
                _weather.value = fresh
                fresh.nextFetchMs - System.currentTimeMillis()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                WeatherApi.RETRY_MS
            }
            delay(wait.coerceAtLeast(30_000))
        }
    }
}

/** CTP's news, read once a session like the website's popover. */
object NewsFeed {
    val items = MutableStateFlow<List<NewsItem>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow(false)
    private var loaded = false

    suspend fun load() {
        if (loaded) return
        try {
            items.value = NewsApi.fetch()
            error.value = false
            loaded = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error.value = true
        } finally {
            loading.value = false
        }
    }
}

/** The Material icon and tint standing in for the website's Meteocons slug. */
fun weatherIcon(slug: String?): Pair<ImageVector, Color> = when {
    slug == null || slug == "not-available" -> Icons.Rounded.HelpOutline to Color(0xFF94A3B8)
    slug.startsWith("thunderstorms") -> Icons.Rounded.Thunderstorm to Color(0xFF7C3AED)
    slug.contains("snow") || slug == "snowflake" || slug.contains("sleet") -> Icons.Rounded.AcUnit to Color(0xFF38BDF8)
    slug.contains("rain") -> Icons.Rounded.Umbrella to Color(0xFF3B82F6)
    slug.contains("drizzle") -> Icons.Rounded.Grain to Color(0xFF60A5FA)
    slug.startsWith("fog") -> Icons.Rounded.Dehaze to Color(0xFF94A3B8)
    slug == "clear-day" -> Icons.Rounded.WbSunny to Color(0xFFF59E0B)
    slug == "clear-night" -> Icons.Rounded.NightsStay to Color(0xFF818CF8)
    slug.startsWith("partly-cloudy") -> Icons.Rounded.FilterDrama to Color(0xFFF59E0B)
    else -> Icons.Rounded.Cloud to Color(0xFF94A3B8)
}

/** Whether there are line changes you haven't seen: the dot on the News chip. */
@Composable
fun rememberNewsDot(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val showChanges by Services.settings.showTimetableChanges.collectAsStateWithLifecycle()
    val changes by Services.routeUpdates.changes.collectAsStateWithLifecycle()
    val seen by Services.routeUpdates.seen.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    LaunchedEffect(showChanges, followed) { if (showChanges) Services.routeUpdates.fetchChanges() }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (Services.settings.showTimetableChanges.value) Services.routeUpdates.refreshIfStale()
        }
    }
    return showChanges && Services.routeUpdates.unseenIds(changes, seen).isNotEmpty()
}

/** The weather page in the sheet: what the website's weather popover shows. */
@Composable
fun WeatherPage() {
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    val weather by WeatherFeed.weather.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.w_weather), listState.pastHeader())
        val current = weather
        if (current == null) {
            Text(stringResource(R.string.w_news_loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val (icon, tint) = weatherIcon(WeatherApi.slug(current))
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icon, null, Modifier.size(56.dp), tint = tint)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.w_weather_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val label = current.code?.let { WeatherApi.LABELS[it] }?.let { if (locale == "en") it.first else it.second }
                        Text(label ?: stringResource(R.string.w_weather_unknown), style = MaterialTheme.typography.titleLarge)
                    }
                    Text("${current.temp ?: "—"}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${stringResource(R.string.w_weather_high)} ${current.highTemp ?: "—"}°", style = MaterialTheme.typography.bodyLarge)
                    Text("${stringResource(R.string.w_weather_low)} ${current.lowTemp ?: "—"}°", style = MaterialTheme.typography.bodyLarge)
                }
            }
            val stats = listOf(
                R.string.w_weather_feels_like to "${current.apparentTemp ?: "—"}°",
                R.string.w_weather_humidity to "${current.humidity ?: "—"}%",
                R.string.w_weather_wind to "${current.windSpeed ?: "—"} km/h",
                R.string.w_weather_rain to "${current.precipitationChance ?: "—"}%",
            )
            items(stats.chunked(2)) { row ->
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, value) in row) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                        ) {
                            Text(stringResource(label).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stringResource(R.string.w_weather_sunrise)} ${WeatherApi.formatTime(current.sunrise)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${stringResource(R.string.w_weather_sunset)} ${WeatherApi.formatTime(current.sunset)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The news page in the sheet: changes to the lines you follow, then CTP's own news. */
@Composable
fun NewsPage() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val showChanges by Services.settings.showTimetableChanges.collectAsStateWithLifecycle()
    val changes by Services.routeUpdates.changes.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    val loadingChanges by Services.routeUpdates.loading.collectAsStateWithLifecycle()
    val changesError by Services.routeUpdates.error.collectAsStateWithLifecycle()
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    val news by NewsFeed.items.collectAsStateWithLifecycle()
    val loading by NewsFeed.loading.collectAsStateWithLifecycle()
    val error by NewsFeed.error.collectAsStateWithLifecycle()
    // Kept while the page is open, so the "new" marks don't vanish at once.
    var freshIds by remember { mutableStateOf(emptySet<Long>()) }
    val hasUpdates = rememberNewsDot()

    LaunchedEffect(Unit) { NewsFeed.load() }
    LaunchedEffect(changes) {
        if (!hasUpdates) return@LaunchedEffect
        freshIds = freshIds + Services.routeUpdates.unseenIds()
        Services.routeUpdates.markAllSeen()
    }

    fun openChange(change: RouteChange) {
        if (change.routeId == 0) return
        nav.route(change.routeId, changeDirection(change), change = change.id)
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.w_news), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            if (showChanges) {
                item { NewsSection(stringResource(R.string.w_news_your_lines), stringResource(R.string.w_news_source_ours)) }
                when {
                    followed.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_follow_hint)) }
                    loadingChanges && changes.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_loading)) }
                    changesError && changes.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_error), MaterialTheme.colorScheme.error) }
                    changes.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_no_changes)) }
                    else -> items(changes, key = { "change-${it.id}" }) { change ->
                        val isNew = change.id in freshIds
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = change.routeId != 0) { openChange(change) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LineBadge(change.routeShortName, change.routeColor.ifEmpty { "#64748b" }, height = 28.dp, minWidth = 36.dp, corner = 6.dp)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(formatChangeDate(change.detectedAt, locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (isNew) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEAB308)))
                                }
                                Text(
                                    changeTitle(change, context),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isNew) FontWeight.ExtraBold else FontWeight.SemiBold,
                                )
                                Text(
                                    changeKindLabels(change, context).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { NewsSection(stringResource(R.string.w_news_ctp), "ctpcj.ro") }
            when {
                loading && news.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_loading)) }
                error && news.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_error), MaterialTheme.colorScheme.error) }
                news.isEmpty() -> item { NewsHint(stringResource(R.string.w_news_empty)) }
                else -> items(news, key = { "news-${it.url}" }) { item ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, item.url.toUri())) } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(item.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                capitalizeWords(item.title),
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp).padding(top = 3.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// CSS text-transform: capitalize: the first letter of every word upper-cased.
private fun capitalizeWords(text: String): String = text.split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun NewsSection(title: String, source: String) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp))
        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NewsHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = color)
}
