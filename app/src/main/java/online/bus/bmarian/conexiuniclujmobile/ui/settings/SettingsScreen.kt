package online.bus.bmarian.conexiuniclujmobile.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.AppUpdates
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.RegionSettings
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalSheet
import online.bus.bmarian.conexiuniclujmobile.ui.PopIcon
import online.bus.bmarian.conexiuniclujmobile.ui.RisingArrow
import online.bus.bmarian.conexiuniclujmobile.ui.SheetSnap
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.buzz
import online.bus.bmarian.conexiuniclujmobile.ui.stop.LiveBadge
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

@Composable
fun SettingsScreen(pairCode: String?, pairKey: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings = Services.settings
    val locale by settings.locale.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val colorful by settings.colorfulLines.collectAsStateWithLifecycle()
    val city by Cities.current.collectAsStateWithLifecycle()
    val regions by settings.regions.collectAsStateWithLifecycle()
    val center by settings.autoCenterOnMe.collectAsStateWithLifecycle()
    val fit by settings.autoFitMap.collectAsStateWithLifecycle()
    val alarms by settings.fullScreenAlarms.collectAsStateWithLifecycle()
    val update by AppUpdates.available.collectAsStateWithLifecycle()
    val watchApp by WearLink.watchWithoutApp.collectAsStateWithLifecycle()
    val sheet = LocalSheet.current
    val scope = rememberCoroutineScope()
    val updateFlow = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}


    val notifications = context.getSystemService(NotificationManager::class.java)
    fun fullScreenAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent()
    val fullScreenAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!fullScreenAllowed()) settings.showToast(resources.getString(R.string.alarm_full_screen_off))
    }
    fun toggleAlarms(on: Boolean) {
        settings.setFullScreenAlarms(on)
        if (on && !fullScreenAllowed()) {
            fullScreenAccess.launch(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
        }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        val pastHeader by remember { derivedStateOf { scroll.value > 120 } }
        SheetTopBar(stringResource(R.string.w_settings), pastHeader)
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            Text(
                stringResource(R.string.w_settings),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Prompt(
                update,
                stringResource(R.string.update_question),
                Icons.Rounded.Check,
                stringResource(R.string.w_yes),
                onYes = { scope.launch { AppUpdates.update(updateFlow) } },
                onNo = AppUpdates::decline,
            ) { RisingArrow(tint = it) }
            Prompt(
                watchApp && !update,
                stringResource(R.string.watch_install_question),
                Icons.Rounded.Download,
                stringResource(R.string.watch_install),
                onYes = { WearLink.installOnWatch(context) },
                onNo = { WearLink.skipWatchApp(context) },
            ) { Icon(Icons.Rounded.Watch, null, Modifier.buzz(), tint = it) }

            Section(stringResource(R.string.w_language))
            if (locale == "en") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Palette.Amber.copy(alpha = 0.12f)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = Palette.Amber)
                    Text(stringResource(R.string.w_language_desc), style = MaterialTheme.typography.bodySmall)
                }
            }
            val languages = listOf("ro" to "Română", "en" to "English")
            ChoiceRow(languages.map { it.first }, locale, settings::setLocale, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { tag ->
                Text(languages.first { it.first == tag }.second)
            }

            Section(stringResource(R.string.w_display))
            ThemeRow(theme, settings::setTheme)
            Toggle(Icons.Rounded.Palette, stringResource(R.string.w_colorful_lines), stringResource(R.string.w_colorful_lines_desc), colorful, onChange = settings::setColorfulLines)

            Section(stringResource(R.string.w_settings_map))
            Toggle(Icons.Rounded.MyLocation, stringResource(R.string.w_auto_center_on_me), stringResource(R.string.w_auto_center_on_me_desc), center, onChange = settings::setAutoCenterOnMe)
            Toggle(Icons.Rounded.CropFree, stringResource(R.string.w_auto_fit_map), stringResource(R.string.w_auto_fit_map_desc), fit, onChange = settings::setAutoFitMap)

            Section(stringResource(R.string.w_city))
            CityChooser()
            for (setting in RegionSettings.forCity(city)) {
                Toggle(
                    regionIcon(setting.key), stringResource(setting.title), stringResource(setting.description),
                    settings.regionSetting(city, setting.key, regions),
                ) { settings.setRegionSetting(city, setting.key, it) }
            }

            Section(stringResource(R.string.w_settings_notifications))
            Toggle(Icons.Rounded.Alarm, stringResource(R.string.alarm_setting), stringResource(R.string.alarm_setting_desc), alarms, onChange = ::toggleAlarms)

            Section(stringResource(R.string.w_sync_title))
            SyncPanel(pairCode, pairKey) { scope.launch { sheet?.animateTo(SheetSnap.Full) } }

            Section(stringResource(R.string.w_contact))
            ListItem(
                modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$CONTACT_EMAIL".toUri())) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Rounded.Email, null) },
                headlineContent = { Text(CONTACT_EMAIL) },
                supportingContent = { Text(stringResource(R.string.w_contact_desc)) },
            )
            Text(
                stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                Modifier.fillMaxWidth().padding(top = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private const val CONTACT_EMAIL = "ceautobuziau@gmail.com"

private fun regionIcon(key: String): ImageVector = when (key) {
    RegionSettings.GREEN_FRIDAY -> Icons.Rounded.Eco
    else -> Icons.Rounded.Tune
}

/** The city on screen; the nearest one is offered only while location is already allowed. */
@Composable
private fun CityChooser() {
    val context = LocalContext.current
    val city by Cities.current.collectAsStateWithLifecycle()
    val choice by Cities.choice.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    val located = remember { UserLocation.permitted(context) }
    val automatic = located && choice == null
    val info = cities.firstOrNull { it.slug == city }
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    fun choose(slug: String?) {
        open = false
        Services.chooseCity(slug)
    }
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable { open = !open },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Rounded.Place, null) },
        headlineContent = { Text(if (automatic) stringResource(R.string.w_city_auto) else info?.name ?: city) },
        supportingContent = (if (automatic) info?.name else info?.operator)?.let { { Text(it) } },
        trailingContent = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn)) },
    )
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            if (located) CityOption(stringResource(R.string.w_city_auto), null, null, automatic) { choose(null) }
            for (option in cities) CityOption(option.name, option.operator, option.realtime, !automatic && option.slug == city) { choose(option.slug) }
        }
    }
}

