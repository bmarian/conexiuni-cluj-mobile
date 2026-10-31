package online.bus.bmarian.conexiuniclujmobile.ui.map

import online.bus.bmarian.conexiuniclujmobile.logic.paced
import android.animation.ValueAnimator
import android.view.Choreographer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import org.maplibre.geojson.Feature

// Port of utils/markerMotion.ts and the marker animations in styles/map.css.

const val ENTER_MS = 190L
const val LEAVE_MS = 140L
const val HIDE_MS = 140L
const val PULSE_MS = 280L
const val SPIDER_OPEN_MS = 280L
const val SPIDER_STEP_MS = 26L
const val SPIDER_CLOSE_MS = 155L

const val ENTER_SCALE = 0.6f
private const val PULSE_SCALE = 1.18f
private const val PULSE_PEAK = 0.4f

val Snappy: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

fun flightMs(distanceDp: Double, zoomDelta: Double): Int = paced((260 + distanceDp * 0.22 + abs(zoomDelta) * 155).coerceIn(305.0, 955.0).toInt())

fun pulseScale(t: Float): Float =
    if (t < PULSE_PEAK) 1 + (PULSE_SCALE - 1) * Snappy.transform(t / PULSE_PEAK)
    else PULSE_SCALE + (1 - PULSE_SCALE) * Snappy.transform((t - PULSE_PEAK) / (1 - PULSE_PEAK))

/** Follows the system's "Remove animations" setting. */
fun motionOn(): Boolean = ValueAnimator.areAnimatorsEnabled()

fun progress(startNanos: Long, nowNanos: Long, ms: Long): Float = ((nowNanos - startNanos) / 1_000_000f / ms).coerceIn(0f, 1f)

internal class FrameLoop(private val frame: (Long) -> Boolean) : Choreographer.FrameCallback {
    private var running = false

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (frame(System.nanoTime())) Choreographer.getInstance().postFrameCallback(this) else running = false
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }
}

internal class MarkerLook(val opacity: Float, val scale: Float, val bodyScale: Float)

internal class MarkerFades {
    private class Hide(val from: Float, val to: Float, val start: Long)

    private val entered = HashMap<String, Long>()
    private val pulsed = HashMap<String, Long>()
    private val hides = HashMap<String, Hide>()
    private class Leaving(val features: List<Feature>, val start: Long, val opacity: Float)

    private val left = LinkedHashMap<String, Leaving>()

    fun enter(key: String, now: Long) {
        left.remove(key)
        if (motionOn()) entered[key] = now
    }

    fun leave(key: String, features: List<Feature>, now: Long) {
        if (motionOn()) left[key] = Leaving(features, now, look(key, now).opacity)
    }

    fun clearLeaving() = left.clear()

    fun leaving(now: Long): List<Feature> = left.values.flatMap { l ->
        val opacity = l.opacity * (1 - EaseIn.transform(progress(l.start, now, LEAVE_MS)))
        l.features.map { copy(it).apply { addNumberProperty("opacity", opacity); addBooleanProperty("gone", true) } }
    }

    fun leavingImages(): Set<String> = left.values.flatMapTo(HashSet()) { l -> l.features.mapNotNull { it.getStringProperty("img") } }

    fun pulse(key: String, now: Long) {
        if (motionOn()) pulsed[key] = now
    }

    fun hide(key: String, hidden: Boolean, now: Long) {
        val to = if (hidden) 0f else 1f
        if ((hides[key]?.to ?: 1f) == to) return
        hides[key] = Hide(hideOpacity(key, now), to, if (motionOn()) now else now - HIDE_MS * 1_000_000)
    }

    fun hidden(key: String) = hides[key]?.to == 0f

    fun forget(keys: Set<String>) {
        entered.keys.retainAll(keys)
        pulsed.keys.retainAll(keys)
        hides.keys.retainAll(keys)
    }

    private fun hideOpacity(key: String, now: Long): Float {
        val h = hides[key] ?: return 1f
        return h.from + (h.to - h.from) * EaseOut.transform(progress(h.start, now, HIDE_MS))
    }

    /** [MarkerLook.scale] also pulses; [MarkerLook.bodyScale] is for parts that only pop in. */
    fun look(key: String, now: Long): MarkerLook {
        val enter = entered[key]?.let { Snappy.transform(progress(it, now, ENTER_MS)) } ?: 1f
        val pop = ENTER_SCALE + (1 - ENTER_SCALE) * enter
        val pulse = pulsed[key]?.let { pulseScale(progress(it, now, PULSE_MS)) } ?: 1f
        return MarkerLook(enter * hideOpacity(key, now), pop * pulse, pop)
    }

    /** Whether this frame still changes anything; what had finished by the previous frame is dropped, so its end gets drawn. */
    fun moving(previous: Long): Boolean {
        entered.values.removeAll { progress(it, previous, ENTER_MS) >= 1f }
        pulsed.values.removeAll { progress(it, previous, PULSE_MS) >= 1f }
        hides.entries.removeAll { (_, h) -> h.to == 1f && progress(h.start, previous, HIDE_MS) >= 1f }
        left.values.removeAll { progress(it.start, previous, LEAVE_MS) >= 1f }
        return entered.isNotEmpty() || pulsed.isNotEmpty() || left.isNotEmpty() || hides.values.any { progress(it.start, previous, HIDE_MS) < 1f }
    }
}

/** Features are rebuilt every frame instead of edited, since the map may still be reading the last ones. */
fun copy(feature: Feature): Feature = Feature.fromGeometry(feature.geometry(), feature.properties()?.deepCopy())
