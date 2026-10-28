package online.bus.bmarian.conexiuniclujmobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders

@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = Services.settings
    val locale by settings.locale.collectAsStateWithLifecycle()
    var asked by remember { mutableIntStateOf(0) }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Services.locationPermissionChanged()
        asked++
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }
    val alarms = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { asked++ }
    val located = remember(asked) { UserLocation.permitted(context) }
    val notified = remember(asked) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val alarmed = remember(asked) { Reminders.exactAllowed(context) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
                RoundIcon(Icons.Rounded.DirectionsBus, 72.dp, 36.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.onboard_welcome_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onboard_welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.w_language), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                val options = listOf("ro" to "Română", "en" to "English")
                ChoiceRow(options.map { it.first }, locale, settings::setLocale, Modifier.fillMaxWidth()) { tag ->
                    Text(options.first { it.first == tag }.second)
                }
                Spacer(Modifier.height(28.dp))
                PermissionCard(Icons.Rounded.MyLocation, R.string.onboard_location_title, R.string.onboard_location_body, located) {
                    location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(12.dp))
                    PermissionCard(Icons.Rounded.NotificationsActive, R.string.onboard_notifications_title, R.string.onboard_notifications_body, notified) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(12.dp))
                    PermissionCard(Icons.Rounded.Alarm, R.string.onboard_alarms_title, R.string.onboard_alarms_body, alarmed) {
                        alarms.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onDone) { Text(stringResource(R.string.onboard_done)) }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: Int, body: Int, granted: Boolean, onAllow: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIcon(icon, 40.dp, 22.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(shapes = ButtonDefaults.shapes(), onClick = onAllow) { Text(stringResource(R.string.onboard_allow)) }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
