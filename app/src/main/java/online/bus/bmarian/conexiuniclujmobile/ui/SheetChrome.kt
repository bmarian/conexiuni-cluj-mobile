package online.bus.bmarian.conexiuniclujmobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Links

interface Navigator {
    val canGoBack: Boolean
    fun back()
    fun home()
    fun stop(stopId: Int)
    /** Stops picked one after another on the map replace each other on the stack. */
    fun mapStop(stopId: Int)
    fun mapStops(stopIds: List<Int>)
    fun route(routeId: Int, direction: String, fromStopId: Int? = null, change: Long? = null)
    fun plan(query: PlanQuery)
    fun settings()
    fun news()
    fun destinations()
    fun destination(slug: String)
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
        fun decode(query: String): PlanQuery = fromUri("x://plan?$query".toUri())

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

fun share(context: Context, path: String) {
    val url = Links.web(path)
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
        // Keyed by place: a chip whose label changes (Save → Saved) stays put and resizes.
        itemsIndexed(actions, key = { i, _ -> i }) { _, action ->
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
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val corner by animateDpAsState(
                when {
                    pressed -> 8.dp
                    action.selected -> 12.dp
                    else -> 18.dp
                },
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "chip",
            )
            Surface(
                onClick = action.onClick,
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = content,
                border = if (action.primary || action.selected) null else BorderStroke(1.dp, scheme.outlineVariant),
                interactionSource = press,
                modifier = Modifier.animateItem().squish(press, 0.92f).animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                Row(
                    Modifier.height(36.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PopIcon(action.icon, action.selected, Modifier.size(18.dp), tint = if (action.tint == Color.Unspecified) content else action.tint)
                    Text(action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
