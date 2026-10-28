package online.bus.bmarian.conexiuniclujmobile.logic

import java.time.LocalDate

private const val WEEK = 7L

/** Port of planDays. */
fun planDays(today: LocalDate, chosen: LocalDate): List<LocalDate> {
    val days = (0 until WEEK).map { today.plusDays(it) }
    return if (chosen in days) days else days + chosen
}
