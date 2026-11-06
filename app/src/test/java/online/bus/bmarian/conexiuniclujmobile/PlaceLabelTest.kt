package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.placeIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceLabelTest {
    private fun of(label: String) = placeIcon(label).let { it.icon to it.name }

    @Test
    fun takesALeadingEmojiWordAsTheIcon() {
        assertEquals("🏡" to "Home", of("🏡 Home"))
        assertEquals("👩‍🎓" to "Facultate de Litere", of("👩‍🎓 Facultate de Litere"))
        assertEquals("🇷🇴" to "Acasă", of("🇷🇴 Acasă"))
    }

    @Test
    fun keepsNamesWithoutOne() {
        assertEquals(null to "Iulius Mall", of("Iulius Mall"))
        assertEquals(null to "1 Mai", of("1 Mai"))
        assertEquals(null to "🏢", of("🏢"))
        assertEquals(null to "🏢Work", of("🏢Work"))
    }
}
