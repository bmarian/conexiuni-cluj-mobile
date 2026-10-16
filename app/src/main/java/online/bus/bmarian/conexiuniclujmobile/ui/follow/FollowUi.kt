package online.bus.bmarian.conexiuniclujmobile.ui.follow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.follow.FollowState
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.follow.leaveText
import online.bus.bmarian.conexiuniclujmobile.journey.JourneySync
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.TimePill
import online.bus.bmarian.conexiuniclujmobile.ui.minutesLabel
import online.bus.bmarian.conexiuniclujmobile.ui.rememberNow
import online.bus.bmarian.conexiuniclujmobile.ui.squish

@Composable
fun rememberWithNotifications(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pending = action
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

suspend fun openOnWatch(context: Context, path: String) {
    val opened = WearLink.openOnWatch(context, path)
    Services.settings.showToast(context.getString(if (opened) R.string.opened_on_watch else R.string.no_watch))
}

@Composable
fun FollowingCard(state: FollowState, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val followed = state.followed
    val now = rememberNow()
    val context = LocalContext.current
    val press = remember { MutableInteractionSource() }
    // A reminder still waiting for the user to set off.
    val leave = state.leaveAt?.let { at -> state.minutes?.let { leaveText(context, followed.shortName, it, at, state.walkMinutes, System.currentTimeMillis()).first } }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().squish(press),
        interactionSource = press,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (leave != null) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.Rounded.NotificationsActive,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        leave ?: stringResource(R.string.following),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineBadge(followed.shortName, followed.color, height = 28.dp, minWidth = 36.dp, corner = 8.dp)
                    Text(
                        "→ ${followed.destination}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    followed.stopName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.minutes?.let { TimePill(minutesLabel(it, now), live = state.live) }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onStop) { Text(stringResource(R.string.stop_following)) }
            }
        }
    }
}

@Composable
fun JourneyCard(state: JourneySync, onNext: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val now = rememberNow()
    val press = remember { MutableInteractionSource() }
    Card(
        onClick = { onOpen?.invoke() },
        modifier = modifier.fillMaxWidth().squish(press),
        interactionSource = press,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Navigation, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(R.string.journey_to, state.title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.line?.let { LineBadge(it, state.color ?: "#64748b", height = 28.dp, minWidth = 36.dp, corner = 8.dp) }
                Column(Modifier.weight(1f)) {
                    Text(state.step, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    state.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                }
                state.arrivalAt?.let { at ->
                    val minutes = ((at - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(0)
                    TimePill(minutesLabel(minutes, now), live = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onEnd) { Text(stringResource(R.string.journey_end)) }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onNext) { Text(stringResource(R.string.journey_next)) }
                if (onOpen != null) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = onOpen) {
                        Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_resume))
                    }
                }
            }
        }
    }
}
