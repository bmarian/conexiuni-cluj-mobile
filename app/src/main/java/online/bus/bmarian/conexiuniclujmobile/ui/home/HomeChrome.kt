package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.ui.Bouncy
import online.bus.bmarian.conexiuniclujmobile.ui.LocalNavigator
import online.bus.bmarian.conexiuniclujmobile.ui.map.MapController
import online.bus.bmarian.conexiuniclujmobile.ui.squish

@Composable
fun HomeChrome(query: String, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val online by Services.online.collectAsStateWithLifecycle()
    val showNews by Services.settings.showNews.collectAsStateWithLifecycle()
    val places by Services.favorites.plans.collectAsStateWithLifecycle()
    Column(modifier) {
        Surface(
            onClick = onSearch,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val kept = query.isNotBlank()
                Text(
                    if (kept) query else stringResource(R.string.w_search_placeholder),
                    Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (kept) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = nav::settings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.w_settings))
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showNews && online) {
                item(key = "news") {
                    MapChip(Icons.Rounded.Newspaper, stringResource(R.string.w_news), dot = rememberNewsDot(), onClick = nav::news)
                }
            }
            if (online) {
                items(places, key = { "place-${it.lat}-${it.lon}-${it.originLat}" }) { place ->
                    MapChip(Icons.Rounded.Place, place.label ?: place.name, tint = MaterialTheme.colorScheme.tertiary) { nav.plan(planQueryFor(place)) }
                }
            }
        }
    }
}

@Composable
private fun MapChip(icon: ImageVector, label: String, tint: Color = Color.Unspecified, dot: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.height(36.dp).padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Icon(icon, null, Modifier.size(18.dp), tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
                if (dot) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Color(0xFFEAB308))
                            .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MapFabs(map: MapController, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
        AnimatedVisibility(map.shapes.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            val press = remember { MutableInteractionSource() }
            SmallFloatingActionButton(
                onClick = map::fitShapes,
                modifier = Modifier.squish(press, 0.85f),
                interactionSource = press,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
            ) { Icon(Icons.Rounded.CropFree, contentDescription = stringResource(R.string.w_fit_routes)) }
        }
        val press = remember { MutableInteractionSource() }
        var spins by remember { mutableIntStateOf(0) }
        val spin by animateFloatAsState(spins * 90f, Bouncy.pop, label = "spin")
        FloatingActionButton(
            onClick = {
                spins++
                map.centerOnUser()
            },
            modifier = Modifier.squish(press, 0.85f),
            interactionSource = press,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        ) { Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.w_center), Modifier.rotate(spin)) }
    }
}
