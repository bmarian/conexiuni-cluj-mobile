package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.LegacyLocale
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.ui.LineBadge
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ConexiuniTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(ms: Long) = CLOCK.format(Instant.ofEpochMilli(ms).atZone(CLUJ_ZONE))

class AlarmActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        LegacyLocale.override(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val alarm = AlarmInfo(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT),
            line = intent.getStringExtra(EXTRA_LINE).orEmpty(),
            color = intent.getStringExtra(EXTRA_COLOR).orEmpty(),
            destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty(),
            stopName = intent.getStringExtra(EXTRA_STOP_NAME).orEmpty(),
            busAt = intent.getLongExtra(EXTRA_AT, 0L),
            journey = intent.getBooleanExtra(EXTRA_JOURNEY, false),
        )
        val stopId = intent.getIntExtra(EXTRA_STOP, 0)
        val target = if (alarm.journey) "journey" else "stop/$stopId"
        setContent {
            val mode by Services.settings.theme.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            ConexiuniTheme(dark) {
                // Stopped from the notification, or it timed out.
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(2_000)
                        if (!ReminderAlarm.ringing(this@AlarmActivity)) finish()
                    }
                }
                AlarmScreen(
                    alarm,
                    onStop = {
                        ReminderAlarm.stop(this)
                        finish()
                    },
                    onOpen = {
                        ReminderAlarm.stop(this)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .setData("${BuildConfig.SITE_ORIGIN}/$target".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_LINE = "line"
        const val EXTRA_COLOR = "color"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_STOP = "stop"
        const val EXTRA_STOP_NAME = "stopName"
        const val EXTRA_AT = "at"
        const val EXTRA_JOURNEY = "journey"
    }
}

private class AlarmInfo(
    val title: String,
    val text: String?,
    val line: String,
    val color: String,
    val destination: String,
    val stopName: String,
    val busAt: Long,
    val journey: Boolean,
)

@Composable
private fun AlarmScreen(alarm: AlarmInfo, onStop: () -> Unit, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.padding(start = 8.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.alarm_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                clock(now),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alarm.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        alarm.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LineBadge(alarm.line, alarm.color, height = 40.dp, minWidth = 56.dp, corner = 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "→ ${alarm.destination}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            alarm.stopName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (alarm.busAt > 0) Text(clock(alarm.busAt), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onOpen, Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(if (alarm.journey) R.string.nav_resume else R.string.alarm_open), style = MaterialTheme.typography.titleMedium)
                }
                Button(onStop, Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.alarm_stop), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
