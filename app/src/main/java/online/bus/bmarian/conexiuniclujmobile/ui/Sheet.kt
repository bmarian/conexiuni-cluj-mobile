package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SheetSnap { Peek, Half, Full }

private const val HALF_FRACTION = 0.60f
val SHEET_HANDLE = 28.dp
private val PEEK_CONTENT = 64.dp
private val FlingThreshold = 400.dp
private val ReverseSlop = 32.dp

internal val SheetSpring: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 600f)

@Stable
class SheetState(initial: SheetSnap) {
    val anchored = AnchoredDraggableState(initial)
    internal var containerPx by mutableFloatStateOf(0f)
    internal var onUserSettle: ((SheetSnap) -> Unit)? = null
    internal var onUserDrag: (() -> Unit)? = null

    val snap: SheetSnap get() = anchored.settledValue

    /** Read in layout or draw, not composition. */
    val offsetPx: Float get() = anchored.offset.takeUnless { it.isNaN() } ?: containerPx

    fun heightFor(s: SheetSnap): Float {
        val p = anchored.anchors.positionOf(s)
        return if (p.isNaN()) 0f else containerPx - p
    }

    val restingPx: Float get() = heightFor(anchored.targetValue)

    internal val isAtAnchor: Boolean
        get() {
            val p = anchored.anchors.positionOf(anchored.settledValue)
            return p.isNaN() || abs(offsetPx - p) < 0.5f
        }

    internal fun updateAnchors(heightPx: Float, peekPx: Float, half: Boolean = true) {
        containerPx = heightPx
        val halfPx = if (half) heightPx * (1 - HALF_FRACTION) else 0f
        anchored.updateAnchors(
            DraggableAnchors {
                SheetSnap.Full at 0f
                if (half) SheetSnap.Half at halfPx
                SheetSnap.Peek at (heightPx - peekPx).coerceAtLeast(halfPx)
            },
        )
    }

    suspend fun animateTo(target: SheetSnap) = anchored.animateTo(target, SheetSpring)

    internal fun targetFor(velocity: Float, density: Density): SheetSnap {
        val anchors = anchored.anchors
        val offset = offsetPx
        val closest = anchors.closestAnchor(offset) ?: snap
        if (abs(velocity) < with(density) { FlingThreshold.toPx() }) return closest
        val down = velocity > 0
        val rest = anchors.positionOf(snap)
        val slop = with(density) { ReverseSlop.toPx() }
        val against = if (down) offset < rest - slop else offset > rest + slop
        val from = when {
            rest.isNaN() || against -> offset
            down -> maxOf(offset, rest)
            else -> minOf(offset, rest)
        }
        return SheetSnap.entries
            .filter {
                val p = anchors.positionOf(it)
                !p.isNaN() && if (down) p > from + 1 else p < from - 1
            }
            .minByOrNull { abs(anchors.positionOf(it) - from) } ?: closest
    }

    internal suspend fun fling(velocity: Float, behavior: FlingBehavior) = anchored.anchoredDrag {
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                val old = offsetPx
                val anchors = anchored.anchors
                dragTo((old + pixels).coerceIn(anchors.minPosition(), anchors.maxPosition()))
                return offsetPx - old
            }
        }
        with(behavior) { scope.performFling(velocity) }
    }
}

private class SheetFling(private val sheet: SheetState, private val density: Density) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val target = sheet.targetFor(initialVelocity, density)
        if (target != sheet.snap) sheet.onUserSettle?.invoke(target)
        val to = sheet.anchored.anchors.positionOf(target)
        val from = sheet.offsetPx
        animate(from, to, initialVelocity, SheetSpring) { value, _ ->
            // From where the sheet really is: no overshoot, and no drift back after hitting an edge.
            val next = if (to >= from) minOf(value, to) else maxOf(value, to)
            scrollBy(next - sheet.offsetPx)
        }
        return 0f
    }
}

private fun sheetNestedScroll(sheet: SheetState, fling: FlingBehavior, scope: CoroutineScope) = object : NestedScrollConnection {
    // A touch stops a settle in progress.
    private fun grab() {
        if (sheet.anchored.isAnimationRunning) scope.launch { sheet.anchored.anchoredDrag(MutatePriority.UserInput) {} }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        grab()
        return if (available.y < 0) Offset(0f, sheet.anchored.dispatchRawDelta(available.y)) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        val moved = sheet.anchored.dispatchRawDelta(available.y)
        if (moved > 0f) sheet.onUserDrag?.invoke()
        return Offset(0f, moved)
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y < 0 && sheet.offsetPx > sheet.anchored.anchors.minPosition()) {
            sheet.fling(available.y, fling)
            available
        } else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        // Only if this gesture moved the sheet.
        if (!sheet.isAtAnchor) {
            sheet.fling(available.y, fling)
            available
        } else Velocity.Zero
}

