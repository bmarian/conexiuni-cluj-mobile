package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import online.bus.bmarian.conexiuniclujmobile.ui.home.RemoveZone

class Removal<K>(val onStart: () -> Unit, val onRemove: (K) -> Unit, val ghost: @Composable (K) -> Unit)

@Stable
class ReorderState<K : Any>(
    private val keys: () -> List<K>,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val removal: () -> Removal<K>? = { null },
) {
    internal val bounds = mutableStateMapOf<K, Rect>()
    var dragging by mutableStateOf<K?>(null)
        private set
    internal var offset by mutableStateOf(Offset.Zero)
    private var origin = Offset.Zero
    private var finger = Offset.Zero
    internal val removable get() = removal() != null

    internal fun start(key: K, at: Offset) {
        dragging = key
        offset = Offset.Zero
        origin = bounds[key]?.topLeft ?: Offset.Zero
        finger = at
        val removal = removal() ?: return
        val rect = bounds[key] ?: return
        removal.onStart()
        RemoveZone.begin(rect) { removal.ghost(key) }
    }

    internal fun drag(delta: Offset) {
        val key = dragging ?: return
        offset += delta
        val size = bounds[key]?.size ?: return
        if (removable) {
            RemoveZone.move(Rect(origin + offset, size), origin + finger + offset)
            if (RemoveZone.over) return
        }
        val center = origin + Offset(size.width / 2, size.height / 2) + offset
        val list = keys()
        val from = list.indexOf(key)
        val target = list.indexOfFirst { other -> other != key && bounds[other]?.contains(center) == true }
        if (from < 0 || target < 0) return
        onMove(from, target)
    }

    internal fun relaid(key: K, rect: Rect) {
        if (key != dragging) return
        val moved = rect.topLeft - origin
        if (moved != Offset.Zero) {
            origin = rect.topLeft
            offset -= moved
        }
    }

    internal fun end(dropped: Boolean = true) {
        val key = dragging
        dragging = null
        offset = Offset.Zero
        val removal = removal() ?: return
        if (RemoveZone.endOverBar() && dropped && key != null) removal.onRemove(key)
    }
}

@Composable
fun <K : Any> rememberReorderState(keys: List<K>, removal: Removal<K>? = null, onMove: (from: Int, to: Int) -> Unit): ReorderState<K> {
    val currentKeys by rememberUpdatedState(keys)
    val currentMove by rememberUpdatedState(onMove)
    val currentRemoval by rememberUpdatedState(removal)
    return remember { ReorderState({ currentKeys }, { a, b -> currentMove(a, b) }, { currentRemoval }) }
}

fun <K : Any> Modifier.reorderItem(state: ReorderState<K>, key: K): Modifier = this
    .onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        state.bounds[key] = rect
        state.relaid(key, rect)
    }
    .zIndex(if (state.dragging == key) 1f else 0f)
    .graphicsLayer {
        if (state.dragging == key && state.removable) alpha = 0.3f
        else if (state.dragging == key) {
            translationX = state.offset.x
            translationY = state.offset.y
            scaleX = 1.03f
            scaleY = 1.03f
            shadowElevation = 12f
        }
    }

@Composable
fun <K : Any> Modifier.longPressDrag(state: ReorderState<K>, key: K): Modifier {
    val haptics = LocalHapticFeedback.current
    return reorderDrag(state, key, afterLongPress = true) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
}

fun <K : Any> Modifier.dragToReorder(state: ReorderState<K>, key: K): Modifier = reorderDrag(state, key, afterLongPress = false) {}

private fun <K : Any> Modifier.reorderDrag(state: ReorderState<K>, key: K, afterLongPress: Boolean, onStart: () -> Unit) = pointerInput(key) {
    val start = { at: Offset ->
        onStart()
        state.start(key, at)
    }
    val end = { state.end() }
    val cancel = { state.end(dropped = false) }
    val move = { change: PointerInputChange, delta: Offset ->
        change.consume()
        state.drag(delta)
    }
    try {
        if (afterLongPress) detectDragGesturesAfterLongPress(start, end, cancel, move) else detectDragGestures(start, end, cancel, move)
    } finally {
        if (state.dragging == key) state.end(dropped = false)
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
