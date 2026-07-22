package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.ReservationCall
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.reservationCallOf
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.toggleDropInRequest
import kotlinx.datetime.LocalDate
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

private fun instanceTarget() = ReservationTarget.Instance(sampleInstance())

private fun seriesTarget() = ReservationTarget.Series(
    EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Serie 1",
        description = "Popis",
        price = 100.0,
        capacity = 10,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 3, 1),
        lessonCount = 5,
    )
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

    @Test
    fun reservationCallRoutesByTargetAndWaitlist() {
        assertEquals(ReservationCall.InstanceReserve, reservationCallOf(instanceTarget(), asWaitlist = false))
        assertEquals(ReservationCall.InstanceWaitlist, reservationCallOf(instanceTarget(), asWaitlist = true))
        assertEquals(ReservationCall.SeriesReserve, reservationCallOf(seriesTarget(), asWaitlist = false))
        assertEquals(ReservationCall.SeriesWaitlist, reservationCallOf(seriesTarget(), asWaitlist = true))
    }
}