/** Drags and flings from the sheet itself or from a list inside it. */
@Composable
internal fun rememberSheetGestures(state: SheetState, interactionSource: MutableInteractionSource? = null): Modifier {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val fling = remember(state, density) { SheetFling(state, density) }
    val scope = rememberCoroutineScope()
    val connection = remember(state, fling, scope) { sheetNestedScroll(state, fling, scope) }
    state.onUserSettle = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    return Modifier
        .nestedScroll(connection)
        .anchoredDraggable(state.anchored, Orientation.Vertical, interactionSource = interactionSource, flingBehavior = fling)
}

@Composable
fun rememberSheetState(initial: SheetSnap = SheetSnap.Half): SheetState {
    var saved by rememberSaveable { mutableStateOf(initial) }
    val state = remember { SheetState(saved) }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.snap }.collect { saved = it }
    }
    return state
}

@Composable
fun BottomSheet(
    state: SheetState,
    modifier: Modifier = Modifier,
    above: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val drags = remember { MutableInteractionSource() }
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    val peekPx = with(density) { (SHEET_HANDLE + PEEK_CONTENT).toPx() } + navigationBar
    state.onUserDrag = {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    var focused by remember { mutableStateOf(false) }
    val imeUp = WindowInsets.ime.getBottom(density) > 0

    Box(
        modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                state.updateAnchors(constraints.maxHeight.toFloat(), peekPx)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    ) {
        if (above != null) {
            Box(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        val h = placeable.height.toFloat()
                        placeable.placeWithLayer(0, (state.offsetPx - h).roundToInt()) {
                            alpha = ((state.offsetPx - h) / h).coerceIn(0f, 1f)
                        }
                    }
                },
            ) { above() }
        }
        Surface(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, state.offsetPx.roundToInt()) }
                .then(rememberSheetGestures(state, drags))
                .onFocusChanged { focused = it.hasFocus },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 6.dp,
            tonalElevation = 1.dp,
        ) {
            Column {
                SheetHandle(state)
                Box(Modifier.weight(1f).imePadding()) { content() }
            }
        }
    }
    LaunchedEffect(imeUp, focused) {
        if (imeUp && focused && state.snap != SheetSnap.Full) state.animateTo(SheetSnap.Full)
    }
    LaunchedEffect(drags) {
        drags.interactions.filterIsInstance<DragInteraction.Start>().collect { state.onUserDrag?.invoke() }
    }
}

@Composable
private fun SheetHandle(state: SheetState) {
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.w_toggle_drawer)
    Box(
        Modifier
            .fillMaxWidth()
            .height(SHEET_HANDLE)
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { state.animateTo(if (state.snap == SheetSnap.Peek) SheetSnap.Half else SheetSnap.Peek) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

enum class SideSnap { Closed, Open }

@Stable
class SideSheetState(initial: SideSnap) {
    val anchored = AnchoredDraggableState(initial)
    internal var widthPx by mutableFloatStateOf(0f)

    val isOpen: Boolean get() = anchored.targetValue == SideSnap.Open
    /** Read in layout or draw. */
    val visiblePx: Float get() = widthPx + (anchored.offset.takeUnless { it.isNaN() } ?: 0f)

    internal fun updateAnchors(width: Float) {
        widthPx = width
        anchored.updateAnchors(DraggableAnchors { SideSnap.Closed at -width; SideSnap.Open at 0f })
    }

    suspend fun toggle() = anchored.animateTo(if (isOpen) SideSnap.Closed else SideSnap.Open, SheetSpring)
}

@Composable
fun rememberSideSheetState(): SideSheetState {
    var saved by rememberSaveable { mutableStateOf(SideSnap.Open) }
    val state = remember { SideSheetState(saved) }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.anchored.settledValue }.collect { saved = it }
    }
    return state
}

@Composable
fun SideSheet(
    state: SideSheetState,
    widthPx: Float,
    draggable: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    state.updateAnchors(widthPx)
    val fling = androidx.compose.foundation.gestures.AnchoredDraggableDefaults.flingBehavior(state.anchored, animationSpec = SheetSpring)
    Surface(
        modifier
            .offset { IntOffset((state.anchored.offset.takeUnless { it.isNaN() } ?: 0f).roundToInt(), 0) }
            .anchoredDraggable(state.anchored, Orientation.Horizontal, enabled = draggable, flingBehavior = fling),
        shape = if (draggable) RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) { content() }
}
