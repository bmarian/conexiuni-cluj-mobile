package online.bus.bmarian.conexiuniclujmobile.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.MainActivity
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color

/** One line on the widget: its badge, where it goes, and the next times as clock times. */
@Serializable
data class WidgetRow(val line: String, val color: String, val destination: String, val times: List<String>, val live: Boolean)

/**
 * A stop's next departures on the home screen, worked out on the phone like the stop page.
 * Times are clock times, so they stay right between the refreshes every quarter of an hour.
 */
class StopWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content(context, currentState()) }
        }
    }

    @Composable
    private fun Content(context: Context, prefs: Preferences) {
        val stopId = prefs[STOP_ID]
        val rows = prefs[ROWS]?.let { runCatching { Api.json.decodeFromString<List<WidgetRow>>(it) }.getOrNull() }
        val updated = prefs[UPDATED]
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData((if (stopId != null) "${WearLink.APP_SCHEME}://bus.bmarian.online/stop/$stopId" else "${WearLink.APP_SCHEME}://bus.bmarian.online/").toUri())
        Column(
            GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity(open)),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prefs[STOP_NAME] ?: context.getString(R.string.widget_pick_stop),
                    GlanceModifier.defaultWeight(),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                    maxLines = 1,
                )
                Image(
                    ImageProvider(R.drawable.ic_refresh),
                    contentDescription = context.getString(R.string.refresh),
                    modifier = GlanceModifier.size(32.dp).padding(4.dp).clickable(actionRunCallback<RefreshStopWidget>()),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            when {
                rows == null -> Text(context.getString(R.string.widget_loading), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                rows.isEmpty() -> Text(context.getString(R.string.widget_no_departures), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                else -> rows.take(4).forEach { row -> DepartureRow(row) }
            }
            if (updated != null) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    context.getString(R.string.widget_updated, CLOCK.format(Instant.ofEpochMilli(updated).atZone(java.time.ZoneId.of("Europe/Bucharest")))),
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    @Composable
    private fun DepartureRow(row: WidgetRow) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                GlanceModifier.width(44.dp).height(24.dp).cornerRadius(7.dp).background(ColorProvider(routeColor(row.color))),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.line, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorProvider(Color.White)), maxLines = 1)
            }
            Text(
                "→ ${row.destination}",
                GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
            Text(
                row.times.take(2).joinToString("  "),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (row.live) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
            )
        }
    }

    companion object {
        val STOP_ID = intPreferencesKey("stopId")
        val STOP_NAME = stringPreferencesKey("stopName")
        val ROWS = stringPreferencesKey("rows")
        val UPDATED = longPreferencesKey("updated")
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

class StopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StopWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StopWidgetWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        StopWidgetWorker.cancel(context)
    }
}

/** The widget's refresh button. */
class RefreshStopWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StopWidgetWorker.refreshNow(context)
    }
}
