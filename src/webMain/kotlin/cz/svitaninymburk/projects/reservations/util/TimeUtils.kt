package cz.svitaninymburk.projects.reservations.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number


val LocalDateTime.humanReadable: String get() = buildString {
    append(day)
    append('.')
    append(month.number)
    append('.')
    append(year)
    append(' ')
    append(hour)
    append(':')
    append(minute)
}