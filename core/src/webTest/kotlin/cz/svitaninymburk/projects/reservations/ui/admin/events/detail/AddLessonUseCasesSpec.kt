package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.AddLessonInput
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.addLessonDefaults
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.parseAddLessonInput
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private fun lesson(start: LocalDateTime, end: LocalDateTime) = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = "Lekce",
    description = "",
    startDateTime = start,
    endDateTime = end,
    price = 100.0,
    capacity = 10,
)

class AddLessonUseCasesSpec {

    @Test
    fun defaultsOfferTheSameSlotNextWeek() {
        val defaults = addLessonDefaults(
            lesson(LocalDateTime(2026, 3, 2, 9, 5), LocalDateTime(2026, 3, 2, 10, 30)),
        )
        assertEquals("2026-03-09", defaults.date)
        assertEquals("09:05", defaults.startTime)
        assertEquals("10:30", defaults.endTime)
    }

    @Test
    fun defaultsAreEmptyForTheFirstLesson() {
        val defaults = addLessonDefaults(null)
        assertEquals("", defaults.date)
        assertEquals("", defaults.startTime)
        assertEquals("", defaults.endTime)
    }

    @Test
    fun validInputCombinesDateWithBothTimes() {
        val input = parseAddLessonInput("2026-03-09", "09:05", "10:30") as AddLessonInput.Valid
        assertEquals(LocalDateTime(2026, 3, 9, 9, 5), input.startDateTime)
        assertEquals(LocalDateTime(2026, 3, 9, 10, 30), input.endDateTime)
    }

    @Test
    fun blankOrMalformedInputIsRejected() {
        assertTrue(parseAddLessonInput("", "09:05", "10:30") is AddLessonInput.Invalid)
        assertTrue(parseAddLessonInput("2026-03-09", "", "10:30") is AddLessonInput.Invalid)
        assertTrue(parseAddLessonInput("2026-03-09", "09:05", "") is AddLessonInput.Invalid)
        assertTrue(parseAddLessonInput("9. 3. 2026", "09:05", "10:30") is AddLessonInput.Invalid)
    }

    @Test
    fun lessonCannotEndBeforeOrWhenItStarts() {
        assertTrue(parseAddLessonInput("2026-03-09", "10:30", "09:05") is AddLessonInput.Invalid)
        assertTrue(parseAddLessonInput("2026-03-09", "10:30", "10:30") is AddLessonInput.Invalid)
    }
}
