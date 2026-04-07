package cz.svitaninymburk.projects.reservations.event

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun generateRecurrenceDates(
    startDate: LocalDate,
    startTime: LocalTime,
    recurrenceType: RecurrenceType,
    recurrenceEndDate: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<LocalDateTime> {
    val endLocalDate = recurrenceEndDate.toLocalDateTime(timeZone).date
    if (endLocalDate < startDate) return emptyList()

    val unit: DateTimeUnit.DateBased = when (recurrenceType) {
        RecurrenceType.NONE -> return listOf(LocalDateTime(startDate, startTime))
        RecurrenceType.DAILY -> DateTimeUnit.DAY
        RecurrenceType.WEEKLY -> DateTimeUnit.WEEK
        RecurrenceType.MONTHLY -> DateTimeUnit.MONTH
    }

    val dates = mutableListOf<LocalDateTime>()
    var current = startDate
    while (current <= endLocalDate) {
        dates.add(LocalDateTime(current, startTime))
        current = current.plus(1, unit)
    }
    return dates
}

data class SeriesAutoFill(val endDate: LocalDate, val lessonCount: Int)

fun computeSeriesAutoFill(
    startDate: LocalDate,
    recurrenceType: RecurrenceType,
    recurrenceEndDate: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): SeriesAutoFill? {
    if (recurrenceType == RecurrenceType.NONE) return null
    val dates = generateRecurrenceDates(startDate, LocalTime(0, 0), recurrenceType, recurrenceEndDate, timeZone)
    if (dates.isEmpty()) return null
    return SeriesAutoFill(endDate = dates.last().date, lessonCount = dates.size)
}
