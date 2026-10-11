package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.journey.speechParts
import online.bus.bmarian.conexiuniclujmobile.journey.spokenName
import online.bus.bmarian.conexiuniclujmobile.journey.spokenStopName
import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyVoiceTest {
    @Test
    fun stopNamesAreSaidInFull() {
        mapOf(
            "P-ța Cipariu Nord" to "Piața Cipariu Nord",
            "P-ta M. Viteazul Vest" to "Piața Mihai Viteazul Vest",
            "P-ța M.Viteazu Sosire" to "Piața Mihai Viteazu Sosire",
            "Str. Bucium" to "Strada Bucium",
            "Disp. Clăbucet" to "Dispecerat Clăbucet",
            "M-Moara V" to "Moara Vest",
            "M-Poliția Baciu S" to "Poliția Baciu Sud",
            "M Petrestii de Jos N" to "Petrestii de Jos Nord",
            "M-Eroilor E (Florești)" to "Eroilor Est (Florești)",
            "M- Pod Chinteni Vest" to "Pod Chinteni Vest",
            "N. Balcescu N" to "Nicolae Balcescu Nord",
            "P-ța I Agârbiceanu Est" to "Piața Ion Agârbiceanu Est",
            "Mareșal C-tin Prezan" to "Mareșal Constantin Prezan",
            "Mareșal Al. Averescu" to "Mareșal Alexandru Averescu",
            "T.Vladimirescu Nord" to "Tudor Vladimirescu Nord",
            "W.A. Mozart" to "Wolfgang Amadeus Mozart",
            "D.D. Roșca Est" to "Dumitru D. Roșca Est",
            "M-Bis. Ortodoxă Sos (Luna)" to "Biserica Ortodoxă Sosire (Luna)",
            "M-Tudor Vladimirescu Ap." to "Tudor Vladimirescu Apahida",
            "Varianta Zorilor SUD" to "Varianta Zorilor Sud",
            "ERS CUG S." to "ERS CUG Sud",
            "Mărăști" to "Mărăști",
            "M-Av. T. Darjan" to "Aviator Traian Dârjan",
            "I. Ionescu Sosire" to "Ion Ionescu Sosire",
            "E. Ionesco" to "Eugen Ionesco",
            "Sp. de Boli Infecțioase" to "Spitalul de Boli Infecțioase",
        ).forEach { (written, said) -> assertEquals(said, spokenStopName(written)) }
    }

    @Test
    fun namesAreMarkedForTheRomanianVoice() {
        assertEquals(
            listOf("Get off at the next stop, " to false, "Piața Cipariu Nord" to true),
            speechParts("Get off at the next stop, ${spokenName("P-ța Cipariu Nord")}."),
        )
        assertEquals(listOf("The 44 is coming." to false), speechParts("The 44 is coming."))
    }
}
