package online.bus.bmarian.conexiuniclujmobile.logic

/** Port of composables/useFirstSteps.ts: decided once per device, only someone who starts with nothing saved gets the card. */
data class FirstSteps(val on: Boolean, val done: List<String> = emptyList()) {
    val left get() = if (on) ALL.filter { it !in done } else emptyList()

    fun mark(step: String) = if (on && step !in done && step in ALL) copy(done = done + step) else this

    fun hidden() = copy(on = false)

    companion object {
        const val PLAN = "plan"
        const val STOP = "stop"
        const val LINE = "line"

        val ALL = listOf(PLAN, STOP, LINE)
    }
}
