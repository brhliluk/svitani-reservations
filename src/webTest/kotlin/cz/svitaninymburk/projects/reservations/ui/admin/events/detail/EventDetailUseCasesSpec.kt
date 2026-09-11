package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.toggleDropInRequest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private fun sampleInstance(isDropIn: Boolean = false, title: String = "Lekce 1") = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = title,
    description = "Popis",
    startDateTime = LocalDateTime(2026, 1, 1, 10, 0),
    endDateTime = LocalDateTime(2026, 1, 1, 11, 0),
    price = 100.0,
    capacity = 10,
    isDropIn = isDropIn,
)

class EventDetailUseCasesSpec {

    @Test
    fun toggleDropInRequestFlipsIsDropInAndKeepsRest() {
        val lesson = sampleInstance(isDropIn = false, title = "Lekce 1")
        val req = toggleDropInRequest(lesson)
        assertEquals(true, req.isDropIn)
        assertEquals(lesson.title, req.title)
        assertEquals(lesson.startDateTime, req.startDateTime)
        assertEquals(lesson.capacity, req.capacity)
        assertEquals(lesson.customFields, req.customFields)
    }
}
