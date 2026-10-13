package online.bus.bmarian.conexiuniclujmobile.ui.follow

import android.content.res.Resources
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.Reminder
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.follow.reminderTimes
import online.bus.bmarian.conexiuniclujmobile.follow.resolveSlots
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.dayTypeOf
import online.bus.bmarian.conexiuniclujmobile.logic.formatAbsoluteMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.minutesOfDay
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.PopIcon
import online.bus.bmarian.conexiuniclujmobile.ui.Ticker
import online.bus.bmarian.conexiuniclujmobile.ui.squish
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** "Today 23:42", "Tomorrow 07:40" or "Mon 07:40". */
fun reminderWhen(resources: Resources, atMs: Long, locale: Locale): String {
    val at = Instant.ofEpochMilli(atMs).atZone(CLUJ_ZONE)
    val clock = "%02d:%02d".format(at.hour, at.minute)
    val today = LocalDate.now(CLUJ_ZONE)
    return when (at.toLocalDate()) {
        today -> resources.getString(R.string.remind_today, clock)
        today.plusDays(1) -> resources.getString(R.string.remind_tomorrow, clock)
        else -> "${at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }} $clock"
    }
}

/** "Every day", "Mon-Fri", "Weekends" or the days listed; null for a one-off. */
fun reminderRepeat(resources: Resources, days: List<Int>, locale: Locale): String? = when (days.sorted()) {
    emptyList<Int>() -> null
    (1..7).toList() -> resources.getString(R.string.remind_every_day)
    (1..5).toList() -> resources.getString(R.string.w_weekdays)
    listOf(6, 7) -> resources.getString(R.string.remind_weekends)
    else -> days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(followed: Followed, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val locale = LocalConfiguration.current.locales[0]
    val withNotifications = rememberWithNotifications()
    val existing = remember(followed) { Reminders.forBus(followed.stopId, followed.routeId, followed.tripId) }
    val info by produceState<StopInfo?>(null, followed.stopId) {
        value = Services.repository.cachedStopInfo(followed.stopId)
        runCatching { Services.repository.stopInfo(followed.stopId) }.getOrNull()?.let { value = it }
    }
    val times by produceState(emptyMap<String, List<Int>>(), info) { info?.let { value = reminderTimes(it, followed) } }
    val today = remember { LocalDate.now(CLUJ_ZONE) }
    var repeat by remember { mutableStateOf(existing?.once == false) }
    var days by remember { mutableStateOf(existing?.days?.takeIf { it.isNotEmpty() } ?: (1..7).toList()) }
    var last by remember { mutableStateOf(existing != null && existing.time == null) }
    var time by remember { mutableStateOf(existing?.time) }
    // Bumped when a permission comes back, to read it again.
    var asked by remember { mutableIntStateOf(0) }
    val exactAlarms = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { asked++ }

    val nowMinutes = minutesOfDay(LocalDateTime.now(CLUJ_ZONE))
    // The times shown: from the next day the reminder runs on; a one-off only offers what's still to come.
    val dayTimes = if (repeat) {
        val day = (0L..6L).map(today::plusDays).firstOrNull { it.dayOfWeek.value in days } ?: today
        times[dayTypeOf(day)].orEmpty()
    } else {
        times[dayTypeOf(today)].orEmpty().filter { it > nowMinutes }.ifEmpty { times[dayTypeOf(today.plusDays(1))].orEmpty() }
    }
    val chosen = if (last) null else time
    val draft = remember(info, repeat, days, last, time, times) {
        val stop = info ?: return@remember null
        if (!last && time == null) return@remember null
        val slots = resolveSlots(times, chosen)
        val base = Reminder(
            id = existing?.id ?: UUID.randomUUID().toString(),
            followed = followed,
            lat = stop.lat,
            lon = stop.lon,
            time = chosen,
            days = if (repeat) days.sorted() else emptyList(),
            slots = slots,
            handled = existing?.handled ?: 0,
        )
        if (repeat) return@remember base
        // A one-off runs today, or tomorrow once today's has gone.
        listOf(today, today.plusDays(1)).map { base.copy(date = it.toString()) }.firstOrNull { it.next() != null }
    }
    val next = draft?.next()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LineBadge(followed.shortName, followed.color, height = 40.dp, minWidth = 48.dp, corner = 12.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "→ ${followed.destination}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        followed.stopName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ChoiceRow(listOf(false, true), repeat, { repeat = it }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (it) R.string.remind_repeat else R.string.remind_once), maxLines = 1)
            }
            AnimatedVisibility(repeat) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (day in 1..7) {
                        val on = day in days
                        ToggleButton(
                            checked = on,
                            onCheckedChange = {
                                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                days = if (it) days + day else days - day
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(DayOfWeek.of(day).getDisplayName(TextStyle.NARROW_STANDALONE, locale), maxLines = 1)
                        }
                    }
                }
            }

            if (info != null && times.isNotEmpty()) {
                val lastToday = dayTimes.maxOrNull()
                TimeChoice(
                    label = stringResource(R.string.remind_last_bus) + (lastToday?.let { " · ${formatAbsoluteMinutes(it)}" } ?: ""),
                    selected = last,
                    icon = { PopIcon(Icons.Rounded.Bedtime, last, Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    last = true
                }
                Column {
                    for ((hour, group) in dayTimes.groupBy { it / 60 }) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d".format(hour % 24),
                                modifier = Modifier.width(36.dp).padding(end = 10.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                color = if (hour >= 24) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            )
                            VerticalDivider(Modifier.fillMaxHeight(), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            FlowRow(
                                Modifier.padding(start = 10.dp, top = 3.dp, bottom = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (minute in group) {
                                    TimeChoice(
                                        label = formatAbsoluteMinutes(minute).drop(3),
                                        selected = !last && time == minute,
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                        last = false
                                        time = minute
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stays in view under the times.
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            key(asked) {
                if (!Reminders.exactAllowed(context)) {
                    PermissionRow(stringResource(R.string.remind_exact)) {
                        exactAlarms.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    if (draft != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Ticker(
                                next?.let { reminderWhen(resources, it, locale) } ?: stringResource(R.string.remind_no_bus),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        // Day types picked that have no bus near that time.
                        val missing = if (!repeat) emptyList() else TimetableTab.entries.filter { tab ->
                            days.any { dayTypeOf(DayOfWeek.of(it)) == tab.dayType } && tab.dayType !in draft.slots
                        }
                        if (missing.isNotEmpty()) {
                            Text(
                                missing.map { "${tabName(it)}: ${stringResource(R.string.remind_no_bus)}" }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (existing != null) {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            Reminders.remove(context, existing.id)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.remind_delete)) }
                }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    enabled = draft != null && next != null,
                    onClick = {
                        val reminder = draft ?: return@Button
                        withNotifications {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            Reminders.put(context, reminder)
                            Services.settings.showToast(resources.getString(R.string.remind_saved), next?.let { reminderWhen(resources, it, locale) })
                            onDismiss()
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (existing != null) R.string.save else R.string.remind_set))
                }
            }
        }
    }
}

@Composable
private fun tabName(tab: TimetableTab) = stringResource(
    when (tab) {
        TimetableTab.Weekdays -> R.string.w_weekdays
        TimetableTab.Saturday -> R.string.w_saturday
        TimetableTab.Sunday -> R.string.w_sunday
    },
)

@Composable
private fun TimeChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 18.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "container",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp).squish(press, 0.88f),
        interactionSource = press,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            icon?.invoke()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PermissionRow(text: String, onAllow: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onAllow) { Text(stringResource(R.string.remind_allow)) }
        }
    }
}
