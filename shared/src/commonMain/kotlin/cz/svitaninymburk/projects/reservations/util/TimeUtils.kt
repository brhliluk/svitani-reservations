package cz.svitaninymburk.projects.reservations.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number


/** "9:05" — hodina bez vycpávání, minuty s nulou. */
val LocalTime.hourMinute: String get() = "$hour:${minute.toString().padStart(2, '0')}"

/** "9:05 – 10:30" */
fun timeRangeLabel(start: LocalTime, end: LocalTime): String = "${start.hourMinute} – ${end.hourMinute}"

/**
 * Hodiny mezi dvěma místními časy, bez časového pásma (prohlížeč nemá databázi pásem).
 * Přes změnu letního času to o hodinu ujede, což u denních úseků nevadí.
 */
fun hoursBetween(start: LocalDateTime, end: LocalDateTime): Double {
    val seconds = start.date.daysUntil(end.date) * 86_400L + end.time.toSecondOfDay() - start.time.toSecondOfDay()
    return seconds / 3600.0
}

val LocalDateTime.humanReadable: String get() = "${date.humanReadable} ${time.hourMinute}"

val LocalDate.humanReadable: String get() = buildString {
    append(day)
    append('.')
    append(month.number)
    append('.')
    append(year)
}
