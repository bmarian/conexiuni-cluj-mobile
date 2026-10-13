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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import kotlinx.coroutines.flow.MutableStateFlow
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NewsApi
import online.bus.bmarian.conexiuniclujmobile.data.NewsItem
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.data.parseNewsDate
import online.bus.bmarian.conexiuniclujmobile.logic.changeDirection
import online.bus.bmarian.conexiuniclujmobile.logic.changeKindLabels
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import online.bus.bmarian.conexiuniclujmobile.logic.formatChangeDate
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import java.time.ZoneId

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

@Composable
fun rememberNewsDot(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val changes by Services.routeUpdates.changes.collectAsStateWithLifecycle()
    val seen by Services.routeUpdates.seen.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    LaunchedEffect(followed) { Services.routeUpdates.fetchChanges() }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { Services.routeUpdates.refreshIfStale() }
    }
    return Services.routeUpdates.unseenIds(changes, seen).isNotEmpty()
}

private sealed class NewsEntry(val at: Long, val key: String) {
    class Change(val change: RouteChange) : NewsEntry(change.detectedAt, "change-${change.id}")
    class Ctp(val item: NewsItem, at: Long) : NewsEntry(at, "news-${item.url}")
}

@Composable
fun NewsPage() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val changes by Services.routeUpdates.changes.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    val loadingChanges by Services.routeUpdates.loading.collectAsStateWithLifecycle()
    val changesError by Services.routeUpdates.error.collectAsStateWithLifecycle()
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    val news by NewsFeed.items.collectAsStateWithLifecycle()
    val loading by NewsFeed.loading.collectAsStateWithLifecycle()
    val error by NewsFeed.error.collectAsStateWithLifecycle()
    var freshIds by remember { mutableStateOf(emptySet<Long>()) }
    val hasUpdates = rememberNewsDot()

    LaunchedEffect(Unit) { NewsFeed.load() }
    LaunchedEffect(changes) {
        if (!hasUpdates) return@LaunchedEffect
        freshIds = freshIds + Services.routeUpdates.unseenIds()
        Services.routeUpdates.markAllSeen()
    }

    val entries = remember(changes, news) {
        val zone = ZoneId.systemDefault()
        val ctp = news.map { item -> NewsEntry.Ctp(item, parseNewsDate(item.date)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L) }
        (changes.map { NewsEntry.Change(it) } + ctp).sortedByDescending { it.at }
    }

    fun openChange(change: RouteChange) {
        if (change.routeId == 0) return
        nav.route(change.routeId, changeDirection(change), change = change.id)
    }

    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        SheetTopBar(stringResource(R.string.w_news), listState.pastHeader())
        LazyColumn(Modifier.weight(1f), state = listState) {
            when {
                entries.isNotEmpty() -> items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is NewsEntry.Change -> ChangeRow(entry.change, entry.change.id in freshIds, locale) { openChange(entry.change) }
                        is NewsEntry.Ctp -> NewsRow(entry.item, entry.at.takeIf { it > 0 }?.let { formatChangeDate(it, locale) } ?: entry.item.date) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, entry.item.url.toUri())) }
                        }
                    }
                }
                loading || loadingChanges -> item { NewsHint(stringResource(R.string.w_news_loading)) }
                error || changesError -> item { NewsHint(stringResource(R.string.w_news_error), MaterialTheme.colorScheme.error) }
                else -> item { NewsHint(stringResource(R.string.w_news_empty)) }
            }
            if (followed.isEmpty()) item { NewsHint(stringResource(R.string.w_news_follow_hint)) }
        }
    }
}

@Composable
private fun ChangeRow(change: RouteChange, isNew: Boolean, locale: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().squishClickable(enabled = change.routeId != 0, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
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

@Composable
private fun NewsRow(item: NewsItem, date: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("$date · CTP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// CSS text-transform: capitalize.
private fun capitalizeWords(text: String): String = text.split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun NewsHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = color)
}
