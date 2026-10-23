package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.ui.theme.LineLooks
import org.junit.Assert.assertEquals
import org.junit.Test

class LineColorsTest {
    private val colorfuls = mapOf("25" to "#462EE0", "35" to "#DA771B", "24B" to "#A119D7")

    @Test
    fun favoritesAndLinesDrawnTogetherTakeTheirOwn() {
        val looks = LineLooks(colorful = false, favorites = setOf("25"), colorfuls = colorfuls)
        assertEquals("#462EE0", looks.hex("25", "#1D71B8"))
        assertEquals("#1D71B8", looks.hex("35", "#1D71B8"))
        assertEquals("#DA771B", looks.drawing(listOf("35" to "#1D71B8", "24B" to "#1D71B8")).hex("35", "#1D71B8"))
        assertEquals("#1D71B8", looks.drawing(listOf("35" to "#1D71B8")).hex("35", "#1D71B8"))
    }

    @Test
    fun aLineTheServerGaveNoColourKeepsItsOwn() {
        val looks = LineLooks(colorful = true, favorites = emptySet(), colorfuls = colorfuls)
        assertEquals("#DA771B", looks.hex("35", "#1D71B8"))
        assertEquals("#1D71B8", looks.hex("99", "#1D71B8"))
    }
}
