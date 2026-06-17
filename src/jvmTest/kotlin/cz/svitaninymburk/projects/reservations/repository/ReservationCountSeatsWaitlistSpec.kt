package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ReservationCountSeatsWaitlistSpec {

    private fun reservation(refId: Uuid, status: Reservation.Status, seats: Int = 1) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(refId),
        contactName = "X", contactEmail = "x@test.com",
        seatCount = seats, totalPrice = 0.0,
        status = status, createdAt = Clock.System.now(),
        customValues = emptyMap(), paymentType = PaymentInfo.Type.ON_SITE,
    )

    @Test
    fun `countSeats excludes waitlisted reservations`() = runBlocking {
        val repo = InMemoryReservationRepository()
        val eventId = Uuid.random()
        repo.save(reservation(eventId, Reservation.Status.CONFIRMED, seats = 2))
        repo.save(reservation(eventId, Reservation.Status.PENDING_PAYMENT, seats = 1))
        repo.save(reservation(eventId, Reservation.Status.WAITLISTED, seats = 1))
        repo.save(reservation(eventId, Reservation.Status.CANCELLED, seats = 5))

        // CONFIRMED(2) + PENDING(1) = 3; WAITLISTED and CANCELLED excluded.
        assertEquals(3, repo.countSeats(eventId))
    }
}
