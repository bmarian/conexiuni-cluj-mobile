package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLegs
import online.bus.bmarian.conexiuniclujmobile.logic.GroupLine
import online.bus.bmarian.conexiuniclujmobile.logic.Usage
import online.bus.bmarian.conexiuniclujmobile.logic.counted
import online.bus.bmarian.conexiuniclujmobile.logic.legsKey
import online.bus.bmarian.conexiuniclujmobile.logic.legsOf

/** Same shape as FavoriteGroup in stores/favorites.ts. */
@Serializable
data class SavedGroup(val lines: List<GroupLine>, val name: String? = null, val back: List<GroupLine>? = null) {
    val legs get() = legsOf(lines, back)
    val key get() = legsKey(legs)
}

class LineGroupStore(private val context: Context) {
    var city = Cities.current.value
        private set
    private var prefs = cityPrefs(context, "groups", city)

    private val _saved = MutableStateFlow(readSaved(prefs))
    val saved: StateFlow<List<SavedGroup>> = _saved

    private fun readSaved(from: SharedPreferences): List<SavedGroup> =
        from.getString("saved", null)?.let { runCatching { Api.json.decodeFromString<List<SavedGroup>>(it) }.getOrNull() }.orEmpty()
            .filter { it.lines.isNotEmpty() }

    private fun write(to: SharedPreferences, list: List<SavedGroup>) = to.edit { putString("saved", Api.json.encodeToString(list)) }

    private fun persist(list: List<SavedGroup>) {
        _saved.value = list
        write(prefs, list)
    }

    fun savedIn(city: String): List<SavedGroup> = if (city == this.city) _saved.value else readSaved(cityPrefs(context, "groups", city))

    fun replaceSynced(city: String, list: List<SavedGroup>) = if (city == this.city) persist(list) else write(cityPrefs(context, "groups", city), list)

    fun use(slug: String) {
        if (slug == city) return
        city = slug
        prefs = cityPrefs(context, "groups", slug)
        _saved.value = readSaved(prefs)
    }

    fun find(legs: GroupLegs): SavedGroup? = legsKey(legs).let { key -> _saved.value.firstOrNull { it.key == key } }

    fun save(legs: GroupLegs, name: String?) {
        if (legs.lines.isEmpty()) return
        val list = _saved.value
        val known = find(legs)
        if (known != null) return persist(list.map { if (it == known) it.copy(name = name?.ifBlank { null }) else it })
        if (Services.favorites.atCap(list)) return
        persist(list + SavedGroup(legs.lines, name?.ifBlank { null }, legs.back))
    }

    fun remove(legs: GroupLegs) = legsKey(legs).let { key -> persist(_saved.value.filterNot { it.key == key }) }

    fun replace(from: GroupLegs, legs: GroupLegs) {
        val group = find(from) ?: return
        val key = legsKey(legs)
        persist(_saved.value.filterNot { it.key == key && it != group }.map { if (it == group) it.copy(lines = legs.lines, back = legs.back) else it })
    }

    fun reorder(list: List<SavedGroup>) = persist(list)

    fun lineUsage(): Map<String, Usage> =
        prefs.getString(LINE_USAGE, null)?.let { runCatching { Api.json.decodeFromString<Map<String, Usage>>(it) }.getOrNull() }.orEmpty()

    fun recordLineUse(key: String, atMs: Long = System.currentTimeMillis()) {
        val log = lineUsage()
        prefs.edit { putString(LINE_USAGE, Api.json.encodeToString(log + (key to log[key].counted(atMs)))) }
    }

    private companion object {
        const val LINE_USAGE = "usage:lines"
    }
}
