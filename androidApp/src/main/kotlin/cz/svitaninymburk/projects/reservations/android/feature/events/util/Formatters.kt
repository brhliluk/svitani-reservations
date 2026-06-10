package cz.svitaninymburk.projects.reservations.android.feature.events.util

import io.github.adrcotfas.datetime.names.TextStyle
import io.github.adrcotfas.datetime.names.getDisplayName
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.DateTimeFormatBuilder
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

private fun DateTimeFormatBuilder.WithDate.czechDate() {
    day(Padding.NONE); char('.'); char(' '); monthNumber(Padding.NONE); char('.'); char(' '); year()
}

private val dateTimeFormat = LocalDateTime.Format {
    czechDate(); char(' '); hour(); char(':'); minute()
}

private val dateFormat = LocalDate.Format {
    czechDate()
}

private val timeFormat = LocalTime.Format {
    hour(); char(':'); minute()
}

internal fun LocalDateTime.formatted(): String = dateTimeFormat.format(this)
internal fun LocalDate.formatted(): String = dateFormat.format(this)
internal fun LocalTime.formatted(): String = timeFormat.format(this)
internal fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL)
