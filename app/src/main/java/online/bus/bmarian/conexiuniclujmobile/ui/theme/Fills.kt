package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable

@Composable
fun fillButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryFixed, contentColor = MaterialTheme.colorScheme.onPrimaryFixed)

@Composable
fun fillSwitchColors(): SwitchColors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primaryFixed)

@Composable
fun fillTimePickerColors(): TimePickerColors {
    val scheme = MaterialTheme.colorScheme
    val dial = if (LocalDoodle.current != null) scheme.surfaceContainerHighest else scheme.surfaceContainer
    return TimePickerDefaults.colors(clockDialColor = dial, selectorColor = scheme.primaryFixed, clockDialSelectedContentColor = scheme.onPrimaryFixed)
}
