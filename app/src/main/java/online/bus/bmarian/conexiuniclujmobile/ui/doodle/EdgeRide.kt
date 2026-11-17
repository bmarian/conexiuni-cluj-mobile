package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import kotlin.math.sin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.ui.animatorDurationScale
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle
import kotlin.math.roundToInt

private const val RIDE_MS = 1400
private val RideWidth = 96.dp

// Wheel line, as a share of the character's height.
private const val WHEELS = 0.95f
private val Sink = 3.dp

object EdgeRide {
    class Ride(val mode: Mode, val color: Color, val number: String, val leftward: Boolean) {
        val id = System.nanoTime()
    }

    val now = MutableStateFlow<Ride?>(null)
    private var waiting: Ride? = null

    fun start(mode: Mode, color: Color, number: String) {
        waiting = null
        now.value = Ride(mode, color, number, leftward = false)
    }

    /** Held until the map starts drawing the page's line. */
    fun withLine(mode: Mode, color: Color, number: String, leftward: Boolean) {
        waiting = Ride(mode, color, number, leftward)
    }

    fun lineDrawing() {
        val ride = waiting ?: return
        waiting = null
        now.value = ride
    }

    fun forget() {
        waiting = null
    }
}

/** [edge] is the sheet's top in pixels. */
@Composable
fun EdgeRideLayer(edge: () -> Float, modifier: Modifier = Modifier) {
    val ride by EdgeRide.now.collectAsState()
    val current = ride ?: return
    if (LocalDoodle.current == null || animatorDurationScale(LocalContext.current) == 0f) return
    key(current.id) {
        val across = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            across.animateTo(1f, tween(RIDE_MS))
            EdgeRide.now.compareAndSet(current, null)
        }
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val travel = with(density) { (maxWidth + RideWidth * 2).toPx() }
            val start = with(density) { -RideWidth.toPx() }
            val along = { if (current.leftward) 1 - across.value else across.value }
            val above = with(density) { (RideWidth * Characters.HEIGHT / Characters.WIDTH * WHEELS - Sink).toPx() }
            Character(
                current.mode, current.color, current.number, Mood.Happy,
                Modifier.width(RideWidth).offset { IntOffset((start + travel * along()).roundToInt(), (edge() - above).roundToInt()) },
                flip = current.leftward,
            )
        }
    }
}

@Composable
fun RideWithLine(page: Any, mode: Mode?, color: Color, number: String, heading: Double?) {
    val doodle = LocalDoodle.current != null
    LaunchedEffect(page, mode != null, heading != null) {
        if (doodle && mode != null && heading != null) EdgeRide.withLine(mode, color, number, leftward = sin(Math.toRadians(heading)) < 0)
    }
    DisposableEffect(page) { onDispose { EdgeRide.forget() } }
}

@Composable
fun RideOnOpen(page: Any, mode: Mode?, color: Color, number: String) {
    val doodle = LocalDoodle.current != null
    LaunchedEffect(page, mode != null) {
        if (doodle && mode != null) EdgeRide.start(mode, color, number)
    }
}
