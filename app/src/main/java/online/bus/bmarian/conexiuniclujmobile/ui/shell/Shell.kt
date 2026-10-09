package online.bus.bmarian.conexiuniclujmobile.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Toast

@Composable
fun OfflinePill(online: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(!online, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.clip(CircleShape).background(Color(0xF2B45309)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.WifiOff, null, Modifier.size(14.dp), tint = Color.White)
            Text(stringResource(R.string.w_offline_short), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Services.settings.toasts.collectLatest { toast ->
            if (host.showSnackbar(ToastVisuals(toast)) == SnackbarResult.ActionPerformed) toast.action?.invoke()
        }
    }
    SnackbarHost(host, modifier.widthIn(max = 560.dp)) { data ->
        val toast = (data.visuals as ToastVisuals).toast
        Snackbar(
            action = toast.actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                    ) { Text(label) }
                }
            },
        ) {
            Column {
                Text(toast.title)
                toast.body?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f))
                }
            }
        }
    }
}

private class ToastVisuals(val toast: Toast) : SnackbarVisuals {
    override val message = toast.title
    override val actionLabel = toast.actionLabel
    override val withDismissAction = false
    override val duration = if (toast.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
}

/** Dismissal keyed by UTC date, as on the website. */
@Composable
fun GreenFridayBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("greenFriday", android.content.Context.MODE_PRIVATE) }
    val isFriday = remember { LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY }
    val todayKey = remember { Instant.now().toString().take(10) }
    var dismissed by remember { mutableStateOf(prefs.getString("dismissed", null) == todayKey) }
    if (!isFriday || dismissed) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFFDCFCE7), Color(0xFFD1FAE5))))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF10B981)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Eco, null, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.w_green_friday_title), color = Color(0xFF065F46), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.w_green_friday_desc), color = Color(0xFF047857), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = {
            prefs.edit { putString("dismissed", todayKey) }
            dismissed = true
        }) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_dismiss), tint = Color(0xFF059669))
        }
    }
}
