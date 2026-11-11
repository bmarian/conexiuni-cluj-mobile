package online.bus.bmarian.conexiuniclujmobile.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Port of utils/hints.ts: one-time tips shown in the snackbar when someone's own habits show a feature would help.
 * Counted on the device only; each tip shows once, and no more than one a day.
 */
@Serializable
data class HintState(
    val shown: List<String> = emptyList(),
    val lastDay: String? = null,
    /** Distinct days, newest last, keyed by "stop", "stop@hour" and "stop>line". */
    val days: Map<String, List<String>> = emptyMap(),
)

data class HintLine(val routeId: Int, val direction: String, val platform: Int)

sealed class Hint(val id: String) {
    data object Save : Hint(SAVE)
    data class Group(val a: HintLine, val b: HintLine) : Hint(GROUP)
    data object Remind : Hint(REMIND)

    companion object {
        const val SAVE = "save"
        const val GROUP = "group"
        const val REMIND = "remind"
        val ALL = listOf(SAVE, GROUP, REMIND)
    }
}

class HintContext(val stop: String, val day: String, val hour: Int, val saved: Boolean, val grouped: (HintLine, HintLine) -> Boolean, val canRemind: Boolean)

object Hints {
    const val SAVE_DAYS = 3
    const val GROUP_DAYS = 2
    const val REMIND_DAYS = 3
    private val KEPT_DAYS = maxOf(SAVE_DAYS, GROUP_DAYS, REMIND_DAYS)

    private val json = Json { ignoreUnknownKeys = true }

    private fun lineKey(stop: String, line: HintLine) = "$stop>${line.routeId}:${line.direction}:${line.platform}"

    private fun parseLine(key: String): HintLine? {
        val parts = key.substringAfter('>').split(':')
        val direction = parts.getOrNull(1)?.takeIf { it == "0" || it == "1" } ?: return null
        return HintLine(parts[0].toIntOrNull() ?: return null, direction, parts.getOrNull(2)?.toIntOrNull() ?: return null)
    }

    private fun HintState.withDay(key: String, day: String): HintState {
        val list = days[key].orEmpty()
        if (day in list) return this
        return copy(days = days + (key to (list + day).takeLast(KEPT_DAYS)))
    }

    fun visitStop(state: HintState, stop: String, day: String, hour: Int) = state.withDay(stop, day).withDay("$stop@$hour", day)

    fun openLineFromStop(state: HintState, stop: String, line: HintLine, day: String) = state.withDay(lineKey(stop, line), day)

    fun pick(state: HintState, ctx: HintContext): Hint? {
        if (state.lastDay == ctx.day) return null
        fun free(id: String) = id !in state.shown
        if (free(Hint.SAVE) && !ctx.saved && state.days[ctx.stop].orEmpty().size >= SAVE_DAYS) return Hint.Save
        if (free(Hint.GROUP)) {
            val often = state.days.entries
                .filter { (key, days) -> key.startsWith("${ctx.stop}>") && days.size >= GROUP_DAYS }
                .sortedByDescending { it.value.last() }
                .mapNotNull { parseLine(it.key) }
                .distinctBy { it.routeId }
            val a = often.getOrNull(0)
            val b = often.getOrNull(1)
            if (a != null && b != null && !ctx.grouped(a, b)) return Hint.Group(a, b)
        }
        if (free(Hint.REMIND) && ctx.canRemind && state.days["${ctx.stop}@${ctx.hour}"].orEmpty().size >= REMIND_DAYS) return Hint.Remind
        return null
    }

    fun markShown(state: HintState, id: String, day: String) = state.copy(shown = state.shown + id, lastDay = day)

    fun read(raw: String?): HintState {
        val state = raw?.let { runCatching { json.decodeFromString<HintState>(it) }.getOrNull() } ?: return HintState()
        return state.copy(shown = state.shown.filter { it in Hint.ALL })
    }

    fun write(state: HintState): String = json.encodeToString(HintState.serializer(), state)
}
