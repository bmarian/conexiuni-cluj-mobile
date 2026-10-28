package online.bus.bmarian.conexiuniclujmobile.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

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

/** The system's animator duration scale, public only from Android 13. */
fun animatorDurationScale(context: Context): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.getDurationScale()
    else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
