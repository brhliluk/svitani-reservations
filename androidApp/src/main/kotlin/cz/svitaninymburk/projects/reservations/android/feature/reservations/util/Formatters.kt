package cz.svitaninymburk.projects.reservations.android.feature.reservations.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

private val dateTimeFormat = LocalDateTime.Format {
    day(Padding.NONE); char('.'); char(' '); monthNumber(Padding.NONE); char('.'); char(' '); year()
    char(' '); hour(); char(':'); minute()
}

internal fun LocalDateTime.formatted(): String = dateTimeFormat.format(this)
