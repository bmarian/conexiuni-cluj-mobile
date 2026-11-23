package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.ui.unit.sp
import androidx.glance.material3.ColorProviders
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import online.bus.bmarian.conexiuniclujmobile.R

@Immutable
class DoodleInk(
    val outline: Color,
    val rim: Color,
    val marker: Color,
    val onMarker: Color,
    val chalk: Boolean,
    val text: Color,
    val paper: Color,
    val ground: Color,
    val window: Color,
    val blob: Color,
)

/** Null in the standard look. */
val LocalDoodle = staticCompositionLocalOf<DoodleInk?> { null }

private val Ink = Color(0xFF23201C)
private val Paper = Color(0xFFFFFBF2)
private val Pencil = Color(0xFF6B6458)
private val Marker = Color(0xFFFFD84D)
private val Board = Color(0xFF1B2120)
private val Chalk = Color(0xFFF2EDE0)
private val ChalkLine = Color(0xFFE4DFD1)
private val ChalkPencil = Color(0xFFA3A699)
private val NightMarker = Color(0xFFF6CB45)

internal val PaperInk = DoodleInk(
    outline = Ink, rim = Ink, marker = Marker, onMarker = Ink, chalk = false,
    text = Ink, paper = Color(0xFFFFFDF8), ground = Color(0xFFFBF6EA), window = Paper, blob = Paper,
)
internal val ChalkInk = DoodleInk(
    outline = ChalkLine, rim = Color(0xFF101413), marker = NightMarker, onMarker = Ink, chalk = true,
    text = Chalk, paper = Color(0xFF212827), ground = Color(0xFF151A19), window = Color(0xFF2A3331), blob = Chalk,
)

fun doodleInk(dark: Boolean): DoodleInk = if (dark) ChalkInk else PaperInk

internal val DoodleLight = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryFixed = Ink,
    onPrimaryFixed = Paper,
    primaryContainer = Marker,
    onPrimaryContainer = Ink,
    inversePrimary = Marker,
    secondary = Pencil,
    onSecondary = Paper,
    secondaryContainer = Marker,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFE4473A),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFFFDDD6),
    onTertiaryContainer = Color(0xFF5C1A12),
    error = Color(0xFFC9363E),
    onError = Paper,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEE6D5),
    onSurfaceVariant = Pencil,
    surfaceTint = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    outline = Ink,
    outlineVariant = Color(0xFFD8CFBE),
    scrim = Color.Black,
    surfaceBright = Paper,
    surfaceDim = Color(0xFFE9E1D0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF8),
    surfaceContainer = Color(0xFFF8F2E5),
    surfaceContainerHigh = Color(0xFFF3ECDD),
    surfaceContainerHighest = Color(0xFFEEE6D5),
)

internal val DoodleDark = darkColorScheme(
    primary = Chalk,
    onPrimary = Board,
    primaryFixed = Chalk,
    onPrimaryFixed = Board,
    primaryContainer = NightMarker,
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = ChalkPencil,
    onSecondary = Board,
    secondaryContainer = NightMarker,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFFF7468),
    onTertiary = Board,
    tertiaryContainer = Color(0xFF5C2620),
    onTertiaryContainer = Color(0xFFFFDAD3),
    error = Color(0xFFFF7468),
    onError = Board,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Board,
    onBackground = Chalk,
    surface = Board,
    onSurface = Chalk,
    surfaceVariant = Color(0xFF38423F),
    onSurfaceVariant = ChalkPencil,
    surfaceTint = Board,
    inverseSurface = Chalk,
    inverseOnSurface = Board,
    outline = ChalkLine,
    outlineVariant = Color(0xFF3A4442),
    scrim = Color.Black,
    surfaceBright = Color(0xFF38423F),
    surfaceDim = Color(0xFF151A19),
    surfaceContainerLowest = Color(0xFF151A19),
    surfaceContainerLow = Color(0xFF212827),
    surfaceContainer = Color(0xFF29312F),
    surfaceContainerHigh = Color(0xFF303937),
    surfaceContainerHighest = Color(0xFF38423F),
)

val DoodleWidgetColors = ColorProviders(light = DoodleLight, dark = DoodleDark)

internal val DoodleLiveLight = LiveColors(Ink, Marker, Ink, Ink, Marker)
internal val DoodleLiveDark = LiveColors(Chalk, NightMarker, Ink, Chalk, NightMarker)

// Shantell's INFM axis, 0 (tidy) to 100.
private const val INFORMALITY = 60f

internal val DoodleFont = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.shantell_sans,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("INFM", INFORMALITY)),
        )
    },
)

internal val DoodleTypography = Typography().run {
    fun TextStyle.hand() = copy(fontFamily = DoodleFont, letterSpacing = 0.sp)
    copy(
        displayLarge = displayLarge.hand(),
        displayMedium = displayMedium.hand(),
        displaySmall = displaySmall.hand(),
        headlineLarge = headlineLarge.hand(),
        headlineMedium = headlineMedium.hand(),
        headlineSmall = headlineSmall.hand(),
        titleLarge = titleLarge.hand(),
        titleMedium = titleMedium.hand(),
        titleSmall = titleSmall.hand(),
        bodyLarge = bodyLarge.hand(),
        bodyMedium = bodyMedium.hand(),
        bodySmall = bodySmall.hand(),
        labelLarge = labelLarge.hand(),
        labelMedium = labelMedium.hand(),
        labelSmall = labelSmall.hand(),
    )
}
