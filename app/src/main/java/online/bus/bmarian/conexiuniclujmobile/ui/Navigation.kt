package online.bus.bmarian.conexiuniclujmobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.R

interface Navigator {
    val canGoBack: Boolean
    fun back()
    fun home()
    fun stop(stopId: Int)
    fun route(routeId: Int, direction: String, fromStopId: Int? = null, change: Long? = null)
    fun plan(query: PlanQuery)
    fun settings()
    fun weather()
    fun news()
}

/** Same keys as the website's /plan query. */
data class PlanQuery(
    val lat: String? = null,
    val lon: String? = null,
    val name: String? = null,
    val originLat: String? = null,
    val originLon: String? = null,
    val originName: String? = null,
    val time: String? = null,
    val arriveBy: String? = null,
    val last: String? = null,
    val plan: String? = null,
) {
    private fun pairs() = listOf(
        "lat" to lat, "lon" to lon, "name" to name,
        "originLat" to originLat, "originLon" to originLon, "originName" to originName,
        "time" to time, "arrive_by" to arriveBy, "last" to last, "plan" to plan,
    ).filter { it.second != null }

    fun encode(): String = pairs().joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }

    companion object {
        fun decode(query: String): PlanQuery = fromUri(Uri.parse("x://plan?$query"))

        fun fromUri(uri: Uri) = PlanQuery(
            lat = uri.getQueryParameter("lat"),
            lon = uri.getQueryParameter("lon"),
            name = uri.getQueryParameter("name"),
            originLat = uri.getQueryParameter("originLat"),
            originLon = uri.getQueryParameter("originLon"),
            originName = uri.getQueryParameter("originName"),
            time = uri.getQueryParameter("time"),
            arriveBy = uri.getQueryParameter("arrive_by"),
            last = uri.getQueryParameter("last"),
            plan = uri.getQueryParameter("plan"),
        )

        fun to(lat: Double, lon: Double, name: String, originLat: Double? = null, originLon: Double? = null, originName: String? = null) =
            PlanQuery(
                lat = jsNumber(lat), lon = jsNumber(lon), name = name,
                originLat = originLat?.let(::jsNumber), originLon = originLon?.let(::jsNumber), originName = originName,
            )

        // JS String(number): no trailing ".0".
        fun jsNumber(value: Double): String =
            if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
    }
}

val LocalNavigator = compositionLocalOf<Navigator> { error("no navigator") }

class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SheetTopBar(
    title: String?,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    menu: List<MenuAction> = emptyList(),
) {
    val nav = LocalNavigator.current
    Row(
        modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nav.canGoBack) {
            IconButton(onClick = nav::back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.w_back))
            }
        }
        val titleAlpha by animateFloatAsState(if (showTitle && title != null) 1f else 0f, label = "title")
        Text(
            title.orEmpty(),
            Modifier.weight(1f).padding(horizontal = 8.dp).graphicsLayer { alpha = titleAlpha },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (action in menu) {
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, null) },
                            onClick = {
                                open = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = nav::home) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_home))
        }
    }
}

@Composable
fun LazyListState.pastHeader(): Boolean {
    val past by remember(this) { derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 120 } }
    return past
}

@Composable
fun ShareButton(path: String) {
    val context = LocalContext.current
    IconButton(onClick = { share(context, path) }) {
        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.w_share_url))
    }
}

fun share(context: Context, path: String) {
    val url = BuildConfig.SITE_ORIGIN + path
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}

class ChipAction(
    val label: String,
    val icon: ImageVector,
    val primary: Boolean = false,
    val selected: Boolean = false,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

@Composable
fun ActionChips(actions: List<ChipAction>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(actions, key = { it.label }) { action ->
            val scheme = MaterialTheme.colorScheme
            val container = when {
                action.primary -> scheme.primary
                action.selected -> scheme.secondaryContainer
                else -> Color.Transparent
            }
            val content = when {
                action.primary -> scheme.onPrimary
                action.selected -> scheme.onSecondaryContainer
                else -> scheme.onSurface
            }
            Surface(
                onClick = action.onClick,
                shape = CircleShape,
                color = container,
                contentColor = content,
                border = if (action.primary || action.selected) null else BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.animateItem(),
            ) {
                Row(
                    Modifier.height(36.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(action.icon, null, Modifier.size(18.dp), tint = if (action.tint == Color.Unspecified) content else action.tint)
                    Text(action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
