package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.SelectField
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.parsePlannerDateTime
import online.bus.bmarian.conexiuniclujmobile.logic.planDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MODEL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private enum class Picking { Time, Day }

/** Port of PlanTimeFields.vue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTimeFields(value: String, minMs: Long, onValue: (String) -> Unit) {
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(minMs), CLUJ_ZONE).toLocalDate()
    val current = parsePlannerDateTime(value)?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), CLUJ_ZONE) } ?: today.atStartOfDay()
    var picking by remember { mutableStateOf<Picking?>(null) }

    fun set(day: LocalDate, hour: Int, minute: Int) = onValue(day.atTime(hour, minute).format(MODEL))

    val press = remember { MutableInteractionSource() }
    LaunchedEffect(press) {
        press.interactions.collect { if (it is PressInteraction.Release) picking = Picking.Time }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = current.format(CLOCK),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.w_plan_time)) },
            trailingIcon = { IconButton(onClick = { picking = Picking.Time }) { Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.w_time_picker_title)) } },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            interactionSource = press,
        )
        val days = planDays(today, current.toLocalDate()).map { it to dayLabel(it, today) } + (null to stringResource(R.string.w_plan_other_day))
        SelectField(stringResource(R.string.w_plan_day), dayLabel(current.toLocalDate(), today), days, Modifier.weight(2f)) { day ->
            if (day == null) picking = Picking.Day else set(day, current.hour, current.minute)
        }
    }

    when (picking) {
        Picking.Time -> TimeDialog(current, onDismiss = { picking = null }) { hour, minute ->
            picking = null
            set(current.toLocalDate(), hour, minute)
        }
        Picking.Day -> DayDialog(current.toLocalDate(), today, onDismiss = { picking = null }) { day ->
            picking = null
            set(day, current.hour, current.minute)
        }
        null -> Unit
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.w_plan_today)
    today.plusDays(1) -> stringResource(R.string.w_plan_tomorrow)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        day.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale)).replaceFirstChar { it.titlecase(locale) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(current: LocalDateTime, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onPick(state.hour, state.minute) }) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(stringResource(R.string.w_time_picker_title)) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker },
                displayMode = mode,
            )
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.w_cancel)) } },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(current: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: current)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.w_cancel)) } },
    ) { DatePicker(state) }
}