@Composable
private fun CityOption(name: String, operator: String?, realtime: Boolean?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
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

/** A city without live positions, shaped like [LiveBadge]. */
@Composable
private fun TimetableBadge() {
    Text(
        stringResource(R.string.w_timetable).uppercase(),
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A one-off question at the top. */
@Composable
private fun Prompt(
    visible: Boolean,
    question: String,
    yesIcon: ImageVector,
    yes: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val state = remember { MutableTransitionState(false) }.apply { targetState = visible }
    AnimatedVisibility(
        state,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        icon(colors.onPrimary)
                    }
                    Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = onYes, modifier = Modifier.weight(1f)) {
                        Icon(yesIcon, null, Modifier.size(18.dp))
                        Text(yes, Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onNo,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onPrimaryContainer),
                        border = BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.4f)),
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.w_no), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun Toggle(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = { PopIcon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, checked, Modifier.size(SwitchDefaults.IconSize)) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ThemeRow(theme: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.Light, Icons.Rounded.LightMode, R.string.w_theme_light),
        Triple(ThemeMode.Dark, Icons.Rounded.DarkMode, R.string.w_theme_dark),
        Triple(ThemeMode.System, Icons.Rounded.BrightnessMedium, R.string.w_theme_system),
    )
    ChoiceRow(
        options.map { it.first }, theme, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        icon = { mode -> options.first { it.first == mode }.second },
    ) { mode ->
        Text(stringResource(options.first { it.first == mode }.third), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
