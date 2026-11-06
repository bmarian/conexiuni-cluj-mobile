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

@Stable
class ReorderState<K : Any>(private val keys: () -> List<K>, private val onMove: (from: Int, to: Int) -> Unit) {
    internal val bounds = mutableStateMapOf<K, Rect>()
    var dragging by mutableStateOf<K?>(null)
        private set
    internal var offset by mutableStateOf(Offset.Zero)
    private var origin = Offset.Zero

    internal fun start(key: K) {
        dragging = key
        offset = Offset.Zero
        origin = bounds[key]?.topLeft ?: Offset.Zero
    }

    internal fun drag(delta: Offset) {
        val key = dragging ?: return
        offset += delta
        val center = (bounds[key]?.let { origin + Offset(it.width / 2, it.height / 2) } ?: return) + offset
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

    internal fun end() {
        dragging = null
        offset = Offset.Zero
    }
}

@Composable
fun <K : Any> rememberReorderState(keys: List<K>, onMove: (from: Int, to: Int) -> Unit): ReorderState<K> {
    val currentKeys by rememberUpdatedState(keys)
    val currentMove by rememberUpdatedState(onMove)
    return remember { ReorderState({ currentKeys }, { a, b -> currentMove(a, b) }) }
}

fun <K : Any> Modifier.reorderItem(state: ReorderState<K>, key: K): Modifier = this
    .onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        state.bounds[key] = rect
        state.relaid(key, rect)
    }
    .zIndex(if (state.dragging == key) 1f else 0f)
    .graphicsLayer {
        if (state.dragging == key) {
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
    val start = { _: Offset ->
        onStart()
        state.start(key)
    }
    val end = { state.end() }
    val move = { change: PointerInputChange, delta: Offset ->
        change.consume()
        state.drag(delta)
    }
    try {
        if (afterLongPress) detectDragGesturesAfterLongPress(start, end, end, move) else detectDragGestures(start, end, end, move)
    } finally {
        if (state.dragging == key) state.end()
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
