package online.bus.bmarian.conexiuniclujmobile.journey

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.follow.arrivingText
import online.bus.bmarian.conexiuniclujmobile.follow.clockAt
import online.bus.bmarian.conexiuniclujmobile.follow.isClockTime
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.data.Vehicle
import online.bus.bmarian.conexiuniclujmobile.data.VehicleStream
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.computeStopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt

private const val TICK_MS = 5_000L
private const val STOP_INFO_REFRESH_MS = 120_000L
// Close enough to count as at the stop, and far enough from it to count as on the bus.
private const val AT_STOP_M = 40.0
private const val LEFT_STOP_M = 150.0
// How far from the line's stops a position can be and still say which stop the bus is at.
private const val ON_LINE_M = 300.0
private const val ARRIVED_M = 50.0
// Nobody's trip lasts this long; a forgotten journey shouldn't drain the battery.
private const val GIVE_UP_MS = 4 * 60 * 60_000L

/** Where the journey is. Each change is a new step: the watch buzzes for it. */
private sealed interface Phase {
    val leg: Int
    data class Walk(override val leg: Int) : Phase
    data class Wait(override val leg: Int) : Phase
    data class Ride(override val leg: Int) : Phase
    data object WalkEnd : Phase { override val leg = -1 }
    data object Done : Phase { override val leg = -1 }
}

/**
 * Walks a trip through step by step, like Maps transit navigation: walk to the stop, wait for
 * the bus with its live countdown, count the stops on board and buzz one before yours, then
 * the next leg or the walk to the destination. Uses the phone's position when allowed, and
 * the clock and the bus times when not.
 */
class JourneyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var journey: Journey? = null
    private var phase: Phase = Phase.Walk(0)
    private var stepIndex = 0
    private var alerted = false
    private var ended = false
    // Progress on board: the furthest stop reached, and when the ride began.
    private var rideStop = 0
    private var rideStartedAt = 0L
    private var lastDepartureMinutes: Int? = null
    private var walkEndStartedAt = 0L
    private var walkStartM = 0.0
    private val legIndex = MutableStateFlow(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val parsed = intent?.getStringExtra(EXTRA_JOURNEY)
            ?.let { runCatching { Api.json.decodeFromString<Journey>(it) }.getOrNull() }
        if (parsed == null || parsed.legs.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (JourneyManager.journey.value?.id != parsed.id) JourneyManager.mutableJourney.value = parsed
        if (journey?.id != parsed.id) {
            journey = parsed
            phase = Phase.Walk(0)
            stepIndex = 0
            alerted = false
            ended = false
            rideStop = 0
            legIndex.value = 0
        }
        val located = UserLocation.permitted(this)
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
            (if (located) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        val first = describe(parsed, null, null, System.currentTimeMillis())
        JourneyManager.publish(this, first)
        ServiceCompat.startForeground(this, JOURNEY_NOTIFICATION_ID, JourneyNotifications.ongoing(this, parsed, first, progress(parsed, null)), type)
        job?.cancel()
        job = scope.launch { run(parsed) }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        scope.cancel()
        val current = journey
        // Ended from the app or the watch: the watch drops its copy too, and "get off" goes.
        if (current != null && !ended) {
            JourneyManager.publish(this, JourneySync(current.id, current.destination.name, "", end = true))
            JourneyNotifications.clearAlert(this)
        }
        if (JourneyManager.state.value?.id == current?.id) {
            JourneyManager.mutableState.value = null
            JourneyManager.mutableJourney.value = null
            JourneyManager.navigating.value = false
        }
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun locations(): Flow<Fix?> = callbackFlow {
        if (!UserLocation.permitted(this@JourneyService)) {
            send(null)
            awaitClose {}
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(this@JourneyService)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(Fix(it.latitude, it.longitude, it.accuracy, it.bearing.takeIf { _ -> it.hasBearing() })) }
            }
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TICK_MS).setMinUpdateIntervalMillis(2_000).build(),
            callback,
            Looper.getMainLooper(),
        )
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private suspend fun run(journey: Journey) = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val fix = MutableStateFlow<Fix?>(null)
        val departure = MutableStateFlow<StopDeparture?>(null)
        val clock = MutableStateFlow(startedAt)
        launch { locations().collect { fix.value = it } }
        launch {
            while (true) {
                delay(TICK_MS)
                clock.value = System.currentTimeMillis()
            }
        }
        // The next bus of the leg ahead, worked out at its first stop like the stop page does.
        launch {
            legIndex.collectLatest { i ->
                departure.value = null
                val leg = journey.legs.getOrNull(i) ?: return@collectLatest
                val repository = Services.repository
                val shapes = runCatching { repository.shapes(leg.tripIds) }.getOrDefault(emptyMap())
                val info = MutableStateFlow(repository.cachedStopInfo(leg.boarding.id)?.only(leg.routeIds))
                val vehicles = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())
                launch {
                    while (true) {
                        runCatching { info.value = repository.stopInfo(leg.boarding.id).only(leg.routeIds) }
                        delay(STOP_INFO_REFRESH_MS)
                    }
                }
                launch { VehicleStream.vehicles(leg.tripIds).collect { vehicles.value = it } }
                combine(info, vehicles, clock) { stop, buses, nowMs -> Triple(stop, buses, nowMs) }.collect { (stop, buses, nowMs) ->
                    if (stop == null) return@collect
                    departure.value = withContext(Dispatchers.Default) {
                        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
                        computeStopDepartures(stop, shapes, buses, now, nowMs)
                            .filter { it.tripId in leg.tripIds }
                            .minByOrNull { it.nextTimes.firstOrNull()?.minutes ?: Int.MAX_VALUE }
                    }
                }
            }
        }
        var handledNext = JourneyManager.nextRequests.value
        combine(fix, departure, clock, JourneyManager.nextRequests) { f, d, nowMs, next -> Pair(Triple(f, d, nowMs), next) }.collect { (inputs, next) ->
            val (f, d, nowMs) = inputs
            if (nowMs - startedAt > GIVE_UP_MS) return@collect finish(journey)
            val skip = next != handledNext
            handledNext = next
            step(journey, f, d, nowMs, skip)
        }
    }

    private fun step(journey: Journey, fix: Fix?, departure: StopDeparture?, nowMs: Long, skip: Boolean) {
        val before = phase
        phase = advance(journey, phase, fix, departure, nowMs, skip)
        if (phase != before) {
            stepIndex++
            alerted = false
            if (phase is Phase.Ride && before !is Phase.Ride) {
                rideStop = 0
                rideStartedAt = nowMs
            }
            if (phase is Phase.WalkEnd) walkEndStartedAt = nowMs
            if (phase is Phase.Walk) walkStartM = fix?.let { distanceTo(it, journey.legs[phase.leg].boarding) } ?: 0.0
            if (phase.leg >= 0) legIndex.value = phase.leg
        }
        if (phase == Phase.Done) return finish(journey)
        val described = describe(journey, fix, departure, nowMs)
        val progress = progress(journey, fix)
        val sync = described.copy(
            progress = (progress.doneMinutes / progress.parts.sumOf { it.minutes }.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            etaAt = arrivalAt(progress, departure, nowMs),
        )
        if (sync.alert && !alerted) {
            alerted = true
            stepIndex++
            JourneyNotifications.alert(this, journey, sync)
        }
        val shown = sync.copy(stepIndex = stepIndex)
        JourneyManager.publish(this, shown)
        getSystemService(NotificationManager::class.java)
            .notify(JOURNEY_NOTIFICATION_ID, JourneyNotifications.ongoing(this, journey, shown, progress))
    }

    private fun advance(journey: Journey, phase: Phase, fix: Fix?, departure: StopDeparture?, nowMs: Long, skip: Boolean): Phase {
        fun afterLeg(i: Int): Phase = when {
            i + 1 < journey.legs.size -> {
                val here = journey.legs[i].alighting
                val next = journey.legs[i + 1].boarding
                if (haversineMeters(here.lat, here.lon, next.lat, next.lon) <= AT_STOP_M) Phase.Wait(i + 1) else Phase.Walk(i + 1)
            }
            journey.endsAtStop -> Phase.Done
            else -> Phase.WalkEnd
        }
        return when (phase) {
            is Phase.Walk -> {
                val leg = journey.legs[phase.leg]
                when {
                    skip -> Phase.Wait(phase.leg)
                    fix != null && boarded(fix, leg) -> Phase.Ride(phase.leg)
                    fix != null && distanceTo(fix, leg.boarding) <= max(AT_STOP_M, fix.accuracyMeters.toDouble()) -> Phase.Wait(phase.leg)
                    fix == null && busLeft(departure) -> Phase.Ride(phase.leg)
                    else -> phase
                }
            }
            is Phase.Wait -> {
                val leg = journey.legs[phase.leg]
                when {
                    skip -> Phase.Ride(phase.leg)
                    fix != null && boarded(fix, leg) -> Phase.Ride(phase.leg)
                    fix == null && busLeft(departure) -> Phase.Ride(phase.leg)
                    else -> phase
                }
            }
            is Phase.Ride -> {
                val leg = journey.legs[phase.leg]
                val last = leg.stops.lastIndex
                if (fix != null) {
                    nearestStop(fix, leg)?.let { rideStop = max(rideStop, it) }
                } else {
                    // No position: count the stops off the ride time.
                    val perStop = leg.rideMinutes.takeIf { it > 0 }?.div(last.coerceAtLeast(1)) ?: 2.0
                    rideStop = max(rideStop, ((nowMs - rideStartedAt) / 60_000.0 / perStop).toInt().coerceAtMost(last))
                }
                val there = fix != null && distanceTo(fix, leg.alighting) <= max(ARRIVED_M, fix.accuracyMeters.toDouble())
                if (skip || there || rideStop >= last) afterLeg(phase.leg) else phase
            }
            Phase.WalkEnd -> {
                val walkMin = fix?.let { distanceTo(it, journey.destination) / WALK_SPEED } ?: 0.0
                when {
                    skip -> Phase.Done
                    fix != null && distanceTo(fix, journey.destination) <= max(ARRIVED_M, fix.accuracyMeters.toDouble()) -> Phase.Done
                    // Without a position, or never quite there: the walk can't take much more than this.
                    nowMs - walkEndStartedAt > (walkMin * 2 + 10) * 60_000 -> Phase.Done
                    else -> phase
                }
            }
            Phase.Done -> phase
        }
    }

    // On the bus: away from the boarding stop and at a later stop of the line.
    private fun boarded(fix: Fix, leg: JourneyLeg): Boolean =
        distanceTo(fix, leg.boarding) > LEFT_STOP_M && (nearestStop(fix, leg) ?: 0) >= 1

    // The bus came and went: its countdown reached zero and the next one is further off.
    private fun busLeft(departure: StopDeparture?): Boolean {
        val minutes = departure?.nextTimes?.firstOrNull()?.minutes
        val left = lastDepartureMinutes == 0 && minutes != null && minutes > 2
        lastDepartureMinutes = minutes
        return left
    }

    private fun nearestStop(fix: Fix, leg: JourneyLeg): Int? {
        val (index, meters) = leg.stops.mapIndexed { i, s -> i to distanceTo(fix, s) }.minByOrNull { it.second } ?: return null
        return index.takeIf { meters <= ON_LINE_M }
    }

    private fun distanceTo(fix: Fix, stop: JourneyStop) = haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon)

    /**
     * When you get there: what is left of the trip, and while the bus is still to come, its
     * countdown in place of the walk to it if that is longer.
     */
    private fun arrivalAt(progress: JourneyProgress, departure: StopDeparture?, nowMs: Long): Long {
        val parts = progress.parts
        val total = parts.sumOf { it.minutes }
        val left = (total - progress.doneMinutes).coerceAtLeast(0.0)
        val p = phase
        val bus = departure?.nextTimes?.firstOrNull()?.minutes
        val minutes = if ((p is Phase.Walk || p is Phase.Wait) && bus != null) {
            val ride = parts.indexOfFirst { it.leg == p.leg && it.ride }.coerceAtLeast(0)
            val fromBoarding = parts.drop(ride).sumOf { it.minutes }
            maxOf(bus.toDouble(), left - fromBoarding) + fromBoarding
        } else left
        return nowMs + (minutes * 60_000).toLong()
    }

    private fun describe(journey: Journey, fix: Fix?, departure: StopDeparture?, nowMs: Long): JourneySync {
        val title = journey.destination.name
        val base = JourneySync(journey.id, title, "", stepIndex = stepIndex, legIndex = phase.leg.coerceAtLeast(0))
        val minutes = departure?.nextTimes?.firstOrNull()?.minutes
        val busLine = departure?.shortName
        val busColor = departure?.color
        return when (val p = phase) {
            is Phase.Walk -> {
                val leg = journey.legs[p.leg]
                base.copy(
                    stepType = StepType.WALK,
                    step = getString(R.string.journey_walk_to, leg.boarding.name),
                    detail = minutes?.let {
                        val line = busLine ?: leg.lineLabel
                        when {
                            it <= 0 -> getString(R.string.journey_line_here, line)
                            isClockTime(it) -> getString(R.string.journey_line_at, line, clockAt(nowMs, it))
                            else -> getString(R.string.journey_line_in, line, getString(R.string.eta_minutes, it))
                        }
                    }
                        ?: fix?.let { getString(R.string.journey_walk_minutes, (distanceTo(it, leg.boarding) / WALK_SPEED).roundToInt().coerceAtLeast(1)) },
                    line = busLine ?: leg.lines.first(),
                    color = busColor ?: leg.colors.first(),
                    arrivalAt = minutes?.let { nowMs + it * 60_000L },
                )
            }
            is Phase.Wait -> {
                val leg = journey.legs[p.leg]
                base.copy(
                    stepType = StepType.WAIT,
                    step = getString(R.string.journey_wait_for, busLine ?: leg.lineLabel),
                    detail = minutes?.let { arrivingText(this, it, nowMs) },
                    line = busLine ?: leg.lines.first(),
                    color = busColor ?: leg.colors.first(),
                    arrivalAt = minutes?.let { nowMs + it * 60_000L },
                    alert = minutes != null && minutes <= 1,
                )
            }
            is Phase.Ride -> {
                val leg = journey.legs[p.leg]
                val left = (leg.stops.lastIndex - rideStop).coerceAtLeast(1)
                base.copy(
                    stepType = StepType.RIDE,
                    stopsLeft = left,
                    totalStops = leg.stops.lastIndex,
                    step = getString(R.string.journey_get_off_at, leg.alighting.name),
                    detail = if (left == 1) getString(R.string.journey_next_stop) else resources.getQuantityString(R.plurals.journey_stops_left, left, left),
                    line = busLine ?: leg.lines.first(),
                    color = busColor ?: leg.colors.first(),
                    alert = left == 1,
                )
            }
            Phase.WalkEnd -> base.copy(
                stepType = StepType.WALK_END,
                step = getString(R.string.journey_walk_to, journey.destination.name),
                detail = fix?.let { getString(R.string.journey_walk_minutes, (distanceTo(it, journey.destination) / WALK_SPEED).roundToInt().coerceAtLeast(1)) },
            )
            Phase.Done -> base.copy(stepType = StepType.ARRIVED, step = getString(R.string.journey_arrived))
        }
    }

    // Done so far, out of the whole trip, in minutes: the walks at walking pace, the rides as planned.
    private fun progress(journey: Journey, fix: Fix?): JourneyProgress {
        val parts = JourneyProgress.parts(journey)
        val done = when (val p = phase) {
            is Phase.Walk -> {
                val walk = parts.indexOfFirst { it.leg == p.leg && !it.ride }
                val part = parts.getOrNull(walk)
                val fraction = if (fix != null && walkStartM > 0) 1 - distanceTo(fix, journey.legs[p.leg].boarding) / walkStartM else 0.0
                parts.take(walk.coerceAtLeast(0)).sumOf { it.minutes } + (part?.minutes ?: 0.0) * fraction.coerceIn(0.0, 1.0)
            }
            is Phase.Wait -> parts.takeWhile { !(it.leg == p.leg && it.ride) }.sumOf { it.minutes }
            is Phase.Ride -> {
                val ride = parts.indexOfFirst { it.leg == p.leg && it.ride }
                val stops = journey.legs[p.leg].stops.lastIndex.coerceAtLeast(1)
                parts.take(ride).sumOf { it.minutes } + parts[ride].minutes * rideStop / stops
            }
            Phase.WalkEnd -> parts.dropLast(1).sumOf { it.minutes }
            Phase.Done -> parts.sumOf { it.minutes }
        }
        return JourneyProgress(parts, done, phase is Phase.Ride)
    }

    private fun finish(journey: Journey) {
        if (ended) return
        ended = true
        JourneyManager.publish(
            this,
            JourneySync(journey.id, journey.destination.name, getString(R.string.journey_arrived), stepIndex = stepIndex + 1, end = true, stepType = StepType.ARRIVED, progress = 1f),
        )
        JourneyNotifications.arrived(this, journey)
        if (JourneyManager.state.value?.id == journey.id) {
            JourneyManager.mutableState.value = null
            JourneyManager.mutableJourney.value = null
            JourneyManager.navigating.value = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        job?.cancel()
    }

    companion object {
        const val EXTRA_JOURNEY = "journey"
    }
}

private fun StopInfo.only(routeIds: List<Int>) = copy(shapesInfo = shapesInfo.filter { it.routeId in routeIds })
