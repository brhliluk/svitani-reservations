package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private fun instanceTarget(allowMultipleSeats: Boolean, capacity: Int = 10, occupied: Int = 0) =
    ReservationTarget.Instance(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Akce",
            description = "",
            startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
            price = 100.0,
            capacity = capacity,
            occupiedSpots = occupied,
            allowMultipleSeats = allowMultipleSeats,
        )
    )

private fun seriesTarget(allowMultipleSeats: Boolean, capacity: Int = 10, occupied: Int = 0) =
    ReservationTarget.Series(
        EventSeries(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Kurz",
            description = "",
            price = 100.0,
            capacity = capacity,
            occupiedSpots = occupied,
            startDate = LocalDate(2099, 6, 1),
            endDate = LocalDate(2099, 7, 1),
            lessonCount = 4,
            allowMultipleSeats = allowMultipleSeats,
        )
    )

class SeatCountSpec {

    @Test
    fun targetExposesAllowMultipleSeatsFromInstance() {
        assertEquals(false, instanceTarget(allowMultipleSeats = false).allowMultipleSeats)
        assertEquals(true, instanceTarget(allowMultipleSeats = true).allowMultipleSeats)
    }

    @Test
    fun targetExposesAllowMultipleSeatsFromSeries() {
        assertEquals(false, seriesTarget(allowMultipleSeats = false).allowMultipleSeats)
        assertEquals(true, seriesTarget(allowMultipleSeats = true).allowMultipleSeats)
    }

    @Test
    fun clampForcesOneSeatWhenMultipleSeatsAreNotAllowed() {
        val target = instanceTarget(allowMultipleSeats = false)
        assertEquals(1, target.clampSeatCount(5))
        assertEquals(1, target.clampSeatCount(0))
        assertEquals(1, target.clampSeatCount(null))
    }

    @Test
    fun clampKeepsTypedValueWithinRemainingCapacityWhenAllowed() {
        val target = instanceTarget(allowMultipleSeats = true, capacity = 10, occupied = 4)
        assertEquals(3, target.clampSeatCount(3))
        assertEquals(6, target.clampSeatCount(6), "zbývá 6 míst")
        assertEquals(6, target.clampSeatCount(99), "nad kapacitu se sráží na zbývající počet")
        assertEquals(1, target.clampSeatCount(0), "nula se sráží na 1")
        assertEquals(1, target.clampSeatCount(null), "neplatný vstup padá na 1")
    }

    @Test
    fun clampDoesNotThrowOnFullEvent() {
        // Plná akce otevírá formulář náhradníka — zbývá 0 (u přeplněné i míň) míst.
        assertEquals(1, instanceTarget(allowMultipleSeats = true, capacity = 5, occupied = 5).clampSeatCount(3))
        assertEquals(1, instanceTarget(allowMultipleSeats = true, capacity = 5, occupied = 7).clampSeatCount(3))
    }

    @Test
    fun capacityIsNotReportedAsExceededWhenMultipleSeatsAreNotAllowed() {
        // Pole se vůbec nezobrazuje, takže varování o překročení kapacity nesmí vzniknout.
        assertEquals(false, instanceTarget(allowMultipleSeats = false, capacity = 2).exceedsRemainingCapacity(5))
        assertEquals(true, instanceTarget(allowMultipleSeats = true, capacity = 2).exceedsRemainingCapacity(5))
        assertEquals(false, instanceTarget(allowMultipleSeats = true, capacity = 2).exceedsRemainingCapacity(2))
    }

    @Test
    fun seriesClampAlsoForcesOneSeat() {
        assertEquals(1, seriesTarget(allowMultipleSeats = false).clampSeatCount(4))
        assertEquals(4, seriesTarget(allowMultipleSeats = true).clampSeatCount(4))
    }
}
