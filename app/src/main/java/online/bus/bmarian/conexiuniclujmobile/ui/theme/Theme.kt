package online.bus.bmarian.conexiuniclujmobile.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

object Palette {
    val Emerald = Color(0xFF10B981)
    val EmeraldLight = Color(0xFF34D399)
    val EmeraldDeep = Color(0xFF047857)
    val Purple = Color(0xFFA855F7)
    val Rose = Color(0xFFF43F5E)
    val Amber = Color(0xFFF59E0B)
    val Sky = Color(0xFF0EA5E9)
    val Blue = Color(0xFF3B82F6)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate700 = Color(0xFF334155)
    val Slate900 = Color(0xFF0F172A)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D5D8B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF354571),
    inversePrimary = Color(0xFFB5C5F9),
    secondary = Color(0xFF585E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFF6B567E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1DBFF),
    onTertiaryContainer = Color(0xFF533E65),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF4D5D8B),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    outline = Color(0xFF757680),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C5F9),
    onPrimary = Color(0xFF1D2F59),
    primaryContainer = Color(0xFF354571),
    onPrimaryContainer = Color(0xFFDAE2FF),
    inversePrimary = Color(0xFF4D5D8B),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFD7BDEB),
    onTertiary = Color(0xFF3B284D),
    tertiaryContainer = Color(0xFF533E65),
    onTertiaryContainer = Color(0xFFF1DBFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB5C5F9),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF33343A),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Immutable
class LiveColors(val content: Color, val container: Color, val onContainer: Color)

private val LiveLight = LiveColors(Color(0xFF137333), Color(0xFFCEEAD6), Color(0xFF0D652D))
private val LiveDark = LiveColors(Color(0xFF81C995), Color(0xFF0F5223), Color(0xFFA8DAB5))
val LocalLiveColors = staticCompositionLocalOf { LiveLight }

fun routeColor(hex: String?): Color =
    runCatching { Color((hex ?: "").toColorInt()) }.getOrDefault(Palette.Slate500)

@Composable
fun ConexiuniTheme(dark: Boolean, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        dynamic && dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalLiveColors provides if (dark) LiveDark else LiveLight,
    ) {
        MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
    }
}
