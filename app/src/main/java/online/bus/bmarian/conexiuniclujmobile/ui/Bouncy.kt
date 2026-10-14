package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The app's springs: presses squash, toggles pop, values roll. */
object Bouncy {
    val press = spring<Float>(dampingRatio = 0.32f, stiffness = 650f)
    val pop = spring<Float>(dampingRatio = 0.26f, stiffness = Spring.StiffnessMedium)
    val slide = spring<Float>(dampingRatio = 0.58f, stiffness = 340f)
    val land = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

/** Squashes while [source] is pressed and springs back on release. */
fun Modifier.squish(source: InteractionSource, pressed: Float = 0.92f): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) pressed else 1f, Bouncy.press, label = "squish")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A click (and optional long press) that squashes like a button. */
fun Modifier.squishClickable(
    enabled: Boolean = true,
    pressed: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    squish(source, pressed).combinedClickable(
        interactionSource = source,
        indication = ripple(),
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

private const val CASCADE_ITEMS = 12

/** Rises into place after [index] × a beat whenever [key] changes, so lists arrive as a cascade. Rows further down just appear. */
fun Modifier.cascade(index: Int, key: Any?): Modifier = if (index >= CASCADE_ITEMS) this else composed {
    val rise = with(LocalDensity.current) { 28.dp.toPx() }
    val tag = key.toString()
    // Saved with the list item, so scrolling back doesn't replay it.
    var played by rememberSaveable { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(if (played == tag) 1f else 0f) }
    LaunchedEffect(tag) {
        if (played == tag) return@LaunchedEffect
        played = tag
        progress.snapTo(0f)
        delay(index * 28L)
        progress.animateTo(1f, Bouncy.land)
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * rise
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
    }
}

/** An icon that pops, tilts and throws a ring of sparks when [on] turns true. */
@Composable
fun PopIcon(
    icon: ImageVector,
    on: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == shown) return@LaunchedEffect
        shown = on
        if (on) {
            burst.snapTo(0f)
            pop.snapTo(0.25f)
            coroutineScope {
                launch { burst.animateTo(1f, tween(520)) }
                pop.animateTo(1f, Bouncy.pop)
            }
        } else {
            pop.snapTo(0.8f)
            pop.animateTo(1f, Bouncy.pop)
        }
    }
    Icon(
        icon,
        contentDescription,
        modifier
            .drawBehind {
                val b = burst.value
                if (b >= 1f) return@drawBehind
                val reach = size.minDimension * (0.55f + 0.75f * b)
                val dot = size.minDimension * 0.11f * (1f - b)
                for (i in 0 until 8) {
                    val angle = (i / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    drawCircle(
                        tint.copy(alpha = (1f - b) * tint.alpha),
                        dot,
                        center + Offset(cos(angle) * reach, sin(angle) * reach),
                    )
                }
            }
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * -55f
            },
        tint = tint,
    )
}

/** An up arrow that shoots out the top and springs back in from the bottom, over and over. Clip its parent. */
@Composable
fun RisingArrow(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            lift.animateTo(-1f, tween(240, easing = FastOutLinearInEasing))
            lift.snapTo(1f)
            lift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f))
        }
    }
    Icon(Icons.Rounded.ArrowUpward, null, modifier.graphicsLayer { translationY = lift.value * size.height }, tint = tint)
}

/** Text that rolls up to its new value. */
@Composable
fun Ticker(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        text,
        modifier,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut()) using SizeTransform(clip = false)
        },
        label = "ticker",
    ) { value ->
        Text(value, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
    }
}

/**
 * Picks one option. A single pill slides to the choice, stretching as it travels and settling with a
 * wobble, so the selection flows from one option to the next.
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    icon: ((T) -> ImageVector)? = null,
    label: @Composable (T) -> Unit,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) { position.animateTo(index.toFloat(), Bouncy.slide) }
    var width by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .padding(4.dp)
            .onSizeChanged { width = it.width },
    ) {
        val segment = if (options.isEmpty()) 0f else width / options.size.toFloat()
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(LocalDensity.current) { segment.toDp() })
                .graphicsLayer {
                    translationX = position.value * segment
                    // Stretch along the travel and flatten a touch, by how fast it's moving.
                    val speed = abs(position.velocity)
                    scaleX = 1f + (speed / 7f).coerceAtMost(0.45f)
                    scaleY = 1f - (speed / 40f).coerceAtMost(0.12f)
                }
                .clip(CircleShape)
                .background(scheme.primary),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val checked = i == index
                val on = enabled(option)
                val color by animateColorAsState(if (checked) scheme.onPrimary else scheme.onSurfaceVariant, label = "choice")
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .squish(press, 0.9f)
                        .selectable(checked, enabled = on, role = Role.RadioButton, interactionSource = press, indication = ripple()) { onSelect(option) }
                        .alpha(if (on) 1f else 0.38f),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (icon != null) {
                                    PopIcon(icon(option), checked, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                label(option)
                            }
                        }
                    }
                }
            }
        }
    }
}
