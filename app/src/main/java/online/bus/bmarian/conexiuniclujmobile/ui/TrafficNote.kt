package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Jam
import online.bus.bmarian.conexiuniclujmobile.data.LiveTraffic
import online.bus.bmarian.conexiuniclujmobile.data.Traffic
import online.bus.bmarian.conexiuniclujmobile.logic.jamMinutes
import online.bus.bmarian.conexiuniclujmobile.logic.worstJam

@Composable
fun shownTraffic(): Map<String, Traffic> {
    val on by Services.settings.traffic.collectAsStateWithLifecycle()
    val all by LiveTraffic.all.collectAsStateWithLifecycle()
    return if (on) all else emptyMap()
}

@Composable
fun rememberJams(): (tripId: String, fromStopId: Int?) -> Jam? {
    val traffic = shownTraffic()
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    return remember(traffic, index) { { tripId, from -> worstJam(traffic[tripId], index?.tripStopIds(tripId).orEmpty(), from) } }
}

@Composable
fun TrafficNote(jam: Jam, modifier: Modifier = Modifier, between: Boolean = false, style: TextStyle = MaterialTheme.typography.bodySmall) {
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    fun name(stopId: Int) = index?.stop(stopId)?.name.orEmpty()
    val text = if (between) {
        stringResource(R.string.w_traffic_between, name(jam.fromStopId), name(jam.toStopId), jamMinutes(jam))
    } else {
        stringResource(R.string.w_traffic_after, name(jam.fromStopId), jamMinutes(jam))
    }
    val color = MaterialTheme.colorScheme.error
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Traffic, null, Modifier.size(16.dp), tint = color)
        Text(text, style = style, color = color, fontWeight = FontWeight.Medium)
    }
}
