package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Look

object Palette {
    val Emerald = Color(0xFF10B981)
    val EmeraldLight = Color(0xFF34D399)
    val EmeraldDeep = Color(0xFF047857)
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
    primary = Color(0xFF3A6F69),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F5F2),
    onPrimaryContainer = Color(0xFF1F3D39),
    primaryFixed = Color(0xFFA9E0D9),
    onPrimaryFixed = Color(0xFF1C2B29),
    inversePrimary = Color(0xFFA8DCD5),
    secondary = Color(0xFF5D6665),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF1F1),
    onSecondaryContainer = Color(0xFF2C3332),
    tertiary = Color(0xFF636C94),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6E8F6),
    onTertiaryContainer = Color(0xFF353C5E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2322),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2322),
    surfaceVariant = Color(0xFFE9ECEB),
    onSurfaceVariant = Color(0xFF555D5C),
    surfaceTint = Color(0xFF3A6F69),
    inverseSurface = Color(0xFF2E3231),
    inverseOnSurface = Color(0xFFEFF1F1),
    outline = Color(0xFF858D8C),
    outlineVariant = Color(0xFFE1E5E4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE1E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF6F7F7),
    surfaceContainerHigh = Color(0xFFF0F2F2),
    surfaceContainerHighest = Color(0xFFE9ECEB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8DCD5),
    onPrimary = Color(0xFF14201F),
    primaryContainer = Color(0xFF26302F),
    onPrimaryContainer = Color(0xFFCDEEE9),
    primaryFixed = Color(0xFF9CDCD4),
    onPrimaryFixed = Color(0xFF14201F),
    inversePrimary = Color(0xFF3A6F69),
    secondary = Color(0xFFC4C6C9),
    onSecondary = Color(0xFF2A2B2E),
    secondaryContainer = Color(0xFF2A2A2D),
    onSecondaryContainer = Color(0xFFE3E3E6),
    tertiary = Color(0xFFC2C8EA),
    onTertiary = Color(0xFF2A2F4A),
    tertiaryContainer = Color(0xFF33364A),
    onTertiaryContainer = Color(0xFFE1E4F7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF19191B),
    onBackground = Color(0xFFE8E6E3),
    surface = Color(0xFF19191B),
    onSurface = Color(0xFFE8E6E3),
    surfaceVariant = Color(0xFF333337),
    onSurfaceVariant = Color(0xFFB9B6B2),
    surfaceTint = Color(0xFFA8DCD5),
    inverseSurface = Color(0xFFE8E6E3),
    inverseOnSurface = Color(0xFF2E2E31),
    outline = Color(0xFF8A8782),
    outlineVariant = Color(0xFF333337),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38383B),
    surfaceDim = Color(0xFF19191B),
    surfaceContainerLowest = Color(0xFF121214),
    surfaceContainerLow = Color(0xFF19191B),
    surfaceContainer = Color(0xFF1F1F21),
    surfaceContainerHigh = Color(0xFF262629),
    surfaceContainerHighest = Color(0xFF2E2E31),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

fun standardScheme(dark: Boolean): ColorScheme = if (dark) DarkColors else LightColors

@Immutable
class LiveColors(val content: Color, val container: Color, val onContainer: Color, val due: Color, val onDue: Color)

private val LiveLight = LiveColors(Color(0xFF3D8F85), Color(0xFFE0F4F1), Color(0xFF225049), Color(0xFFF6EA8C), Color(0xFF3A3520))
private val LiveDark = LiveColors(Color(0xFF7CC9BF), Color(0xFF243230), Color(0xFFBFE9E3), Color(0xFFECE08A), Color(0xFF2E2A17))
val LocalLiveColors = staticCompositionLocalOf { LiveLight }

fun routeColor(hex: String?): Color =
    runCatching { Color((hex ?: "").toColorInt()) }.getOrDefault(Palette.Slate500)

@Composable
fun ConexiuniTheme(dark: Boolean, content: @Composable () -> Unit) {
    val look by Services.settings.look.collectAsState()
    val doodle = look == Look.Doodle
    val scheme = when {
        doodle -> if (dark) DoodleDark else DoodleLight
        dark -> DarkColors
        else -> LightColors
    }
    val lineLooks by LineColors.looks.collectAsState()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalLiveColors provides when {
            doodle -> if (dark) DoodleLiveDark else DoodleLiveLight
            dark -> LiveDark
            else -> LiveLight
        },
        LocalLineLooks provides lineLooks,
        LocalDoodle provides if (doodle) doodleInk(dark) else null,
        LocalTonalElevationEnabled provides false,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = if (doodle) DoodleTypography else null,
            content = content,
        )
    }
}
