package online.bus.bmarian.conexiuniclujmobile

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Transfer
import online.bus.bmarian.conexiuniclujmobile.logic.Planner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64
import java.util.zip.Deflater

class TransferTest {
    private val export = buildJsonObject {
        put("version", 1)
        put("settings", buildJsonObject { put("locale", "ro") })
        put("favorites", buildJsonObject {
            put("stops", buildJsonArray { add(JsonPrimitive(391)); add(JsonPrimitive(4)) })
        })
        put("followedLines", buildJsonArray { add(JsonPrimitive("25")) })
    }

    @Test
    fun sealsSoTheKeyOpensIt() {
        val key = Transfer.newKey()
        val sealed = Transfer.seal(export, key)
        assertEquals(1, sealed.jsonObject["sealed"]?.let { (it as JsonPrimitive).content.toInt() })
        assertNotEquals(export, sealed)
        assertEquals(export, Transfer.open(sealed, key))
        assertEquals(export, Transfer.seal(export, null))
    }

    @Test
    fun exportTextIsBase64RawDeflate() {
        val text = Transfer.compress(export.toString())
        assertEquals(export, Transfer.parseImport(text))
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(export.toString().toByteArray())
        deflater.finish()
        val buffer = ByteArray(4096)
        val n = deflater.deflate(buffer)
        val browser = Base64.getEncoder().encodeToString(buffer.copyOf(n))
        assertEquals(export, Transfer.parseImport(browser))
        assertEquals(export, Transfer.parseImport("  ${export}  "))
    }

    @Test
    fun describesWhatWouldBeKept() {
        val summary = Transfer.describe(export)!!
        assertEquals(Transfer.Summary(stops = 2, lines = 0, places = 0, followed = 1), summary)
        assertEquals(null, Transfer.describe(Api.json.parseToJsonElement("[1,2]")))
    }

    @Test
    fun decodesGooglePolylines() {
        // Example from Google's polyline documentation.
        val points = Planner.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), points)
    }
}
