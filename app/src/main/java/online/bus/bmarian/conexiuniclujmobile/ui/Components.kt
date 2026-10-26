package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.Mode
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLiveColors

@Composable
fun cityName(): String {
    val city by Cities.current.collectAsStateWithLifecycle()
    val cities by Cities.list.collectAsStateWithLifecycle()
    return cities.firstOrNull { it.slug == city }?.name ?: city
}

@Composable
fun rememberNow(): LocalDateTime {
    val clock by Services.clock.collectAsStateWithLifecycle()
    return remember(clock) { LocalDateTime.ofInstant(Instant.ofEpochMilli(clock), CLUJ_ZONE) }
}

/** Port of formatMinutes. */
@Composable
fun minutesLabel(minutes: Int, now: LocalDateTime): String =
    formatMinutesFromNow(minutes, now, stringResource(R.string.w_now))

@Composable
fun LineBadge(
    shortName: String,
    color: String,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    minWidth: Dp = 40.dp,
    corner: Dp = 8.dp,
) {
    val looks = LocalLineLooks.current
    val morph = rememberMorph(shortName, looks.color(shortName, color), looks.favorite(shortName))
    FavoriteMorph(morph, RoundedCornerShape(corner), modifier.widthIn(min = minWidth).heightIn(min = height)) {
        Text(
            shortName,
            Modifier.padding(horizontal = 6.dp),
            color = Color.White,
            fontSize = (height.value * 0.45f).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

/** When fresh live times last landed. */
val LocalLiveBeat = compositionLocalOf<Long?> { null }

@Composable
fun OnLiveBeat(action: suspend CoroutineScope.() -> Unit) {
    val beat = LocalLiveBeat.current
    var seen by remember { mutableStateOf(beat) }
    LaunchedEffect(beat) {
        if (beat == null || beat == seen) return@LaunchedEffect
        seen = beat
        action()
    }
}

@Composable
fun RealtimeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val parts = remember { List(3) { Animatable(1f) } }
    OnLiveBeat {
        parts.forEach { it.snapTo(0.2f) }
        parts.forEachIndexed { i, part ->
            launch {
                delay(120L + i * 95L)
                part.animateTo(1f, tween(210, easing = LinearEasing))
            }
        }
    }
    Canvas(modifier.size(size)) {
        val stroke = this.size.width * 0.13f
        val corner = Offset(stroke, this.size.height - stroke)
        drawCircle(color.copy(alpha = color.alpha * parts[0].value), stroke * 1.1f, corner)
        for (step in 1..2) {
            val radius = this.size.width * (0.32f + 0.3f * (step - 1))
            drawArc(
                color = color.copy(alpha = color.alpha * parts[step].value),
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(corner.x - radius, corner.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun liveColor(): Color = LocalLiveColors.current.content

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = iconTint)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun SubLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(Modifier.size(56.dp))
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MessageText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun RowIcon(icon: ImageVector, container: Color, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun TimePill(text: String, live: Boolean, modifier: Modifier = Modifier, extra: Boolean = false, due: Boolean = false, mode: Mode = Mode.BUS) {
    val colors = LocalLiveColors.current
    val solid = live && due
    val container by animateColorAsState(
        when {
            solid -> colors.content
            live -> colors.container
            extra -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        if (solid) tween(190, delayMillis = 485, easing = LinearEasing) else snap(),
        label = "pill",
    )
    val content by animateColorAsState(
        when {
            solid -> colors.container
            live -> colors.onContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        if (solid) tween(190, delayMillis = 485, easing = LinearEasing) else snap(),
        label = "pillText",
    )
    val pill = rememberPop()
    val vehicle = rememberPop()
    val arrive = remember { Animatable(1f) }
    var was by remember { mutableStateOf(live to solid) }
    // Only when the time counts down to now, not when a page opens on a bus already due.
    LaunchedEffect(solid) {
        val (wasLive, wasDue) = was
        was = live to solid
        if (!solid || wasDue || !wasLive) return@LaunchedEffect arrive.snapTo(1f)
        arrive.snapTo(0f)
        launch {
            delay(70)
            arrive.animateTo(1f, tween(540, easing = EmphasizedDecelerate))
        }
        launch {
            delay(540)
            pill.from(0.86f)
        }
        delay(610)
        vehicle.squash()
    }
    Row(
        modifier
            .popped(pill)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (live) {
            Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(!solid, enter = fadeIn(), exit = scaleOut(tween(140)) + fadeOut(tween(140))) {
                    RealtimeIcon(content, size = 10.dp)
                }
                if (solid) {
                    Icon(
                        ModeGlyphs.vector(mode),
                        null,
                        Modifier.fillMaxSize().popped(vehicle).graphicsLayer {
                            val k = 0.15f + 0.85f * arrive.value
                            scaleX *= k
                            scaleY *= k
                            alpha = arrive.value.coerceIn(0f, 1f)
                        },
                        tint = content,
                    )
                }
            }
        }
        Ticker(
            text,
            style = TextStyle(fontSize = 13.sp, fontWeight = if (extra) FontWeight.Medium else FontWeight.Bold),
            color = content,
        )
    }
}

@Composable
fun TruncatedText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, maxLines: Int = 1) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Lazy list keys must be unique: repeats get a suffix. */
fun <T> withUniqueKeys(items: List<T>, key: (T) -> String): List<Pair<String, T>> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val k = key(item)
        val n = seen.merge(k, 1, Int::plus)!!
        (if (n == 1) k else "$k#$n") to item
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
