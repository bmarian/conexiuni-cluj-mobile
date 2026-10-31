package online.bus.bmarian.conexiuniclujmobile.journey

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import java.util.UUID

@Serializable
data class JourneyStop(val id: Int, val name: String, val lat: Double, val lon: Double)

@Serializable
data class JourneyPoint(val lat: Double, val lon: Double)

@Serializable
data class JourneyLeg(
    val routeIds: List<Int>,
    val tripIds: List<String>,
    val lines: List<String>,
    val colors: List<String>,
    val stops: List<JourneyStop>,
    val walkMinutes: Double = 0.0,
    val rideMinutes: Double = 0.0,
    val path: List<JourneyPoint> = emptyList(),
) {
    val boarding get() = stops.first()
    val alighting get() = stops.last()
    val lineLabel get() = lines.joinToString(" / ")
}

@Serializable
data class PlannedRun(val arrive: Long, val departs: List<Long>)

@Serializable
data class Journey(
    val id: String,
    val destination: JourneyStop,
    val legs: List<JourneyLeg>,
    val planPath: String? = null,
    val walkEndMinutes: Double = 0.0,
    val walks: List<List<JourneyPoint>> = emptyList(),
    /** The plan's timed runs: when each ride's bus leaves (unix s) and when the run arrives. */
    val runs: List<PlannedRun> = emptyList(),
    // Started before cities: Cluj.
    val city: String = Cities.DEFAULT,
    val onBoard: Boolean = false,
) {
    val endsAtStop get() = destination.id == legs.last().alighting.id
}

@Serializable
data class JourneySync(
    val id: String,
    val title: String,
    val step: String,
    val detail: String? = null,
    val line: String? = null,
    val color: String? = null,
    val arrivalAt: Long? = null,
    val stepIndex: Int = 0,
    val alert: Boolean = false,
    val end: Boolean = false,
    val stepType: String? = null,
    val legIndex: Int = 0,
    val stopsLeft: Int? = null,
    val totalStops: Int? = null,
    val etaAt: Long? = null,
    val progress: Float? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val bearing: Float? = null,
) {
    /** Waiting at the stop: the bus's countdown is the whole story, so it's shown instead of [detail]. */
    val countsDown get() = stepType == StepType.WAIT && arrivalAt != null
}

object StepType {
    const val WALK = "walk"
    const val WAIT = "wait"
    const val RIDE = "ride"
    const val WALK_END = "walkEnd"
    const val ARRIVED = "arrived"
}

fun simplifyPath(points: List<LatLon>, minMeters: Double = 12.0): List<JourneyPoint> {
    if (points.isEmpty()) return emptyList()
    val kept = mutableListOf(points.first())
    for (p in points.drop(1)) {
        val last = kept.last()
        if (haversineMeters(last.lat, last.lon, p.lat, p.lon) >= minMeters) kept += p
    }
    if (kept.last() != points.last()) kept += points.last()
    fun round(v: Double) = Math.round(v * 100_000) / 100_000.0
    return kept.map { JourneyPoint(round(it.lat), round(it.lon)) }
}

object JourneyManager {
    internal val mutableState = MutableStateFlow<JourneySync?>(null)
    val state: StateFlow<JourneySync?> = mutableState

    internal val mutableJourney = MutableStateFlow<Journey?>(null)
    val journey: StateFlow<Journey?> = mutableJourney

    val navigating = MutableStateFlow(false)

    fun openNavigation() {
        if (state.value != null) navigating.value = true
    }

    fun closeNavigation() {
        navigating.value = false
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** False when the app is in the background and may not start the service. */
    fun start(context: Context, journey: Journey): Boolean {
        mutableState.value = JourneySync(journey.id, journey.destination.name, "")
        mutableJourney.value = journey
        navigating.value = true
        val intent = Intent(context, JourneyService::class.java)
            .putExtra(JourneyService.EXTRA_JOURNEY, Api.json.encodeToString(journey))
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: IllegalStateException) {
            mutableState.value = null
            mutableJourney.value = null
            navigating.value = false
            return false
        }
        WearLink.sendJourneyPlan(context, journey)
        WearLink.showOnWatch(context, "/journey")
        return true
    }

    fun stop(context: Context) {
        mutableState.value = null
        mutableJourney.value = null
        navigating.value = false
        context.stopService(Intent(context, JourneyService::class.java))
    }

    internal val nextRequests = MutableStateFlow(0)

    @Suppress("UNUSED_PARAMETER")
    fun next(context: Context) {
        nextRequests.value++
    }

    internal fun onRemoteStop(context: Context, id: String) {
        if (state.value?.id == id) stop(context)
    }

    internal fun onPlanRequest(context: Context, id: String) {
        val current = journey.value ?: return
        if (id.isEmpty() || id == current.id) WearLink.sendJourneyPlan(context, current)
    }

    internal fun publish(context: Context, sync: JourneySync) {
        if (!sync.end) mutableState.value = sync
        WearLink.sendJourney(context, sync)
    }
}
