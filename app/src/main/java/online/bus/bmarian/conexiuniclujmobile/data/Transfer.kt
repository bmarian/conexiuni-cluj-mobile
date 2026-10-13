package online.bus.bmarian.conexiuniclujmobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.Services
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Port of utils/transferLink.ts and useSettingsTransfer.ts.
object Transfer {
    private val random = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    fun newKey(): String = urlEncoder.encodeToString(ByteArray(16).also(random::nextBytes))

    fun url(code: String, key: String) = "${BuildConfig.SITE_ORIGIN}/settings?send=$code#k=$key"

    fun spaced(code: String) = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

    private fun cipher(mode: Int, key: String, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(urlDecoder.decode(key), "AES"), GCMParameterSpec(128, iv))
        }

    fun open(payload: JsonElement, key: String?): JsonElement {
        val sealed = payload as? JsonObject ?: return payload
        if ((sealed["sealed"] as? JsonPrimitive)?.intOrNull != 1) return payload
        val iv = (sealed["iv"] as? JsonPrimitive)?.contentOrNull ?: return payload
        val data = (sealed["data"] as? JsonPrimitive)?.contentOrNull ?: return payload
        if (key == null) throw IllegalStateException("sealed transfer without its key")
        val plain = cipher(Cipher.DECRYPT_MODE, key, urlDecoder.decode(iv)).doFinal(urlDecoder.decode(data))
        return Api.json.parseToJsonElement(String(plain, Charsets.UTF_8))
    }

    fun seal(data: JsonElement, key: String?): JsonElement {
        if (key.isNullOrEmpty()) return data
        val iv = ByteArray(12).also(random::nextBytes)
        val sealed = cipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(data.toString().toByteArray(Charsets.UTF_8))
        return buildJsonObject {
            put("sealed", 1)
            put("iv", urlEncoder.encodeToString(iv))
            put("data", urlEncoder.encodeToString(sealed))
        }
    }

    data class Summary(val stops: Int, val lines: Int, val places: Int, val followed: Int)

    fun describe(data: JsonElement): Summary? {
        val export = data as? JsonObject ?: return null
        val favorites = export["favorites"] as? JsonObject
        if (favorites == null && export["settings"] !is JsonObject) return null
        return Summary(
            stops = Favorites.parseStopIds(favorites?.get("stops")).size,
            lines = Favorites.parseFavoriteRoutes(favorites?.get("routes")).size,
            places = Favorites.parsePlans(favorites?.get("plans")).size,
            followed = (export["followedLines"] as? JsonArray)?.count { (it as? JsonPrimitive)?.isString == true } ?: 0,
        )
    }

    fun buildExport(): JsonObject {
        val s = Services.settings
        val f = Services.favorites
        val json = Api.json
        return buildJsonObject {
            put("version", 1)
            put("settings", buildJsonObject {
                put("theme", s.theme.value.key)
                put("locale", s.locale.value)
                put("arcadeUnlocked", s.arcadeUnlocked.value)
                put("arcadeActive", s.arcadeActive.value)
                put("legacyBlueUnlocked", s.legacyBlueUnlocked.value)
                put("legacyBlueActive", s.legacyBlueActive.value)
                put("paperActive", s.paperActive.value)
                put("showNews", s.showNews.value)
                put("autoCenterOnMe", s.autoCenterOnMe.value)
                put("autoFitMap", s.autoFitMap.value)
                put("showGreenFriday", s.showGreenFriday.value)
            })
            put("favorites", buildJsonObject {
                put("routes", json.encodeToJsonElement(f.routes.value))
                put("stops", json.encodeToJsonElement(f.stops.value))
                put("plans", json.encodeToJsonElement(f.plans.value))
                put("recentPlans", json.encodeToJsonElement(f.recentPlans.value))
            })
            put("followedLines", json.encodeToJsonElement(Services.routeUpdates.followed.value))
        }
    }

    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    fun applyExport(data: JsonElement) {
        val export = data as? JsonObject ?: throw IllegalArgumentException("not a settings export")
        val s = export["settings"] as? JsonObject ?: JsonObject(emptyMap())
        val settings = Services.settings
        ThemeMode.of((s["theme"] as? JsonPrimitive)?.contentOrNull)?.let(settings::setTheme)
        (s["locale"] as? JsonPrimitive)?.contentOrNull?.let(settings::setLocale)
        if (s.flag("arcadeUnlocked") == true) settings.unlockArcade()
        settings.setArcadeActive(s.flag("arcadeActive") == true)
        if (s.flag("legacyBlueUnlocked") == true) settings.unlockLegacyBlue()
        settings.setLegacyBlueActive(s.flag("legacyBlueActive") == true)
        settings.setPaperActive(s.flag("paperActive") == true)
        s.flag("showNews")?.let(settings::setShowNews)
        s.flag("autoCenterOnMe")?.let(settings::setAutoCenterOnMe)
        s.flag("autoFitMap")?.let(settings::setAutoFitMap)
        s.flag("showGreenFriday")?.let(settings::setShowGreenFriday)
        Services.favorites.importAll(export["favorites"] as? JsonObject ?: JsonObject(emptyMap()))
        (export["followedLines"] as? JsonArray)?.let(Services.routeUpdates::importFollowed)
    }

    /** Base64 of raw-deflated JSON, as the website writes it. */
    fun compress(json: String): String {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(json.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun decompress(text: String): String {
        val inflater = Inflater(true)
        inflater.setInput(Base64.getMimeDecoder().decode(text))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        val done = inflater.finished()
        inflater.end()
        if (!done) throw IllegalArgumentException("truncated")
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun parseImport(text: String): JsonElement {
        val raw = text.trim()
        val json = runCatching { decompress(raw) }.getOrDefault(raw)
        return Api.json.parseToJsonElement(json)
    }

    enum class SendResult { Sent, Unknown, Limited, Failed }

    suspend fun send(code: String, data: JsonElement, key: String?): SendResult = try {
        val body = seal(data, key).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(Api.url("transfer/${Api.encode(code)}")).put(body).build()
        Api.fetch(request) { response ->
            when {
                response.isSuccessful -> SendResult.Sent
                response.code == 404 -> SendResult.Unknown
                response.code == 429 -> SendResult.Limited
                else -> SendResult.Failed
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        SendResult.Failed
    }
}
