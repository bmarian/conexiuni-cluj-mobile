package online.bus.bmarian.conexiuniclujmobile.ui.follow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation

private val FOREGROUND = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

object ReminderLocation {
    internal val asking = MutableStateFlow(false)

    internal fun prefs(context: Context) = context.getSharedPreferences("reminder_location", Context.MODE_PRIVATE)

    /** Asks once, after a reminder is set. */
    fun offer(context: Context) {
        val prefs = prefs(context)
        if (UserLocation.allTheTime(context) || prefs.getBoolean("offered", false)) return
        prefs.edit { putBoolean("offered", true) }
        asking.value = true
    }

    fun ask() {
        asking.value = true
    }

    fun openAppSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

@Composable
fun rememberLocationAllTheTime(): Boolean {
    val context = LocalContext.current
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    return remember(state) { UserLocation.allTheTime(context) }
}

@Composable
fun ReminderLocationDialog() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val asking by ReminderLocation.asking.collectAsStateWithLifecycle()

    // Once the system stops asking, only the app's settings can allow it.
    fun request(permission: String, launch: () -> Unit) {
        val prefs = ReminderLocation.prefs(context)
        val refused = prefs.getBoolean(permission, false) && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        if (refused) return ReminderLocation.openAppSettings(context)
        prefs.edit { putBoolean(permission, true) }
        launch()
    }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        request(Manifest.permission.ACCESS_BACKGROUND_LOCATION) { background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        Services.locationPermissionChanged()
        if (granted.values.any { it }) askBackground()
    }

    if (!asking) return
    val option = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.packageManager.backgroundPermissionOptionLabel.toString()
    else stringResource(R.string.location_all_the_time)
    AlertDialog(
        onDismissRequest = { ReminderLocation.asking.value = false },
        icon = { Icon(Icons.Rounded.MyLocation, null) },
        title = { Text(stringResource(R.string.location_reminders)) },
        text = { Text(stringResource(R.string.location_reminders_body, stringResource(R.string.app_name), option)) },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                ReminderLocation.asking.value = false
                if (UserLocation.permitted(context)) askBackground() else request(Manifest.permission.ACCESS_FINE_LOCATION) { foreground.launch(FOREGROUND) }
            }) { Text(stringResource(R.string.location_continue)) }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { ReminderLocation.asking.value = false }) { Text(stringResource(R.string.location_not_now)) }
        },
    )
}
