package online.bus.bmarian.conexiuniclujmobile.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import online.bus.bmarian.conexiuniclujmobile.R
import java.util.Locale

enum class ThemeMode(val key: String) {
    Light("light"), Dark("dark"), System("system");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

class Toast(val id: Long, val title: String, val body: String?, val actionLabel: String?, val action: (() -> Unit)?)

/** Same keys and defaults as stores/settings.ts. */
class Settings(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

    private fun set(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        flow.value = value
        prefs.edit { putBoolean(key, value) }
    }

    private val _theme = MutableStateFlow(ThemeMode.of(prefs.getString("theme", null)) ?: ThemeMode.System)
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit { putString("theme", mode.key) }
    }

    private val _locale = MutableStateFlow(detectInitialLocale())
    val locale: StateFlow<String> = _locale

    private fun detectInitialLocale(): String {
        // The per-app language from system settings wins.
        val applied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language
        } else null
        if (applied == "ro" || applied == "en") {
            prefs.edit { putString("locale", applied) }
            return applied
        }
        val saved = prefs.getString("locale", null)
        if (saved == "ro" || saved == "en") return saved
        return if (Locale.getDefault().language.lowercase().startsWith("en")) "en" else "ro"
    }

    fun setLocale(tag: String) {
        if (tag != "ro" && tag != "en") return
        _locale.value = tag
        prefs.edit { putString("locale", tag) }
        applyLocale()
    }

    fun applyLocale() {
        val tag = _locale.value
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LegacyLocale.apply(context, tag)
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        if (manager.applicationLocales.toLanguageTags() == tag) return
        manager.applicationLocales = LocaleList.forLanguageTags(tag)
    }

    // Web-only settings, kept for exports.
    val arcadeUnlocked = bool("arcadeUnlocked", false)
    val arcadeActive = bool("arcadeActive", false)
    val legacyBlueUnlocked = bool("legacyBlueUnlocked", false)
    val legacyBlueActive = bool("legacyBlueActive", false)
    val paperActive = bool("paperActive", false)

    fun unlockArcade() = set(arcadeUnlocked, "arcadeUnlocked", true)
    fun setArcadeActive(on: Boolean) {
        if (on) {
            set(legacyBlueActive, "legacyBlueActive", false)
            set(paperActive, "paperActive", false)
        }
        set(arcadeActive, "arcadeActive", on)
    }
    fun unlockLegacyBlue() = set(legacyBlueUnlocked, "legacyBlueUnlocked", true)
    fun setLegacyBlueActive(on: Boolean) {
        if (on) {
            set(arcadeActive, "arcadeActive", false)
            set(paperActive, "paperActive", false)
        }
        set(legacyBlueActive, "legacyBlueActive", on)
    }
    fun setPaperActive(on: Boolean) {
        if (on) {
            set(arcadeActive, "arcadeActive", false)
            set(legacyBlueActive, "legacyBlueActive", false)
        }
        set(paperActive, "paperActive", on)
    }

    val autoCenterOnMe = bool("autoCenterOnMe", true)
    val autoFitMap = bool("autoFitMap", true)
    val colorfulLines = bool("colorfulLines", false)
    val journeyVoice = bool("journeyVoice", true)
    val fullScreenAlarms = bool("fullScreenAlarms", false)

    val onboarded = bool("onboarded", false)
    fun setOnboarded() = set(onboarded, "onboarded", true)

    fun setAutoCenterOnMe(v: Boolean) = set(autoCenterOnMe, "autoCenterOnMe", v)
    fun setAutoFitMap(v: Boolean) = set(autoFitMap, "autoFitMap", v)
    fun setColorfulLines(v: Boolean) = set(colorfulLines, "colorfulLines", v)
    fun setJourneyVoice(v: Boolean) = set(journeyVoice, "journeyVoice", v)
    fun setFullScreenAlarms(v: Boolean) = set(fullScreenAlarms, "fullScreenAlarms", v)

    // Saved as "{city}:{key}"; Cluj's were saved before cities under the bare key.
    private fun regionKey(city: String, key: String) = "$city:$key"

    private val _regions = MutableStateFlow(
        RegionSettings.ALL.flatMap { setting ->
            setting.cities.map { city ->
                val saved = regionKey(city, setting.key).takeIf(prefs::contains)
                    ?: setting.key.takeIf { city == Cities.DEFAULT && prefs.contains(it) }
                regionKey(city, setting.key) to (saved?.let { prefs.getBoolean(it, setting.defaultOn) } ?: setting.defaultOn)
            }
        }.toMap(),
    )
    val regions: StateFlow<Map<String, Boolean>> = _regions

    fun regionSetting(city: String, key: String, values: Map<String, Boolean> = _regions.value): Boolean = values[regionKey(city, key)] ?: false

    fun setRegionSetting(city: String, key: String, value: Boolean) {
        if (RegionSettings.forCity(city).none { it.key == key }) return
        _regions.value += regionKey(city, key) to value
        prefs.edit { putBoolean(regionKey(city, key), value) }
    }

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 4)
    val toasts: SharedFlow<Toast> = _toasts
    private var toastSeq = 0L

    fun showToast(title: String, body: String? = null, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _toasts.tryEmit(Toast(++toastSeq, title, body, actionLabel, action))
    }
}

/** Port of utils/regionSettings.ts: settings that only some cities have. */
object RegionSettings {
    class Setting(val key: String, val cities: List<String>, val defaultOn: Boolean, @StringRes val title: Int, @StringRes val description: Int)

    const val GREEN_FRIDAY = "showGreenFriday"

    val ALL = listOf(
        Setting(GREEN_FRIDAY, listOf("cluj"), true, R.string.w_green_friday_title, R.string.w_green_friday_setting_desc),
    )

    fun forCity(city: String) = ALL.filter { city in it.cities }
}
