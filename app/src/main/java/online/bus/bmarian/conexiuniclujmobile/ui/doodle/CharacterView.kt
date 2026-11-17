package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.NetworkRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.modeOf
import online.bus.bmarian.conexiuniclujmobile.ui.theme.DoodleFont
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDarkTheme
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle
import online.bus.bmarian.conexiuniclujmobile.ui.theme.PaperInk
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private const val LIFT = 3.5f
private const val FULL_TURN = 360f
private const val WHEEL_MS = 500
private const val PALE = 0.5f
private val MomentWidth = 170.dp
private val CaptionText = 22.sp

/** Draws nothing in the standard look. */
@Composable
fun Character(mode: Mode, color: Color, number: String, mood: Mood, modifier: Modifier = Modifier, flip: Boolean = false) {
    val ink = LocalDoodle.current ?: return
    val dark = LocalDarkTheme.current
    val measurer = rememberTextMeasurer()
    val moving = mood == Mood.Happy || mood == Mood.Wave
    val motion = rememberInfiniteTransition("bob")
    val t by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(BOB_MS.toInt(), easing = LinearEasing)), label = "bob")
    val spin by motion.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(WHEEL_MS, easing = LinearEasing)), label = "wheels")
    val lift = if (moving) LIFT * sin(PI * floor(t * BOB_STEPS) / BOB_STEPS).toFloat() else 0f
    val colors = Characters.Colors(color, ink.outline, ink.window, ink.rim, ink.blob, PaperInk.text)
    Canvas(modifier.aspectRatio(Characters.WIDTH / Characters.HEIGHT).graphicsLayer { alpha = if (mood == Mood.Pale) PALE else 1f }) {
        with(Characters) {
            shadow(dark)
            translate(top = -lift * size.width / Characters.WIDTH) {
                character(mode, colors, flip, mood = mood, spin = if (moving) spin else 0f)
                if (number.isNotEmpty()) number(measurer, mode, number, flip, DoodleFont)
            }
        }
    }
}

@Composable
fun modeColor(mode: Mode): Color {
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    return routeColor(index?.modeColor(mode))
}

@Composable
fun routeMode(routeId: Int): Mode {
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    return index?.route(routeId)?.type?.let(::modeOf) ?: Mode.BUS
}

@Composable
fun routeLook(routeId: Int?): NetworkRoute? {
    val index by Services.repository.network.index.collectAsStateWithLifecycle()
    return routeId?.let { index?.route(it) }
}

@Composable
fun DoodleMoment(mood: Mood, modifier: Modifier = Modifier, mode: Mode = Mode.BUS, caption: String? = null) {
    if (LocalDoodle.current == null) return
    val color = modeColor(mode)
    Column(modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Character(mode, color, "", mood, Modifier.width(MomentWidth))
        caption?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = CaptionText, fontWeight = FontWeight.Bold) }
    }
}
