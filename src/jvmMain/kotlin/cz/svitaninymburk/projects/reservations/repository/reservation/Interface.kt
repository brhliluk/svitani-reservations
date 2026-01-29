package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reservation

interface ReservationRepository {
    suspend fun save(reservation: Reservation): Reservation
    suspend fun findById(id: String): Reservation?
    suspend fun findAwaitingPayment(vs: String): Reservation?
    suspend fun hasPendingReservations(): Boolean
    suspend fun countSeats(id: String): Int
    suspend fun getAll(userId: String): List<Reservation>
}