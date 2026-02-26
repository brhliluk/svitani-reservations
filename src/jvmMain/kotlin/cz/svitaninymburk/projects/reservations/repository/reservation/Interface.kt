package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.uuid.Uuid

interface ReservationRepository {
    suspend fun save(reservation: Reservation): Reservation
    suspend fun findById(id: Uuid): Reservation?
    suspend fun findAwaitingPayment(vs: String): Reservation?
    suspend fun hasPendingReservations(): Boolean
    suspend fun countSeats(id: Uuid): Int
    suspend fun getAll(userId: Uuid): List<Reservation>
    suspend fun findAll(): List<Reservation>
    suspend fun existsByVariableSymbol(variableSymbol: String): Boolean
}