package cz.svitaninymburk.projects.reservations.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number


/** "9:05" — hodina bez vycpávání, minuty s nulou. */
val LocalTime.hourMinute: String get() = "$hour:${minute.toString().padStart(2, '0')}"

/** "9:05 – 10:30" */
fun timeRangeLabel(start: LocalTime, end: LocalTime): String = "${start.hourMinute} – ${end.hourMinute}"

val LocalDateTime.humanReadable: String get() = "${date.humanReadable} ${time.hourMinute}"

val LocalDate.humanReadable: String get() = buildString {
    append(day)
    append('.')
    append(month.number)
    append('.')
    append(year)
}
