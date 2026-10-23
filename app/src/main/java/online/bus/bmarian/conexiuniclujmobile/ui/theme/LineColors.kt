package online.bus.bmarian.conexiuniclujmobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import online.bus.bmarian.conexiuniclujmobile.Services

fun manyLines(names: List<String>) = names.map { it.trim() }.filter { it.isNotEmpty() }.toSet().size > 1

@Immutable
data class LineLooks(
    private val colorful: Boolean,
    private val favorites: Set<String>?,
    private val colorfuls: Map<String, String> = emptyMap(),
    private val drawnLines: List<Pair<String, String>> = emptyList(),
) {
    private val drawn = manyLines(drawnLines.map { it.first }).let { apart -> drawnLines.associate { (name, hex) -> name to own(name, hex, apart) } }

    fun own(name: String?, hex: String?, apart: Boolean = false): String? =
        if (!name.isNullOrBlank() && (apart || colorful || favorite(name) == true)) colorfuls[name] ?: hex else hex

    /** Null until the favourites are known, so loading never animates. */
    fun favorite(name: String?): Boolean? = favorites?.contains(name)

    val favoriteNames: Set<String> get() = favorites.orEmpty()

    // Badges of lines drawn on the map take the map's colour, as its legend.
    fun hex(name: String?, hex: String?, apart: Boolean = false): String? = drawn[name] ?: own(name, hex, apart)

    fun color(name: String?, hex: String?, apart: Boolean = false): Color = routeColor(hex(name, hex, apart))

    /** For the map itself, before [LineColors.draw] lands. */
    fun drawing(lines: List<Pair<String, String>>) = copy(drawnLines = lines)
}

object LineColors {
    private val drawn = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    /** As (name, feed colour). */
    fun draw(lines: List<Pair<String, String>>) {
        drawn.value = lines
    }

    val looks: StateFlow<LineLooks> by lazy {
        val lines = combine(Services.favorites.routes, Services.repository.routes) { favorites, routes ->
            val names = favorites.mapNotNull { f -> routes.firstOrNull { it.routeId == f.routeId }?.shortName }.toSet()
            (if (routes.isEmpty()) null else names) to routes.filter { it.colorful.isNotEmpty() }.associate { it.shortName to it.colorful }
        }
        combine(Services.settings.colorfulLines, lines, drawn) { on, (favorites, colorfuls), drawnLines -> LineLooks(on, favorites, colorfuls, drawnLines) }
            .stateIn(Services.scope, SharingStarted.Eagerly, LineLooks(Services.settings.colorfulLines.value, null))
    }
}

/** For notifications and widgets: not the map's colours. */
fun ownHex(name: String?, hex: String?, apart: Boolean = false): String? = LineColors.looks.value.own(name, hex, apart)

fun ridesApart(lines: List<List<String>>) = manyLines(lines.map { it.firstOrNull().orEmpty() })

val LocalLineLooks = compositionLocalOf { LineLooks(colorful = false, favorites = null) }

@Composable
fun lineColor(name: String?, hex: String?, apart: Boolean = false): Color = LocalLineLooks.current.color(name, hex, apart)
