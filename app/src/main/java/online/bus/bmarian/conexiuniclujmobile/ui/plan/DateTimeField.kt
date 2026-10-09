package online.bus.bmarian.conexiuniclujmobile.ui.plan

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.parsePlannerDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SHOWN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val MODEL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/**
 * The website's date-time picker: a day, then a time, 24 h, no earlier than now; nothing
 * changes until both are confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(value: String, minMs: Long, label: String, onValue: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    val current = parsePlannerDateTime(value)?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), CLUJ_ZONE) }
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(minMs), CLUJ_ZONE).toLocalDate()
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release) step = 1 }
    }
    Column {
        OutlinedTextField(
            value = current?.format(SHOWN) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = true,
            label = { Text(label) },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = { step = 1 }) { Icon(Icons.Rounded.CalendarMonth, contentDescription = label) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            interactionSource = source,
        )
    }

    if (step == 1) {
        val initial = (current?.toLocalDate() ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: today
                    step = 2
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.w_cancel)) } },
        ) { DatePicker(state) }
    }
    if (step == 2) {
        val time = rememberTimePickerState(current?.hour ?: 0, current?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickedDate ?: today
                    onValue(LocalDateTime.of(date.year, date.month, date.dayOfMonth, time.hour, time.minute).format(MODEL))
                    step = 0
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.w_cancel)) } },
            text = { TimePicker(time) },
        )
    }
}
