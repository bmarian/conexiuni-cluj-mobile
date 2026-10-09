package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

// Set by the nav host so a screen's elements can travel to the next screen.
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A line badge that grows from the card you tapped into the line page's header, so the page
 * reads as that card opened up. [key] names the line and direction on both sides.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBadge(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedBadge.sharedBounds(
            rememberSharedContentState(key),
            animated,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

fun badgeKey(routeId: Int, tripId: String) = "badge-$routeId-$tripId"
