package online.bus.bmarian.conexiuniclujmobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow

private enum class Step { Welcome, Location, Notifications }

@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = Services.settings
    val steps = remember {
        buildList {
            add(Step.Welcome)
            if (!UserLocation.permitted(context)) add(Step.Location)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Step.Notifications)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    fun next() {
        if (index < steps.lastIndex) index++ else onDone()
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Services.locationPermissionChanged()
        next()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    val locale by settings.locale.collectAsStateWithLifecycle()
    val step = steps[index]

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier.height(4.dp).width(if (i == index) 24.dp else 12.dp).clip(CircleShape)
                            .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = { (fadeIn() + slideInHorizontally { it / 6 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 6 }) },
                label = "step",
            ) { shown ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    val (icon, title, body) = when (shown) {
                        Step.Welcome -> Triple(Icons.Rounded.DirectionsBus, R.string.onboard_welcome_title, R.string.onboard_welcome_body)
                        Step.Location -> Triple(Icons.Rounded.MyLocation, R.string.onboard_location_title, R.string.onboard_location_body)
                        Step.Notifications -> Triple(Icons.Rounded.NotificationsActive, R.string.onboard_notifications_title, R.string.onboard_notifications_body)
                    }
                    StepIcon(icon)
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (shown == Step.Welcome) {
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(R.string.w_language), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        val options = listOf("ro" to "Română", "en" to "English")
                        ChoiceRow(options.map { it.first }, locale, settings::setLocale, Modifier.fillMaxWidth()) { tag ->
                            Text(options.first { it.first == tag }.second)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                when (step) {
                    Step.Welcome -> Button(shapes = ButtonDefaults.shapes(), onClick = ::next) {
                        Text(stringResource(if (index == steps.lastIndex) R.string.onboard_done else R.string.onboard_next))
                    }
                    Step.Location -> {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = ::next) { Text(stringResource(R.string.onboard_skip)) }
                        Button(shapes = ButtonDefaults.shapes(), onClick = {
                            location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text(stringResource(R.string.onboard_allow)) }
                    }
                    Step.Notifications -> {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = ::next) { Text(stringResource(R.string.onboard_skip)) }
                        Button(shapes = ButtonDefaults.shapes(), onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.onboard_allow)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
