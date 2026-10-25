package online.bus.bmarian.conexiuniclujmobile.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.ui.home.SearchField
import online.bus.bmarian.conexiuniclujmobile.ui.home.norm
import online.bus.bmarian.conexiuniclujmobile.ui.stop.LiveBadge
import online.bus.bmarian.conexiuniclujmobile.ui.stop.TimetableBadge

private const val CITY_ROWS_SHOWN = 5

@Composable
fun CityChooser() {
    val context = LocalContext.current
    val city by Cities.current.collectAsStateWithLifecycle()
    val choice by Cities.choice.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    val located = remember { UserLocation.permitted(context) }
    val automatic = located && choice == null
    val info = cities.firstOrNull { it.slug == city }
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var rowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val folded = norm(query.trim())
    val shown = if (folded.isEmpty()) cities else cities.filter { norm(it.name).contains(folded) || norm(it.operator).contains(folded) }
    fun choose(slug: String?) {
        open = false
        query = ""
        Services.chooseCity(slug)
    }
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable {
            open = !open
            if (!open) query = ""
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Rounded.Place, null) },
        headlineContent = { Text(if (automatic) stringResource(R.string.w_city_auto) else info?.name ?: city) },
        supportingContent = (if (automatic) info?.name else info?.operator)?.let { { Text(it) } },
        trailingContent = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn)) },
    )
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SearchField(query, { query = it }, stringResource(R.string.w_city_search), Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp))
                if (located && folded.isEmpty()) CityOption(stringResource(R.string.w_city_auto), null, null, automatic) { choose(null) }
                // verticalScroll needs a bounded height inside the settings' scroll.
                val fit = if (rowHeight > 0) Modifier.heightIn(max = with(density) { (rowHeight * CITY_ROWS_SHOWN).toDp() }).verticalScroll(scroll) else Modifier
                Column(fit) {
                    shown.forEachIndexed { i, option ->
                        val measure = if (i == 0) Modifier.onSizeChanged { rowHeight = it.height } else Modifier
                        CityOption(option.name, option.operator, option.realtime, !automatic && option.slug == city, measure) { choose(option.slug) }
                    }
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(R.string.w_no_results),
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CityOption(name: String, operator: String?, realtime: Boolean?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        modifier = modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(name) },
        supportingContent = operator?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        trailingContent = when (realtime) {
            true -> { { LiveBadge() } }
            false -> { { TimetableBadge() } }
            null -> null
        },
    )
}

