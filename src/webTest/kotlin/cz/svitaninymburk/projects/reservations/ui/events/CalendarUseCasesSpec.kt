package cz.svitaninymburk.projects.reservations.ui.events

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.events.usecase.CalendarEventKind
import cz.svitaninymburk.projects.reservations.ui.events.usecase.MAX_EVENTS_PER_DAY
import cz.svitaninymburk.projects.reservations.ui.events.usecase.calendarEventKind
import cz.svitaninymburk.projects.reservations.ui.events.usecase.calendarGrid
import cz.svitaninymburk.projects.reservations.ui.events.usecase.daysWithEvents
import cz.svitaninymburk.projects.reservations.util.hourMinute
import cz.svitaninymburk.projects.reservations.ui.events.usecase.eventsOn
import cz.svitaninymburk.projects.reservations.ui.events.usecase.hiddenEventCount
import cz.svitaninymburk.projects.reservations.ui.events.usecase.monthNameIndex
import cz.svitaninymburk.projects.reservations.ui.events.usecase.weekdayIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private fun event(
    title: String,
    at: LocalDateTime,
    seriesId: Uuid? = null,
) = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    seriesId = seriesId,
    title = title,
    description = "",
    startDateTime = at,
    endDateTime = at,
    price = 0.0,
    capacity = 10,
    occupiedSpots = 0,
)

class CalendarGridSpec {

    @Test
    fun monthStartingOnMondayHasNoLeadingBlanks() {
        // 1. 6. 2026 je pondělí.
        val grid = calendarGrid(LocalDate(2026, 6, 1))
        assertEquals(0, grid.leadingBlanks)
        assertEquals(30, grid.daysInMonth)
    }

    @Test
    fun leadingBlanksCountFromMonday() {
        // 1. 9. 2026 je úterý → jedna prázdná buňka (pondělí).
        val grid = calendarGrid(LocalDate(2026, 9, 1))
        assertEquals(1, grid.leadingBlanks)
        assertEquals(30, grid.daysInMonth)

        // 1. 2. 2024 je čtvrtek → tři prázdné buňky.
        assertEquals(3, calendarGrid(LocalDate(2024, 2, 1)).leadingBlanks)

        // 1. 2. 2026 je nedělí → šest, nejvíc, co může být.
        assertEquals(6, calendarGrid(LocalDate(2026, 2, 1)).leadingBlanks)
    }

    @Test
    fun gridAlwaysEndsOnAWholeWeek() {
        val months = listOf(
            LocalDate(2026, 1, 1), LocalDate(2026, 2, 1), LocalDate(2026, 6, 1),
            LocalDate(2026, 9, 1), LocalDate(2026, 12, 1), LocalDate(2024, 2, 1),
        )
        months.forEach { month ->
            val grid = calendarGrid(month)
            assertEquals(0, grid.totalCells % 7, "měsíc $month nekončí na celém týdnu")
            assertTrue(grid.trailingBlanks in 0..6, "měsíc $month má ${grid.trailingBlanks} buněk navíc")
        }
    }

    @Test
    fun monthThatFillsTheGridExactlyGetsNoExtraWeek() {
        // Únor 2021: začíná v pondělí a má 28 dní, tedy přesně čtyři týdny.
        // Bez druhého `% 7` by tady přibyl celý prázdný týden.
        val grid = calendarGrid(LocalDate(2021, 2, 1))
        assertEquals(0, grid.leadingBlanks)
        assertEquals(28, grid.daysInMonth)
        assertEquals(0, grid.trailingBlanks)
        assertEquals(28, grid.totalCells)
    }

    @Test
    fun noMonthOverTenYearsGetsAnEmptyTrailingWeek() {
        for (year in 2020..2030) {
            for (m in 1..12) {
                val grid = calendarGrid(LocalDate(year, m, 1))
                assertEquals(0, grid.totalCells % 7, "$year-$m nekončí na celém týdnu")
                assertTrue(grid.trailingBlanks < 7, "$year-$m má celý prázdný týden navíc")
            }
        }
    }

