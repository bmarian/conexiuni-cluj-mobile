package online.bus.bmarian.conexiuniclujmobile.ui

import online.bus.bmarian.conexiuniclujmobile.logic.LONG_PACE
import online.bus.bmarian.conexiuniclujmobile.logic.paced
import online.bus.bmarian.conexiuniclujmobile.logic.pacedStiffness
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

const val RIPPLE_MS = (870 * LONG_PACE).toInt()

object Bouncy {
    val fastSize = spring<IntSize>(dampingRatio = 0.6f, stiffness = pacedStiffness(1980f))
    val press = spring<Float>(dampingRatio = 0.32f, stiffness = pacedStiffness(860f))
    val pop = spring<Float>(dampingRatio = 0.26f, stiffness = pacedStiffness(1980f))
    val slide = spring<Float>(dampingRatio = 0.58f, stiffness = pacedStiffness(450f))
    val land = spring<Float>(dampingRatio = 0.55f, stiffness = pacedStiffness(530f))
}

class Pop {
    val x = Animatable(1f)
    val y = Animatable(1f)

    // A caller cancelled mid-spring would leave the shape stretched.
    suspend fun from(scaleX: Float, scaleY: Float = scaleX) = withContext(NonCancellable) {
        x.snapTo(scaleX)
        y.snapTo(scaleY)
        launch { x.animateTo(1f, Bouncy.pop) }
        y.animateTo(1f, Bouncy.pop)
    }

    suspend fun squash() = from(1.2f, 0.8f)
}

@Composable
fun rememberPop() = remember { Pop() }

fun Modifier.popped(pop: Pop): Modifier = graphicsLayer {
    scaleX = pop.x.value
    scaleY = pop.y.value
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

/** Rises into place after [index] × a beat whenever [key] changes. Rows past CASCADE_ITEMS just appear. */
fun Modifier.cascade(index: Int, key: Any?): Modifier = if (index >= CASCADE_ITEMS) this else cascadeIn(key, { index })

/** A lazy list's rows on screen as [key] changes rise in turn; rows scrolled to later just appear. */
class Cascade(val key: Any?) {
    private var joined = 0
    private var laidOut = false

    internal fun join(): Int? = if (laidOut) null else joined++

    internal fun settle() {
        laidOut = true
    }
}

@Composable
fun rememberCascade(key: Any?): Cascade = remember(key) { Cascade(key) }

fun Modifier.cascade(cascade: Cascade): Modifier = cascadeIn(cascade.key, cascade::join, cascade::settle)

private fun Modifier.cascadeIn(key: Any?, turn: () -> Int?, settle: () -> Unit = {}): Modifier = composed {
    val rise = with(LocalDensity.current) { 28.dp.toPx() }
    val tag = key.toString()
    // Saved with the list item, so scrolling back doesn't replay it.
    var played by rememberSaveable { mutableStateOf<String?>(null) }
    val beat = remember(tag) { if (played == tag) null else turn() }
    val progress = remember { Animatable(if (played == tag || beat == null) 1f else 0f) }
    LaunchedEffect(tag) {
        // Runs once the list has laid out the rows it starts with.
        settle()
        if (played == tag) return@LaunchedEffect
        played = tag
        if (beat == null) return@LaunchedEffect progress.snapTo(1f)
        progress.snapTo(0f)
        delay(beat * 24L)
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
                launch { burst.animateTo(1f, tween(paced(450))) }
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
            .drawBehind { drawSparks(burst.value, tint) }
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * -55f
            },
        tint = tint,
    )
}

/** PopIcon's ring of sparks at [burst] (0 to 1) around a [box]-sized icon. */
fun DrawScope.drawSparks(burst: Float, tint: Color, box: Float = size.minDimension) {
    if (burst >= 1f) return
    val reach = box * (0.55f + 0.75f * burst)
    val dot = box * 0.11f * (1f - burst)
    for (i in 0 until 8) {
        val angle = (i / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        drawCircle(tint.copy(alpha = (1f - burst) * tint.alpha), dot, center + Offset(cos(angle) * reach, sin(angle) * reach))
    }
}

/** An up arrow that shoots out the top and springs back in from the bottom, over and over. Clip its parent. */
@Composable
fun RisingArrow(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            lift.animateTo(-1f, tween(210, easing = FastOutLinearInEasing))
            lift.snapTo(1f)
            lift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 555f))
        }
    }
    Icon(Icons.Rounded.ArrowUpward, null, modifier.graphicsLayer { translationY = lift.value * size.height }, tint = tint)
}

/** Wiggles now and then. */
fun Modifier.buzz(): Modifier = composed {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            angle.animateTo(0f, keyframes {
                durationMillis = 450
                14f at 52
                -14f at 139
                10f at 226
                -8f at 313
                4f at 383
            })
        }
    }
    graphicsLayer { rotationZ = angle.value }
}

private val DIGITS = Regex("""\d+""")

private fun numbers(text: String) = DIGITS.findAll(text).map { it.value.toLong() }.toList()

private fun shrinks(was: String, now: String): Boolean {
    val before = numbers(was)
    val after = numbers(now)
    val i = after.indices.firstOrNull { after[it] != before.getOrNull(it) } ?: return false
    return before.getOrNull(i)?.let { after[i] < it } == true
}

/** Text that rolls to its new value; when the words stay, only the digits that change roll, down when the number shrinks. */
@Composable
fun Ticker(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    var last by remember { mutableStateOf(text) }
    val down = remember(text) { shrinks(last, text) }
    SideEffect { last = text }
    AnimatedContent(
        text,
        modifier,
        contentKey = { it.replace(DIGITS, "#") },
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.5f, stiffness = pacedStiffness(530f))) { it } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = pacedStiffness(530f))) { -it } + fadeOut()) using SizeTransform(clip = false)
        },
        label = "ticker",
    ) { value ->
        if (!DIGITS.containsMatchIn(value)) {
            Text(value, style = style, color = color, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@AnimatedContent
        }
        Row(Modifier.clearAndSetSemantics { this.text = AnnotatedString(value) }) {
            var at = 0
            for (run in DIGITS.findAll(value)) {
                if (run.range.first > at) Text(value.substring(at, run.range.first), style = style, color = color, fontWeight = fontWeight, maxLines = 1)
                Row(Modifier.animateContentSize(Bouncy.fastSize)) {
                    run.value.forEachIndexed { k, digit ->
                        key(run.value.length - k) {
                            AnimatedContent(
                                digit,
                                transitionSpec = {
                                    val from = if (down) -1 else 1
                                    (slideInVertically(spring(dampingRatio = 0.5f, stiffness = pacedStiffness(530f))) { it * from } + fadeIn()) togetherWith
                                        (slideOutVertically(spring(stiffness = pacedStiffness(530f))) { -it * from } + fadeOut()) using
                                            SizeTransform(clip = true)
                                },
                                label = "digit",
                            ) { d ->
                                Text(d.toString(), style = style.copy(fontFeatureSettings = "tnum"), color = color, fontWeight = fontWeight, maxLines = 1)
                            }
                        }
                    }
                }
                at = run.range.last + 1
            }
            if (at < value.length) Text(value.substring(at), style = style, color = color, fontWeight = fontWeight, maxLines = 1)
        }
    }
}

/** A single pill slides to the choice, stretching as it moves. */
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
