package online.bus.bmarian.conexiuniclujmobile.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.Bouncy
import online.bus.bmarian.conexiuniclujmobile.ui.theme.darkRing
import kotlin.math.roundToInt

object RemoveZone {
    var holding by mutableStateOf(false)
        private set
    var over by mutableStateOf(false)
        private set
    internal var ghost by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    internal var ghostRect by mutableStateOf(Rect.Zero)
        private set
    private var bar = Rect.Zero

    fun begin(rect: Rect, content: @Composable () -> Unit) {
        ghost = content
        ghostRect = rect
        over = false
        holding = true
    }

    fun move(rect: Rect, finger: Offset) {
        ghostRect = rect
        over = bar != Rect.Zero && finger.y <= bar.bottom && finger.x in bar.left..bar.right
    }

    fun endOverBar(): Boolean {
        val dropped = holding && over
        holding = false
        over = false
        ghost = null
        return dropped
    }

    internal fun placeBar(rect: Rect) {
        bar = rect
    }
}

@Composable
fun RemoveBar(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val over = RemoveZone.over
    LaunchedEffect(over) { if (over) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val scale by animateFloatAsState(if (over) 1.04f else 1f, Bouncy.press, label = "bar")
    val color by animateColorAsState(
        if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "barColor",
    )
    val content by animateColorAsState(if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface, label = "barText")
    AnimatedVisibility(
        RemoveZone.holding,
        modifier.statusBarsPadding(),
        enter = fadeIn() + slideInVertically { -it / 4 },
        exit = fadeOut() + slideOutVertically { -it / 4 },
    ) {
        Surface(
            modifier = Modifier.padding(top = 8.dp).searchPill()
                .onGloballyPositioned { RemoveZone.placeBar(it.boundsInRoot()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            color = color,
            contentColor = content,
            shadowElevation = 4.dp,
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Delete, null)
                Text(stringResource(R.string.w_remove_from_favorites), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun DragGhost() {
    val ghost = RemoveZone.ghost ?: return
    val rect = RemoveZone.ghostRect
    val density = LocalDensity.current
    Box(
        Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .graphicsLayer {
                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 12f
                shape = RoundedCornerShape(14.dp)
                clip = true
            },
    ) { ghost() }
}
