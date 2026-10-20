package online.bus.bmarian.conexiuniclujmobile.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Port of frontend/src/utils/cityClock.ts.

val CLUJ_ZONE: ZoneId = ZoneId.of("Europe/Bucharest")

fun clujDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(CLUJ_ZONE).toLocalDate()

/** The moment the city's clock reads [minutes] past [date]'s midnight; 24:30 is the next day's 00:30. */
fun cityAt(date: LocalDate, minutes: Int): Long =
    date.atStartOfDay().plusMinutes(minutes.toLong()).atZone(CLUJ_ZONE).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
