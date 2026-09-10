package cz.svitaninymburk.projects.reservations.ui.events.usecase

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** Kolik akcí se vejde do dne v měsíční mřížce, zbytek se schová za "+N dalších". */
const val MAX_EVENTS_PER_DAY = 3

/**
 * Rozvržení měsíční mřížky. Týden začíná v pondělí, takže před prvním dnem
 * měsíce je [leadingBlanks] prázdných buněk a za posledním [trailingBlanks],
 * aby mřížka končila na celém týdnu.
 */
data class CalendarGrid(
    val daysInMonth: Int,
    val leadingBlanks: Int,
    val trailingBlanks: Int,
) {
    val totalCells: Int get() = leadingBlanks + daysInMonth + trailingBlanks
}

/**
 * [month] je první den zobrazovaného měsíce.
 *
 * Dvojité `% 7` u [CalendarGrid.trailingBlanks] je podstatné: měsíc, který
 * mřížku vyplní přesně, nesmí dostat celý prázdný týden navíc.
 */
fun calendarGrid(month: LocalDate): CalendarGrid {
    val daysInMonth = YearMonth(month.year, month.month).numberOfDays
    val leading = month.dayOfWeek.isoDayNumber - 1
    return CalendarGrid(
        daysInMonth = daysInMonth,
        leadingBlanks = leading,
        trailingBlanks = (7 - ((leading + daysInMonth) % 7)) % 7,
    )
}

fun eventsOn(events: List<EventInstance>, date: LocalDate): List<EventInstance> =
    events.filter { it.startDateTime.date == date }.sortedBy { it.startDateTime }

/**
 * Dny měsíce, které mají aspoň jednu akci — mobilní agenda prázdné dny
 * vynechává, na rozdíl od mřížky, která musí mít všechny.
 */
fun daysWithEvents(events: List<EventInstance>, month: LocalDate): List<Pair<LocalDate, List<EventInstance>>> {
    val grid = calendarGrid(month)
    return (1..grid.daysInMonth).mapNotNull { day ->
        val date = LocalDate(month.year, month.month, day)
        eventsOn(events, date).takeIf { it.isNotEmpty() }?.let { date to it }
    }
}

/** Kolik akcí se do dne nevešlo. Nula znamená, že se "+N dalších" nepíše. */
fun hiddenEventCount(dayEventCount: Int): Int = (dayEventCount - MAX_EVENTS_PER_DAY).coerceAtLeast(0)

/** "9:05" — hodina bez vycpávání, minuty s nulou. */
fun eventTimeLabel(dateTime: LocalDateTime): String =
    "${dateTime.hour}:${dateTime.minute.toString().padStart(2, '0')}"

/** Lekce kurzu se v kalendáři odlišuje barvou od jednorázové akce. */
enum class CalendarEventKind { SERIES_LESSON, ONE_OFF }

fun calendarEventKind(event: EventInstance): CalendarEventKind =
    if (event.seriesId != null) CalendarEventKind.SERIES_LESSON else CalendarEventKind.ONE_OFF

/** Index měsíce pro `AppStrings.monthName`, které počítá od nuly. */
fun monthNameIndex(month: LocalDate): Int = month.month.number - 1

/** Index dne v týdnu pro `AppStrings.shortDayName`, pondělí = 0. */
fun weekdayIndex(date: LocalDate): Int = date.dayOfWeek.isoDayNumber - 1
