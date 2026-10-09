package online.bus.bmarian.conexiuniclujmobile.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Stop
import online.bus.bmarian.conexiuniclujmobile.ui.RowIcon
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ConexiuniTheme
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

class StopWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        setContent {
            ConexiuniTheme(isSystemInDarkTheme()) {
                val scope = rememberCoroutineScope()
                val favorites by Services.favorites.stops.collectAsStateWithLifecycle()
                val stops by Services.repository.stops.collectAsStateWithLifecycle()
                val byId = stops.associateBy { it.stopId }
                val choices = favorites.mapNotNull { byId[it] }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.widget_pick_stop), style = MaterialTheme.typography.headlineSmall)
                        if (choices.isEmpty()) {
                            Text(stringResource(R.string.widget_no_favorites), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = {
                                startActivity(Intent(this@StopWidgetConfigActivity, MainActivity::class.java))
                            }) { Text(stringResource(R.string.app_name)) }
                        }
                        LazyColumn {
                            items(choices, key = { it.stopId }) { stop ->
                                StopChoice(stop) { scope.launch { choose(appWidgetId, stop) } }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun choose(appWidgetId: Int, stop: Stop) {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(this, glanceId) {
            it[StopWidget.STOP_ID] = stop.stopId
            it[StopWidget.STOP_NAME] = stop.name
            it.remove(StopWidget.ROWS)
            it.remove(StopWidget.UPDATED)
        }
        StopWidget().update(this, glanceId)
        StopWidgetWorker.schedule(this)
        StopWidgetWorker.refreshNow(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun StopChoice(stop: Stop, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RowIcon(Icons.Rounded.Place, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Text(stop.name, style = MaterialTheme.typography.bodyLarge)
    }
}
