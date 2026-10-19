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
import online.bus.bmarian.conexiuniclujmobile.data.LiveState
import online.bus.bmarian.conexiuniclujmobile.data.LiveStream
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.StopDeparture
import online.bus.bmarian.conexiuniclujmobile.logic.WALK_SPEED
import online.bus.bmarian.conexiuniclujmobile.logic.stopDepartures
import online.bus.bmarian.conexiuniclujmobile.logic.haversineMeters
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TICK_MS = 5_000L
private const val STOP_INFO_REFRESH_MS = 120_000L
private const val ARRIVED_M = 50.0
private const val GIVE_UP_MS = 4 * 60 * 60_000L
private const val CUE_WAIT_MS = 10_000L

private sealed interface Phase {
    val leg: Int
    data class Walk(override val leg: Int) : Phase
    data class Wait(override val leg: Int) : Phase
    data class Ride(override val leg: Int) : Phase
    data object WalkEnd : Phase { override val leg = -1 }
    data object Done : Phase { override val leg = -1 }
}

class JourneyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var journey: Journey? = null
    private var phase: Phase = Phase.Walk(0)
    private var stepIndex = 0
    private var alerted = false
    private var ended = false
    private var rideStop = 0
    private var rideStartedAt = 0L
    private var lastDepartureMinutes: Int? = null
    private var walkEndStartedAt = 0L
    private var walkStartM = 0.0
    private val motion = Motion()
    private var ride: RideTracker? = null
    private var awayInARow = 0
    private var awayFix: Fix? = null
    private var atStopAt = 0L
    private val legIndex = MutableStateFlow(0)
    private var voice: JourneyVoice? = null
    private var cue: Phase? = null
    private var cueSince = 0L
    private var cueAfterRide: Int? = null

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
            motion.reset()
            legIndex.value = 0
            cue = Phase.Walk(0)
            cueSince = System.currentTimeMillis()
            cueAfterRide = null
        }
        if (voice == null) voice = JourneyVoice(this)
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
        voice?.close()
        val current = journey
        if (current != null && !ended) {
            JourneyManager.publish(this, JourneySync(current.id, current.destination.name, "", end = true))
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
                result.lastLocation?.let {
                    trySend(Fix(it.latitude, it.longitude, it.accuracy, it.bearing.takeIf { _ -> it.hasBearing() }, it.speed.takeIf { _ -> it.hasSpeed() }))
                }
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
        launch { Services.settings.journeyVoice.collect { on -> if (!on) voice?.stop() } }
        launch {
            while (true) {
                delay(TICK_MS)
                clock.value = System.currentTimeMillis()
            }
        }
        launch {
            legIndex.collectLatest { i ->
                departure.value = null
                val leg = journey.legs.getOrNull(i) ?: return@collectLatest
                val repository = Services.repository
                val info = MutableStateFlow(repository.cachedStopInfo(leg.boarding.id, journey.city)?.only(leg.routeIds))
                val live = MutableStateFlow<LiveState?>(null)
                launch {
                    while (true) {
                        runCatching { info.value = repository.stopInfo(leg.boarding.id, journey.city).only(leg.routeIds) }
                        delay(STOP_INFO_REFRESH_MS)
                    }
                }
                launch { LiveStream.live(listOf(leg.boarding.id), emptyList(), journey.city).collect { live.value = it } }
                combine(info, live, clock) { stop, state, nowMs -> Triple(stop, state, nowMs) }.collect { (stop, state, nowMs) ->
                    if (stop == null) return@collect
                    departure.value = withContext(Dispatchers.Default) {
                        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), CLUJ_ZONE)
                        stopDepartures(stop, state, now, nowMs)
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
        fix?.let { motion.update(it, nowMs, journey.legs.getOrNull(phase.leg)?.let(::ridePace)) }
        phase = advance(journey, phase, fix, departure, nowMs, skip)
        if (phase != before) {
            stepIndex++
            alerted = false
            motion.reset()
            awayInARow = 0
            if (phase is Phase.Ride && before !is Phase.Ride) {
                rideStop = 0
                ride = null
                rideStartedAt = nowMs
            }
            if (phase is Phase.WalkEnd) walkEndStartedAt = nowMs
            if (phase is Phase.Walk) walkStartM = fix?.let { distanceTo(it, journey.legs[phase.leg].boarding) } ?: 0.0
            if (phase.leg != before.leg) atStopAt = 0
            if (phase.leg >= 0) legIndex.value = phase.leg
            cue = phase
            cueSince = nowMs
            cueAfterRide = (before as? Phase.Ride)?.leg
        }
        if (phase == Phase.Done) {
            if (before != Phase.Done) voice?.say(listOfNotNull(gotOffText(journey), getString(R.string.voice_arrived)).joinToString(" "))
            return finish(journey)
        }
        val described = describe(journey, fix, departure, nowMs)
        val progress = progress(journey, fix)
        val sync = described.copy(
            progress = (progress.doneMinutes / progress.parts.sumOf { it.minutes }.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
            etaAt = arrivalAt(journey, progress, fix, departure, nowMs),
            lat = fix?.lat?.let { Math.round(it * 1e5) / 1e5 },
            lon = fix?.lon?.let { Math.round(it * 1e5) / 1e5 },
            bearing = fix?.bearing,
        )
        val alert = sync.alert && !alerted
        if (alert) {
            alerted = true
            stepIndex++
        }
        announce(journey, fix, departure, nowMs, alert)
        val shown = sync.copy(stepIndex = stepIndex)
        JourneyManager.publish(this, shown)
        getSystemService(NotificationManager::class.java)
            .notify(JOURNEY_NOTIFICATION_ID, JourneyNotifications.ongoing(this, journey, shown, progress, alert))
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
                    fix != null && distanceTo(fix, leg.boarding) <= max(AT_STOP_M, fix.accuracyMeters.toDouble()) -> Phase.Wait(phase.leg).also { atStopAt = nowMs }
                    fix != null && leftOnBus(fix, leg) && motion.riding(fix, nowMs, ridePace(leg)) -> Phase.Ride(phase.leg)
                    fix == null && busLeft(departure) -> Phase.Ride(phase.leg)
                    else -> phase
                }
            }
            is Phase.Wait -> {
                val leg = journey.legs[phase.leg]
                // One fix away from the stop can be the GPS wandering while you wait.
                if (fix != null && fix != awayFix) {
                    awayFix = fix
                    awayInARow = if (leftOnBus(fix, leg)) awayInARow + 1 else 0
                    if (distanceTo(fix, leg.boarding) <= max(AT_STOP_M, fix.accuracyMeters.toDouble())) atStopAt = nowMs
                }
                when {
                    skip -> Phase.Ride(phase.leg)
                    awayInARow >= 2 -> Phase.Ride(phase.leg)
                    fix == null && busLeft(departure) -> Phase.Ride(phase.leg)
                    else -> phase
                }
            }
            is Phase.Ride -> {
                val leg = journey.legs[phase.leg]
                val last = leg.stops.lastIndex
                // From the last time you were seen at the stop: the bus may be well on its way by now.
                val tracker = ride ?: RideTracker(leg, atStopAt.takeIf { it > 0 } ?: rideStartedAt).also { ride = it }
                if (fix != null) {
                    tracker.update(fix, nowMs)
                    rideStop = max(rideStop, tracker.stop)
                } else {
                    val perStop = leg.rideMinutes.takeIf { it > 0 }?.div(last.coerceAtLeast(1)) ?: 2.0
                    rideStop = max(rideStop, ((nowMs - rideStartedAt) / 60_000.0 / perStop).toInt().coerceAtMost(last))
                }
                val there = fix != null && tracker.there
                val gotOff = fix != null && motion.walking() && farOffLine(fix, leg)
                if (skip || there || gotOff || rideStop >= last) afterLeg(phase.leg) else phase
            }
            Phase.WalkEnd -> {
                val walkMin = fix?.let { distanceTo(it, journey.destination) / WALK_SPEED } ?: 0.0
                when {
                    skip -> Phase.Done
                    fix != null && distanceTo(fix, journey.destination) <= max(ARRIVED_M, fix.accuracyMeters.toDouble()) -> Phase.Done
                    nowMs - walkEndStartedAt > (walkMin * 2 + 10) * 60_000 -> Phase.Done
                    else -> phase
                }
            }
            Phase.Done -> phase
        }
    }

    private fun busLeft(departure: StopDeparture?): Boolean {
        val minutes = departure?.nextTimes?.firstOrNull()?.minutes
        val left = lastDepartureMinutes == 0 && minutes != null && minutes > 2
        lastDepartureMinutes = minutes
        return left
    }

    /** The planned run nearest the bus being waited for or ridden, later by however late it is. */
    private fun plannedArrival(journey: Journey, departure: StopDeparture?, nowMs: Long): Long? {
        val p = phase
        if (journey.runs.isEmpty() || (p !is Phase.Walk && p !is Phase.Wait && p !is Phase.Ride)) return null
        val riding = p is Phase.Ride
        val bus = if (riding) rideStartedAt else departure?.nextTimes?.firstOrNull()?.let { nowMs + it.minutes * 60_000L } ?: return null
        val best = journey.runs.filter { it.departs.size > p.leg }.minByOrNull { abs(it.departs[p.leg] * 1000 - bus) } ?: return null
        val late = if (riding) 0L else max(0L, bus - best.departs[p.leg] * 1000)
        return best.arrive * 1000 + late
    }

    private fun arrivalAt(journey: Journey, progress: JourneyProgress, fix: Fix?, departure: StopDeparture?, nowMs: Long): Long {
        plannedArrival(journey, departure, nowMs)?.let { return it }
        val parts = progress.parts
        val total = parts.sumOf { it.minutes }
        val p = phase
        // Off the bus early, the walk left is longer than the planned one.
        val walkLeft = if (p == Phase.WalkEnd && fix != null) distanceTo(fix, journey.destination) / WALK_SPEED else 0.0
        val left = maxOf(total - progress.doneMinutes, walkLeft, 0.0)
        val bus = departure?.nextTimes?.firstOrNull()?.minutes
        val minutes = if ((p is Phase.Walk || p is Phase.Wait) && bus != null) {
            val ride = parts.indexOfFirst { it.leg == p.leg && it.ride }.coerceAtLeast(0)
            val fromBoarding = parts.drop(ride).sumOf { it.minutes }
            maxOf(bus.toDouble(), left - fromBoarding) + fromBoarding
        } else left
        return nowMs + (minutes * 60_000).toLong()
    }

    private fun announce(journey: Journey, fix: Fix?, departure: StopDeparture?, nowMs: Long, alert: Boolean) {
        val voice = voice ?: return
        val p = cue
        if (p != null) {
            val leg = journey.legs.getOrNull(p.leg)
            val bus = departure?.takeIf { leg != null && it.tripId in leg.tripIds }
            val waitsForBus = p is Phase.Walk || p is Phase.Wait
            if (!waitsForBus || bus != null || nowMs - cueSince >= CUE_WAIT_MS) {
                cue = null
                val step = if (alert && (p is Phase.Wait || p is Phase.Ride)) null else spokenStep(journey, p, fix, bus, nowMs)
                val text = listOfNotNull(gotOffText(journey), step).joinToString(" ")
                if (text.isNotEmpty()) voice.say(text)
            }
        }
        if (!alert) return
        when (val now = phase) {
            is Phase.Wait -> spokenStep(journey, now, fix, departure, nowMs)?.let(voice::say)
            is Phase.Ride -> voice.say(
                getString(R.string.voice_get_off_soon, spokenName(journey.legs[now.leg].alighting.name)) + " " + getString(R.string.voice_next_stop),
            )
            else -> {}
        }
    }

    private fun gotOffText(journey: Journey): String? =
        cueAfterRide?.let { getString(R.string.voice_get_off_at, spokenName(journey.legs[it].alighting.name)) }

    /** What to do, then how long or when. */
    private fun spokenStep(journey: Journey, p: Phase, fix: Fix?, bus: StopDeparture?, nowMs: Long): String? = when (p) {
        is Phase.Walk -> {
            val leg = journey.legs[p.leg]
            listOf(
                getString(R.string.voice_walk_to, spokenName(leg.boarding.name)),
                walkText(fix?.let { distanceTo(it, leg.boarding) / WALK_SPEED } ?: leg.walkMinutes),
            ).joinToString(" ")
        }
        is Phase.Wait -> listOfNotNull(
            getString(R.string.voice_take, bus?.shortName ?: journey.legs[p.leg].lines.first()),
            departsText(bus, nowMs),
        ).joinToString(" ")
        is Phase.Ride -> {
            val leg = journey.legs[p.leg]
            val stops = (leg.stops.lastIndex - rideStop).coerceAtLeast(1)
            listOf(
                getString(R.string.voice_ride_to, spokenName(leg.alighting.name)),
                getString(R.string.voice_ride_detail, count(R.plurals.voice_stops, stops, feminine = true), minutesText(leg.rideMinutes)),
            ).joinToString(" ")
        }
        Phase.WalkEnd -> listOf(
            getString(R.string.voice_walk_to, spokenName(journey.destination.name)),
            walkText(fix?.let { distanceTo(it, journey.destination) / WALK_SPEED } ?: journey.walkEndMinutes),
        ).joinToString(" ")
        Phase.Done -> null
    }

    private fun walkText(minutes: Double) = getString(R.string.voice_about, minutesText(minutes))

    private fun minutesText(minutes: Double) = count(R.plurals.voice_minutes, minutes.roundToInt().coerceAtLeast(1), feminine = false)

    private fun departsText(bus: StopDeparture?, nowMs: Long): String? {
        val minutes = bus?.nextTimes?.firstOrNull()?.minutes ?: return null
        return when {
            minutes <= 0 -> getString(R.string.voice_departs_now)
            isClockTime(minutes) -> getString(R.string.voice_departs_at, spokenClock(nowMs, minutes))
            else -> getString(R.string.voice_departs_in, count(R.plurals.voice_minutes, minutes, feminine = false))
        }
    }

    // Romanian voices read digits as masculine ("doi"), so Romanian counts are written out.
    private fun count(id: Int, n: Int, feminine: Boolean): String =
        resources.getQuantityString(id, n, if (english()) "$n" else romanianNumber(n, feminine))

    private fun spokenClock(nowMs: Long, minutes: Int): String {
        if (english()) return clockAt(nowMs, minutes)
        val at = Instant.ofEpochMilli(nowMs + minutes * 60_000L).atZone(CLUJ_ZONE)
        return romanianClock(at.hour, at.minute)
    }

    private fun english() = Services.settings.locale.value == "en"

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
