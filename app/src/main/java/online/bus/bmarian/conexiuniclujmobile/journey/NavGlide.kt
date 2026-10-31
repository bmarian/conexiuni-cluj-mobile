package online.bus.bmarian.conexiuniclujmobile.journey

import online.bus.bmarian.conexiuniclujmobile.data.NAV_LOCATION_MS
import online.bus.bmarian.conexiuniclujmobile.logic.TREND_WEIGHT
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// MapLibre's longest location animation.
private const val GLIDE_MAX_MS = 2_000L
private const val KEYFRAME_MS = 200L
internal const val WALK_ACCEL = 0.3

/** [keys] are metres along the line, evenly spaced over [ms]. */
internal class Glide(val along: Double, val keys: List<Double>, val ms: Long)

/** Constant-speed Kalman filter along a line. */
internal class NavGlide(private val length: Double, private val accel: Double) {
    private var s = 0.0
    private var v = 0.0
    private var ss = 0.0
    private var sv = 0.0
    private var vv = 0.0
    private var seenAt: Long? = null
    private var gapMs = NAV_LOCATION_MS.toDouble()
    private var from = 0.0
    private var to = 0.0
    private var startedAt = 0L
    private var lastingMs = 0L

    private fun shownAt(ms: Long) = if (lastingMs <= 0) to else from + (to - from) * ((ms - startedAt).toDouble() / lastingMs).coerceIn(0.0, 1.0)

    fun update(measured: Double, error: Double, atMs: Long): Glide {
        val noise = max(error, 1.0).let { it * it }
        val seen = seenAt
        val waited = seen?.let { atMs - it }
        val shown: Double
        // Past the longest animation there is nothing to carry on from.
        if (seen == null || waited!! > GLIDE_MAX_MS) {
            shown = if (seen == null) measured else max(shownAt(atMs), measured)
            s = shown
            v = 0.0
            ss = noise
            sv = 0.0
            vv = MAX_BUS_MPS * MAX_BUS_MPS
        } else {
            shown = shownAt(atMs)
            if (waited > 0) {
                gapMs += (waited - gapMs) * TREND_WEIGHT
                predict(waited / 1000.0)
            }
            correct(measured, noise)
        }
        seenAt = atMs
        val ms = min((2 * gapMs).toLong(), GLIDE_MAX_MS)
        val end = min(max(shown, s + v * ms / 1000), max(shown, length))
        val steps = ceil(ms.toDouble() / KEYFRAME_MS).toInt().coerceAtLeast(1)
        from = shown
        to = end
        startedAt = atMs
        lastingMs = ms
        return Glide(shown, (1..steps).map { shown + (end - shown) * it / steps }, ms)
    }

    private fun predict(dt: Double) {
        val shake = accel * accel
        s += v * dt
        ss += dt * (2 * sv + dt * vv) + shake * dt * dt * dt * dt / 4
        sv += dt * vv + shake * dt * dt * dt / 2
        vv += shake * dt * dt
    }

    private fun correct(measured: Double, noise: Double) {
        val weight = ss + noise
        val gainS = ss / weight
        val gainV = sv / weight
        val miss = measured - s
        s += gainS * miss
        v = (v + gainV * miss).coerceIn(0.0, MAX_BUS_MPS)
        vv -= gainV * sv
        sv *= 1 - gainS
        ss *= 1 - gainS
    }
}
