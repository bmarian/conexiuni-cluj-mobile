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
import online.bus.bmarian.conexiuniclujmobile.logic.FirstSteps
import online.bus.bmarian.conexiuniclujmobile.logic.HintState
import online.bus.bmarian.conexiuniclujmobile.logic.Hints
import online.bus.bmarian.conexiuniclujmobile.logic.HomeSections
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

    val autoCenterOnMe = bool("autoCenterOnMe", true)
    val autoFitMap = bool("autoFitMap", true)
    val colorfulLines = bool("colorfulLines", false)
    val traffic = bool("traffic", true)
    val journeyVoice = bool("journeyVoice", true)
    val fullScreenAlarms = bool("fullScreenAlarms", false)

    val onboarded = bool("onboarded", false)
    fun setOnboarded() = set(onboarded, "onboarded", true)

    fun setAutoCenterOnMe(v: Boolean) = set(autoCenterOnMe, "autoCenterOnMe", v)
    fun setAutoFitMap(v: Boolean) = set(autoFitMap, "autoFitMap", v)
    fun setColorfulLines(v: Boolean) = set(colorfulLines, "colorfulLines", v)
    fun setTraffic(v: Boolean) = set(traffic, "traffic", v)
    fun setJourneyVoice(v: Boolean) = set(journeyVoice, "journeyVoice", v)
    fun setFullScreenAlarms(v: Boolean) = set(fullScreenAlarms, "fullScreenAlarms", v)

    private fun savedList(key: String) = prefs.getString(key, null)?.split(',')?.filter(String::isNotEmpty).orEmpty()

    private val _homeOrder = MutableStateFlow(HomeSections.order(savedList("home.order")))
    val homeOrder: StateFlow<List<String>> = _homeOrder

    fun setHomeOrder(order: List<String>) {
        val next = HomeSections.order(order)
        _homeOrder.value = next
        prefs.edit { putString("home.order", next.joinToString(",")) }
    }

    private val _homeCollapsed = MutableStateFlow(savedList("home.collapsed").toSet())
    val homeCollapsed: StateFlow<Set<String>> = _homeCollapsed

    fun setHomeCollapsed(ids: Set<String>) {
        _homeCollapsed.value = ids
        prefs.edit { putString("home.collapsed", ids.joinToString(",")) }
    }

    fun toggleHomeSection(id: String) = setHomeCollapsed(_homeCollapsed.value.let { if (id in it) it - id else it + id })

    private val _firstSteps = MutableStateFlow(
        if (prefs.contains("home.firstSteps.on")) FirstSteps(prefs.getBoolean("home.firstSteps.on", false), savedList("home.firstSteps.done")) else null,
    )
    val firstSteps: StateFlow<FirstSteps?> = _firstSteps

    private fun setFirstSteps(next: FirstSteps) {
        if (next == _firstSteps.value) return
        _firstSteps.value = next
        prefs.edit {
            putBoolean("home.firstSteps.on", next.on)
            putString("home.firstSteps.done", next.done.joinToString(","))
        }
    }

    fun startFirstSteps(fresh: Boolean) {
        if (_firstSteps.value == null) setFirstSteps(FirstSteps(fresh))
    }

    fun markFirstStep(step: String) {
        _firstSteps.value?.let { setFirstSteps(it.mark(step)) }
    }

    fun hideFirstSteps() {
        _firstSteps.value?.let { setFirstSteps(it.hidden()) }
    }

    private val _hints = MutableStateFlow(Hints.read(prefs.getString("hints", null)))
    val hints: StateFlow<HintState> = _hints

    fun updateHints(change: (HintState) -> HintState) {
        val next = change(_hints.value)
        if (next == _hints.value) return
        _hints.value = next
        prefs.edit { putString("hints", Hints.write(next)) }
    }

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
