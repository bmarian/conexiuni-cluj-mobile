package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Drag to reorder, like the website's vuedraggable lists: the item follows the finger and
 * swaps places with whatever it is dragged over.
 */
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

    /** After a swap the item sits somewhere new, so the finger's offset is measured from there. */
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

/** The item itself: tracks where it is and follows the finger while dragged. */
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

/**
 * A handle that starts the drag at once, the website's grip dots. It sits by the screen's
 * edge, so the system's back swipe is told to leave it alone.
 */
@Composable
fun <K : Any> Modifier.dragHandle(state: ReorderState<K>, key: K): Modifier {
    val haptics = LocalHapticFeedback.current
    return systemGestureExclusion().pointerInput(key) {
        try {
            detectDragGestures(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(key)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            ) { change, delta ->
                change.consume()
                state.drag(delta)
            }
        } finally {
            if (state.dragging == key) state.end()
        }
    }
}

/** A long press anywhere on the item starts the drag too. */
@Composable
fun <K : Any> Modifier.longPressDrag(state: ReorderState<K>, key: K): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(key) {
        try {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(key)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            ) { change, delta ->
                change.consume()
                state.drag(delta)
            }
        } finally {
            if (state.dragging == key) state.end()
        }
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
