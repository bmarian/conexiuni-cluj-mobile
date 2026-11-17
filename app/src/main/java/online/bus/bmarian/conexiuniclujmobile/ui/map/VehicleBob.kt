package online.bus.bmarian.conexiuniclujmobile.ui.map

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.BOB_MS
import online.bus.bmarian.conexiuniclujmobile.ui.doodle.BOB_STEP_MS
import kotlin.math.PI
import kotlin.math.sin

/** [apply] gets two lifts in pixels, half a bob apart. */
private const val HALF = 0.5f

internal class VehicleBob(private val liftPx: Float, private val apply: (Float, Float) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var since = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val t = (SystemClock.uptimeMillis() - since) % BOB_MS / BOB_MS.toFloat()
            apply(lift(t), lift((t + HALF) % 1f))
            handler.postDelayed(this, BOB_STEP_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        since = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        apply(0f, 0f)
    }

    private fun lift(t: Float) = -liftPx * sin(PI * t).toFloat()
}
