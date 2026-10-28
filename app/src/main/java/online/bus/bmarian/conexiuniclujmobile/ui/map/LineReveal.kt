package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.content.Context
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import online.bus.bmarian.conexiuniclujmobile.logic.LatLon
import online.bus.bmarian.conexiuniclujmobile.logic.RoutePath
import online.bus.bmarian.conexiuniclujmobile.ui.Bouncy
import online.bus.bmarian.conexiuniclujmobile.ui.animatorDurationScale

private val POP = Bouncy.pop.vectorize(Float.VectorConverter)
private val ZERO = AnimationVector1D(0f)
private val ONE = AnimationVector1D(1f)
private val POP_NANOS = POP.getDurationNanos(ZERO, ONE, ZERO)

/**
 * Port of utils/lineReveal.ts, on the sweep's own progress: stops on the growing lines pop in as its front passes them,
 * their buses once they are drawn. Until [draw] is first called, they stay hidden.
 */
internal class LineReveal(private val context: Context) {
    private var scale = 1f
    private var paths: List<RoutePath> = emptyList()
    private var names: Set<String> = emptySet()
    private var gone: Set<String> = emptySet()
    private var drawn: Float? = null
    private var growing = false
    private var ended: Long? = null
    private val shares = HashMap<LatLon, Float?>()
    private val pops = HashMap<LatLon, Long>()

    fun grow(lines: List<RoutePath>, lineNames: Set<String>) {
        scale = animatorDurationScale(context)
        paths = lines
        names = lineNames
        gone = emptySet()
        drawn = null
        growing = true
        ended = null
        shares.clear()
        pops.clear()
    }

    fun draw(done: Float) {
        if (growing) drawn = done
    }

    /** Buses of growing lines not in [staying] were never shown, so they stay hidden while they leave. */
    fun end(now: Long, staying: Set<String>? = null) {
        if (!growing) return
        growing = false
        drawn = 1f
        ended = now
        if (staying != null) gone = names - staying
    }

    /** Bouncy.pop from nothing, on the animator duration scale the sweep runs on. */
    private fun popScale(start: Long, now: Long): Float {
        val played = ((now - start) / scale).toLong()
        return if (played >= POP_NANOS) 1f else POP.getValueFromNanos(played, ZERO, ONE, ZERO).value
    }

    private fun share(at: LatLon): Float? {
        if (at in shares) return shares[at]
        return paths.mapNotNull { path -> path.locate(at.lat, at.lon)?.let { (it.s / path.length).toFloat() } }.minOrNull().also { shares[at] = it }
    }

    fun stop(at: LatLon, now: Long): Float {
        if (paths.isEmpty()) return 1f
        val share = share(at) ?: return 1f
        val start = pops[at] ?: run {
            val done = drawn ?: return 0f
            if (share > done) return 0f
            now.also { pops[at] = it }
        }
        return popScale(start, now)
    }

    fun vehicle(now: Long, line: String): Float = when {
        line in gone -> 0f
        line !in names -> 1f
        growing -> 0f
        else -> ended?.let { popScale(it, now) } ?: 1f
    }

    /** Whether this frame still changes a scale; once nothing does, every marker is back to full size for good. */
    fun moving(now: Long): Boolean {
        val playing = (pops.values + listOfNotNull(ended)).any { (now - it) / scale < POP_NANOS }
        if (growing && drawn != null || playing) return true
        if (!growing) {
            paths = emptyList()
            names = emptySet()
            gone = emptySet()
            pops.clear()
            ended = null
        }
        return false
    }
}
