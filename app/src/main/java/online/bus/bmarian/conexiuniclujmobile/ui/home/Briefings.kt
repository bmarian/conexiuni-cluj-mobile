package online.bus.bmarian.conexiuniclujmobile.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Newspaper
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.NewsItem
import online.bus.bmarian.conexiuniclujmobile.data.RouteChange
import online.bus.bmarian.conexiuniclujmobile.logic.changeDirection
import online.bus.bmarian.conexiuniclujmobile.logic.changeKindLabels
import online.bus.bmarian.conexiuniclujmobile.logic.changeTitle
import online.bus.bmarian.conexiuniclujmobile.logic.formatDate
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.pastHeader
import online.bus.bmarian.conexiuniclujmobile.ui.squishClickable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun rememberNewsDot(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val updates by Services.routeUpdates.updates.collectAsStateWithLifecycle()
    val seen by Services.routeUpdates.seen.collectAsStateWithLifecycle()
    val followed by Services.routeUpdates.followed.collectAsStateWithLifecycle()
    val city by Cities.current.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    LaunchedEffect(followed, city, cities) { Services.routeUpdates.fetchUpdates() }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { Services.routeUpdates.refreshIfStale() }
    }
    return Services.routeUpdates.unseenIds(updates, seen).isNotEmpty()
}

private sealed class NewsEntry(val date: String, val key: String) {
    class Change(val change: RouteChange, date: String) : NewsEntry(date, "change-${change.id}")
    class News(val item: NewsItem, date: String) : NewsEntry(date, "news-${item.url}")
}

private fun dayLabel(day: LocalDate, today: LocalDate, locale: String, context: Context): String = when (day) {
    today -> context.getString(R.string.w_news_today)
    today.minusDays(1) -> context.getString(R.string.w_news_yesterday)
    else -> formatDate(day, locale, withYear = day.year != today.year)
}

@Composable
fun NewsPage() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val updates by Services.routeUpdates.updates.collectAsStateWithLifecycle()
    val loading by Services.routeUpdates.loading.collectAsStateWithLifecycle()
    val error by Services.routeUpdates.error.collectAsStateWithLifecycle()
    val locale by Services.settings.locale.collectAsStateWithLifecycle()
    var freshIds by remember { mutableStateOf(emptySet<Long>()) }
    val hasUpdates = rememberNewsDot()
    val today = LocalDate.now()

    LaunchedEffect(updates) {
        if (!hasUpdates) return@LaunchedEffect
        freshIds = freshIds + Services.routeUpdates.unseenIds()
        Services.routeUpdates.markAllSeen()
    }

    // Newest first, as the server sends them.
    val entries = remember(updates, today, locale) {
        val zone = ZoneId.systemDefault()
        updates.mapNotNull { update ->
            val date = dayLabel(Instant.ofEpochMilli(update.at).atZone(zone).toLocalDate(), today, locale, context)
            update.change?.let { NewsEntry.Change(it, date) } ?: update.news?.let { NewsEntry.News(it, date) }
        }
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
                        is NewsEntry.Change -> ChangeRow(entry.change, entry.date, entry.change.id in freshIds) { openChange(entry.change) }
                        is NewsEntry.News -> NewsRow(entry.item, entry.date) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, entry.item.url.toUri())) }
                        }
                    }
                }
                loading -> item { NewsHint(stringResource(R.string.w_news_loading)) }
                error -> item { NewsHint(stringResource(R.string.w_news_error), MaterialTheme.colorScheme.error) }
                else -> item { NewsHint(stringResource(R.string.w_news_empty)) }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: RouteChange, date: String, isNew: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    FeedRow(
        title = changeTitle(change, context),
        meta = (listOf(date) + changeKindLabels(change, context)).joinToString(" · "),
        isNew = isNew,
        enabled = change.routeId != 0,
        onClick = onClick,
    ) {
        LineBadge(change.routeShortName, change.routeColor.ifEmpty { "#64748b" }, height = 28.dp, minWidth = 36.dp, corner = 6.dp)
    }
}

@Composable
private fun NewsRow(item: NewsItem, date: String, onClick: () -> Unit) {
    FeedRow(
        title = item.title,
        meta = "$date · ${item.url.toUri().host.orEmpty().removePrefix("www.")}",
        external = true,
        onClick = onClick,
    ) {
        Icon(Icons.Rounded.Newspaper, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedRow(
    title: String,
    meta: String,
    isNew: Boolean = false,
    external: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    lead: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().squishClickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.widthIn(min = 40.dp).heightIn(min = 28.dp), contentAlignment = Alignment.Center) { lead() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNew) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEAB308)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (external) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NewsHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = color)
}