    @Test
    fun leapYearFebruaryHas29Days() {
        assertEquals(29, calendarGrid(LocalDate(2024, 2, 1)).daysInMonth)
        assertEquals(28, calendarGrid(LocalDate(2026, 2, 1)).daysInMonth)
    }
}

class CalendarEventsSpec {

    private val day = LocalDate(2026, 9, 10)

    @Test
    fun eventsOnADayComeBackSortedByTime() {
        val events = listOf(
            event("odpoledne", LocalDateTime(2026, 9, 10, 15, 0)),
            event("ráno", LocalDateTime(2026, 9, 10, 9, 30)),
            event("jiný den", LocalDateTime(2026, 9, 11, 8, 0)),
        )
        assertEquals(listOf("ráno", "odpoledne"), eventsOn(events, day).map { it.title })
    }

    @Test
    fun emptyDayComesBackEmpty() {
        val events = listOf(event("jiný den", LocalDateTime(2026, 9, 11, 8, 0)))
        assertEquals(emptyList(), eventsOn(events, day))
    }

    @Test
    fun agendaSkipsDaysWithoutEvents() {
        val events = listOf(
            event("desátého", LocalDateTime(2026, 9, 10, 9, 0)),
            event("dvacátého", LocalDateTime(2026, 9, 20, 9, 0)),
            event("desátého podruhé", LocalDateTime(2026, 9, 10, 11, 0)),
        )
        val agenda = daysWithEvents(events, LocalDate(2026, 9, 1))
        assertEquals(listOf(LocalDate(2026, 9, 10), LocalDate(2026, 9, 20)), agenda.map { it.first })
        assertEquals(2, agenda.first().second.size)
    }

    @Test
    fun agendaIgnoresEventsFromOtherMonths() {
        val events = listOf(
            event("v září", LocalDateTime(2026, 9, 10, 9, 0)),
            event("v říjnu", LocalDateTime(2026, 10, 10, 9, 0)),
        )
        val agenda = daysWithEvents(events, LocalDate(2026, 9, 1))
        assertEquals(listOf("v září"), agenda.flatMap { it.second }.map { it.title })
    }

    @Test
    fun onlyEventsBeyondTheLimitAreHidden() {
        assertEquals(0, hiddenEventCount(0))
        assertEquals(0, hiddenEventCount(MAX_EVENTS_PER_DAY))
        assertEquals(1, hiddenEventCount(MAX_EVENTS_PER_DAY + 1))
        assertEquals(5, hiddenEventCount(MAX_EVENTS_PER_DAY + 5))
    }

    @Test
    fun seriesLessonIsDistinguishedFromAOneOffEvent() {
        val lesson = event("lekce", LocalDateTime(2026, 9, 10, 9, 0), seriesId = Uuid.random())
        val oneOff = event("jednorázovka", LocalDateTime(2026, 9, 10, 9, 0))
        assertEquals(CalendarEventKind.SERIES_LESSON, calendarEventKind(lesson))
        assertEquals(CalendarEventKind.ONE_OFF, calendarEventKind(oneOff))
    }
}

class CalendarLabelsSpec {

    @Test
    fun minutesArePaddedButHoursAreNot() {
        assertEquals("9:05", LocalDateTime(2026, 9, 10, 9, 5).time.hourMinute)
        assertEquals("9:00", LocalDateTime(2026, 9, 10, 9, 0).time.hourMinute)
        assertEquals("18:30", LocalDateTime(2026, 9, 10, 18, 30).time.hourMinute)
        assertEquals("0:00", LocalDateTime(2026, 9, 10, 0, 0).time.hourMinute)
    }

    @Test
    fun monthAndWeekdayIndexesAreZeroBased() {
        // AppStrings.monthName a shortDayName počítají od nuly, pondělí = 0.
        assertEquals(0, monthNameIndex(LocalDate(2026, 1, 1)))
        assertEquals(11, monthNameIndex(LocalDate(2026, 12, 1)))
        assertEquals(0, weekdayIndex(LocalDate(2026, 6, 1)))   // pondělí
        assertEquals(6, weekdayIndex(LocalDate(2026, 6, 7)))   // nedělí
    }
}
