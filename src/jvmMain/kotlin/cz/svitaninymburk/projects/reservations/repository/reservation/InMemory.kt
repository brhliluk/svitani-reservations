package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid


class InMemoryReservationRepository : ReservationRepository {
    private val reservations = ConcurrentHashMap<Uuid, Reservation>()

    override suspend fun save(reservation: Reservation): Reservation {
        val id = reservation.id
        val newRes = reservation.copy(id = id)
        reservations[id] = newRes
        return newRes
    }

    override suspend fun findById(id: Uuid): Reservation? = reservations[id]

    override suspend fun findAwaitingPayment(vs: String): Reservation? {
        return reservations.values.find { it.variableSymbol == vs && it.status == Reservation.Status.PENDING_PAYMENT }
    }

    override suspend fun hasPendingReservations(): Boolean {
        return reservations.values.any { it.status == Reservation.Status.PENDING_PAYMENT }
    }

    override suspend fun countSeats(id: Uuid): Int {
        return reservations.values
            .filter { it.reference.id == id }
            .filter { it.status != Reservation.Status.CANCELLED && it.status != Reservation.Status.REJECTED }
            .sumOf { it.seatCount }
    }

    override suspend fun getAll(userId: Uuid): List<Reservation> {
        return reservations.values.filter { it.userId == userId }
    }

    override suspend fun findAll(): List<Reservation> {
        return reservations.values.toList()
    }

    override suspend fun existsByVariableSymbol(variableSymbol: String): Boolean {
        return reservations.values.any { it.variableSymbol == variableSymbol }
    }
}