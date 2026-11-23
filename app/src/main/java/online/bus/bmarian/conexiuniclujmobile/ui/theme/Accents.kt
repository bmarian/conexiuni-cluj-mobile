package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FavoriteLight = Color(0xFFA8798B)
private val FavoriteDark = Color(0xFFB98D9D)

@Composable
fun favoriteColor(): Color = when {
    LocalDoodle.current != null -> Palette.Rose
    LocalDarkTheme.current -> FavoriteDark
    else -> FavoriteLight
}
