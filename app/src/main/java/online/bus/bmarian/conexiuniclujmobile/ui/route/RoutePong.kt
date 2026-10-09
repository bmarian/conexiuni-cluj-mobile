package online.bus.bmarian.conexiuniclujmobile.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDarkTheme
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Port of RoutePong.vue.
private const val H = 220f
private const val PW = 10f
private const val PH = 55f
private const val BR = 15f
private const val WIN_SCORE = 5

private class PongGame(val width: Float) {
    val base = width / 1.6f
    var bx = width / 2
    var by = H / 2
    var vx = base
    var vy = 0f
    var lpy = H / 2 - PH / 2
    var rpy = H / 2 - PH / 2
    var scoreL = 0
    var scoreR = 0
    var pause = 0f

    fun launch() {
        bx = width / 2
        by = H / 2
        val angle = ((Math.random() * 0.35 - 0.175) * Math.PI).toFloat()
        val dir = if (Math.random() < 0.5) 1 else -1
        vx = dir * base * cos(angle)
        vy = base * sin(angle)
        pause = 1.2f
    }

    private fun clampVy() {
        val limit = base * 2.2f
        vy = vy.coerceIn(-limit, limit)
    }

    /** "player" or "ai" once someone has five. */
    fun tick(dt: Float): String? {
        val want = by - PH / 2 - lpy
        lpy += want.coerceIn(-base * 0.5f * dt, base * 0.5f * dt)
        lpy = lpy.coerceIn(0f, H - PH)
        if (pause > 0) {
            pause -= dt
            return null
        }
        bx += vx * dt
        by += vy * dt
        if (by - BR < 0) {
            by = BR
            vy = abs(vy)
        }
        if (by + BR > H) {
            by = H - BR
            vy = -abs(vy)
        }
        if (vx < 0 && bx - BR < PW && by > lpy && by < lpy + PH) {
            bx = PW + BR
            vx = abs(vx) * 1.04f
            vy += ((by - lpy) / PH - 0.5f) * base * 2
            clampVy()
        }
        if (vx > 0 && bx + BR > width - PW && by > rpy && by < rpy + PH) {
            bx = width - PW - BR
            vx = -abs(vx) * 1.04f
            vy += ((by - rpy) / PH - 0.5f) * base * 2
            clampVy()
        }
        if (bx + BR < 0) {
            scoreR++
            if (scoreR >= WIN_SCORE) return "player"
            launch()
        }
        if (bx - BR > width) {
            scoreL++
            if (scoreL >= WIN_SCORE) return "ai"
            launch()
        }
        return null
    }
}

@Composable
fun RoutePong(shortName: String, color: String, onExit: () -> Unit) {
    val dark = LocalDarkTheme.current
    val court = if (dark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val ink = if (dark) Color.White else Color(0xFF0F172A)
    val measurer = rememberTextMeasurer()
    // Canvas px; the court is H units tall.
    var canvas by remember { mutableStateOf(Size.Zero) }
    var game by remember { mutableStateOf<PongGame?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val ball = routeColor(color)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(H.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(court),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var y = down.position.y
                        do {
                            game?.let { g -> g.rpy = (y / size.height * H - PH / 2).coerceIn(0f, H - PH) }
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            y = change.position.y
                            change.consume()
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val g = game ?: return@Canvas
            if (frame < 0) return@Canvas
            val unit = size.height / H
            fun px(v: Float) = v * unit
            drawLine(
                ink.copy(alpha = 0.10f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 7.dp.toPx())),
            )
            val scoreStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ink.copy(alpha = if (dark) 0.30f else 0.33f))
            for ((score, cx) in listOf(g.scoreL to size.width / 2 - px(34f), g.scoreR to size.width / 2 + px(34f))) {
                val layout = measurer.measure("$score", scoreStyle)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2, px(8f)))
                for (i in 0 until WIN_SCORE) {
                    drawCircle(
                        ink.copy(alpha = if (i < score) 0.8f else 0.17f),
                        px(3f),
                        Offset(cx + px((i - 2) * 10f), px(48f)),
                    )
                }
            }
            val paddle = ink.copy(alpha = if (dark) 0.88f else 0.72f)
            drawRoundRect(paddle, Offset(0f, px(g.lpy)), Size(px(PW), px(PH)), CornerRadius(px(3f)))
            drawRoundRect(paddle, Offset(size.width - px(PW), px(g.rpy)), Size(px(PW), px(PH)), CornerRadius(px(3f)))
            val label = measurer.measure(shortName, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
            val bw = max(label.size.width + px(18f), px(34f))
            val bh = px(26f)
            val center = Offset(px(g.bx), px(g.by))
            drawRoundRect(ball.copy(alpha = 0.35f), center - Offset(bw / 2 + 3, bh / 2 + 3), Size(bw + 6, bh + 6), CornerRadius(px(9f)))
            drawRoundRect(ball, center - Offset(bw / 2, bh / 2), Size(bw, bh), CornerRadius(px(7f)))
            drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
        }

        LaunchedEffect(canvas, round) {
            if (canvas.width <= 0f || canvas.height <= 0f) return@LaunchedEffect
            val g = PongGame(canvas.width * H / canvas.height)
            g.launch()
            game = g
            over = null
            var last = 0L
            while (over == null) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else min((now - last) / 1_000_000_000f, 0.05f)
                    last = now
                    over = g.tick(dt)
                    frame++
                }
            }
        }

        if (over == null) {
            Surface(
                onClick = onExit,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                shape = CircleShape,
                color = ink.copy(alpha = if (dark) 0.12f else 0.09f),
                contentColor = ink.copy(alpha = 0.5f),
            ) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.w_pong_exit), Modifier.padding(4.dp)) }
        } else {
            val g = game
            Column(
                Modifier.fillMaxSize().background(if (dark) Color(0xB8000000) else Color(0xE6F8FAFC)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(if (over == "player") R.string.w_pong_you_win else R.string.w_pong_ai_wins),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = ink,
                )
                Text("${g?.scoreR ?: 0} – ${g?.scoreL ?: 0}", style = MaterialTheme.typography.titleMedium, color = ink)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = { round++ }) { Text(stringResource(R.string.w_pong_play_again)) }
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onExit) { Text(stringResource(R.string.w_pong_exit)) }
                }
            }
        }
    }
}
