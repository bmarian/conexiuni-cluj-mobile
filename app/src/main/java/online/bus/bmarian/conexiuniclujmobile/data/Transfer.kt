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

    /** Exports without cities, from older builds, are Cluj's. */
    private fun exportedCities(export: JsonObject): Map<String, JsonObject> {
        val cities = export["cities"] as? JsonObject
            ?: return mapOf(Cities.DEFAULT to JsonObject(listOfNotNull(export["favorites"]?.let { "favorites" to it }, export["followedLines"]?.let { "followedLines" to it }).toMap()))
        return cities.filter { (slug, entry) -> Cities.known(slug) && entry is JsonObject }.mapValues { it.value as JsonObject }
    }

    private fun followedCount(entry: JsonObject) = (entry["followedLines"] as? JsonArray)?.count { (it as? JsonPrimitive)?.isString == true } ?: 0

    fun describe(data: JsonElement): Summary? {
        val export = data as? JsonObject ?: return null
        if (export["favorites"] !is JsonObject && export["settings"] !is JsonObject) return null
        val entries = exportedCities(export).values
        fun favorites(entry: JsonObject) = entry["favorites"] as? JsonObject
        return Summary(
            stops = entries.sumOf { Favorites.parseStopIds(favorites(it)?.get("stops")).size },
            lines = entries.sumOf { Favorites.parseFavoriteRoutes(favorites(it)?.get("routes")).size },
            places = entries.sumOf { Favorites.parsePlans(favorites(it)?.get("plans")).size },
            followed = entries.sumOf(::followedCount),
        )
    }

    private fun savedCities(): Set<String> =
        (Cities.list.value.map { it.slug } + Cities.DEFAULT).filter {
            !Services.favorites.saved(it).isEmpty || Services.routeUpdates.followedIn(it).isNotEmpty()
        }.toSet()

    private fun favoritesJson(city: String): JsonObject {
        val saved = Services.favorites.saved(city)
        val json = Api.json
        return buildJsonObject {
            put("routes", json.encodeToJsonElement(saved.routes))
            put("stops", json.encodeToJsonElement(saved.stops))
            put("plans", json.encodeToJsonElement(saved.plans))
            put("recentPlans", json.encodeToJsonElement(saved.recentPlans))
        }
    }

    fun buildExport(): JsonObject {
        val s = Services.settings
        val json = Api.json
        val regionCities = Cities.list.value.map { it.slug } + RegionSettings.ALL.flatMap { it.cities }
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
                put("autoCenterOnMe", s.autoCenterOnMe.value)
                put("autoFitMap", s.autoFitMap.value)
                put("regions", buildJsonObject {
                    for (city in regionCities.distinct()) {
                        val settings = RegionSettings.forCity(city).takeIf { it.isNotEmpty() } ?: continue
                        put(city, buildJsonObject { settings.forEach { put(it.key, s.regionSetting(city, it.key)) } })
                    }
                })
            })
            // Older builds read these and know only Cluj.
            put("favorites", favoritesJson(Cities.DEFAULT))
            put("followedLines", json.encodeToJsonElement(Services.routeUpdates.followedIn(Cities.DEFAULT)))
            put("cities", buildJsonObject {
                for (city in savedCities()) {
                    put(city, buildJsonObject {
                        put("favorites", favoritesJson(city))
                        put("followedLines", json.encodeToJsonElement(Services.routeUpdates.followedIn(city)))
                    })
                }
            })
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
        s.flag("autoCenterOnMe")?.let(settings::setAutoCenterOnMe)
        s.flag("autoFitMap")?.let(settings::setAutoFitMap)
        // Exports from before cities hold Cluj's settings at the top level.
        val regions = s["regions"] as? JsonObject ?: JsonObject(mapOf(Cities.DEFAULT to s))
        for ((city, values) in regions) {
            if (values !is JsonObject) continue
            for (setting in RegionSettings.forCity(city)) values.flag(setting.key)?.let { settings.setRegionSetting(city, setting.key, it) }
        }
        val byCity = exportedCities(export)
        // With cities, the export is the whole set: a city it leaves out is emptied.
        val targets = if (export["cities"] is JsonObject) byCity.keys + savedCities() else byCity.keys
        for (city in targets) {
            val entry = byCity[city] ?: JsonObject(emptyMap())
            Services.favorites.importAll(entry["favorites"] as? JsonObject ?: JsonObject(emptyMap()), city)
            if (city in byCity && entry["followedLines"] !is JsonArray) continue
            Services.routeUpdates.importFollowed(entry["followedLines"] as? JsonArray, city)
        }
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
        val request = Request.Builder().url(Api.sharedUrl("transfer/${Api.encode(code)}")).put(body).build()
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
