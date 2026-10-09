package online.bus.bmarian.conexiuniclujmobile.journey

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import online.bus.bmarian.conexiuniclujmobile.ui.map.LatLon

@Serializable
data class JourneyStop(val id: Int, val name: String, val lat: Double, val lon: Double)

@Serializable
data class JourneyPoint(val lat: Double, val lon: Double)

/** One bus ride: the lines that make it (alternatives like 45 / 44) and its stops, boarding first. */
@Serializable
data class JourneyLeg(
    val routeIds: List<Int>,
    val tripIds: List<String>,
    val lines: List<String>,
    val colors: List<String>,
    val stops: List<JourneyStop>,
    // The walk to the first stop, and the ride, as planned; they size the progress bar.
    val walkMinutes: Double = 0.0,
    val rideMinutes: Double = 0.0,
    // The ride as the map draws it, boarding to alighting, light enough to send to the watch.
    val path: List<JourneyPoint> = emptyList(),
) {
    val boarding get() = stops.first()
    val alighting get() = stops.last()
    val lineLabel get() = lines.joinToString(" / ")
}

/**
 * A trip to take step by step: walk to the first stop, ride, change, walk to [destination].
 * A "wake me before my stop" from a line page is one leg whose last stop is the destination.
 */
@Serializable
data class Journey(
    val id: String,
    val destination: JourneyStop,
    val legs: List<JourneyLeg>,
    // Set for a planner trip, so the notification opens it again.
    val planPath: String? = null,
    val walkEndMinutes: Double = 0.0,
    // The walks, as the planner found them, for the maps.
    val walks: List<List<JourneyPoint>> = emptyList(),
) {
    val endsAtStop get() = destination.id == legs.last().alighting.id
}

/** The step on show, as both the phone's card and the watch read it. */
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
    // Version 2, for the navigation screens; older watch apps ignore it.
    val stepType: String? = null,
    val legIndex: Int = 0,
    val stopsLeft: Int? = null,
    val totalStops: Int? = null,
    // When you get there, and how much of the trip is behind you (0 to 1).
    val etaAt: Long? = null,
    val progress: Float? = null,
    // Where the phone is, for the watch's map when the watch has no position of its own.
    val lat: Double? = null,
    val lon: Double? = null,
    val bearing: Float? = null,
)

/** What a step is, for screens that draw it rather than read it out. */
object StepType {
    const val WALK = "walk"
    const val WAIT = "wait"
    const val RIDE = "ride"
    const val WALK_END = "walkEnd"
    const val ARRIVED = "arrived"
}

/** A path light enough to send to the watch: points at least [minMeters] apart, to 5 decimals. */
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

/** The journey under way, if any: one at a time, run by [JourneyService]. */
object JourneyManager {
    internal val mutableState = MutableStateFlow<JourneySync?>(null)
    val state: StateFlow<JourneySync?> = mutableState

    // The whole trip under way, for the navigation screen and the watch's map.
    internal val mutableJourney = MutableStateFlow<Journey?>(null)
    val journey: StateFlow<Journey?> = mutableJourney

    /** Whether the navigation screen is up; the journey runs on behind it either way. */
    val navigating = MutableStateFlow(false)

    fun openNavigation() {
        if (state.value != null) navigating.value = true
    }

    fun closeNavigation() {
        navigating.value = false
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun start(context: Context, journey: Journey) {
        mutableState.value = JourneySync(journey.id, journey.destination.name, "")
        mutableJourney.value = journey
        navigating.value = true
        // The plan first, so the watch can draw the trip as soon as it opens.
        WearLink.sendJourneyPlan(context, journey)
        val intent = Intent(context, JourneyService::class.java)
            .putExtra(JourneyService.EXTRA_JOURNEY, Api.json.encodeToString(journey))
        ContextCompat.startForegroundService(context, intent)
        WearLink.showOnWatch(context, "/journey")
    }

    fun stop(context: Context) {
        mutableState.value = null
        mutableJourney.value = null
        navigating.value = false
        context.stopService(Intent(context, JourneyService::class.java))
    }

    // Taps on Next, counted; the service moves on one step for each.
    internal val nextRequests = MutableStateFlow(0)

    /** Skips to the next step when the phone hasn't noticed it yet. */
    @Suppress("UNUSED_PARAMETER")
    fun next(context: Context) {
        nextRequests.value++
    }

    /** End was pressed on the watch. */
    internal fun onRemoteStop(context: Context, id: String) {
        if (state.value?.id == id) stop(context)
    }

    /** The watch opened the journey without its plan (it was off or out of reach): send it again. */
    internal fun onPlanRequest(context: Context, id: String) {
        val current = journey.value ?: return
        if (id.isEmpty() || id == current.id) WearLink.sendJourneyPlan(context, current)
    }

    internal fun publish(context: Context, sync: JourneySync) {
        if (!sync.end) mutableState.value = sync
        WearLink.sendJourney(context, sync)
    }
}
