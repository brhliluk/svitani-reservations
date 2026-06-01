package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
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
    override suspend fun findByReference(reference: Reference): List<Reservation> {
        return reservations.filterValues { it.reference == reference }.values.toList()
    }

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
        return reservations.values.filter { it.registeredUserId == userId }
    }

    override suspend fun findAll(): List<Reservation> {
        return reservations.values.toList()
    }

    override suspend fun findAllPaged(searchQuery: String?, page: Int, pageSize: Int): List<Reservation> {
        var result = reservations.values.toList()
        if (!searchQuery.isNullOrBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter {
                it.contactName.lowercase().contains(q) ||
                it.contactEmail.lowercase().contains(q) ||
                it.variableSymbol?.lowercase()?.contains(q) == true
            }
        }
        return result.sortedByDescending { it.createdAt }
                     .drop(page * pageSize)
                     .take(pageSize)
    }

    override suspend fun countAll(searchQuery: String?): Long {
        if (searchQuery.isNullOrBlank()) return reservations.size.toLong()
        val q = searchQuery.lowercase()
        return reservations.values.count {
            it.contactName.lowercase().contains(q) ||
            it.contactEmail.lowercase().contains(q) ||
            it.variableSymbol?.lowercase()?.contains(q) == true
        }.toLong()
    }

    override suspend fun existsByVariableSymbol(variableSymbol: String): Boolean {
        return reservations.values.any { it.variableSymbol == variableSymbol }
    }

    override suspend fun updateStatus(id: Uuid, status: Reservation.Status): Boolean {
        val reservation = reservations[id] ?: return false
        reservations[id] = reservation.copy(status = status)
        return true
    }
}

class InMemorySeriesLessonOptOutRepository : SeriesLessonOptOutRepository {
    private val optOuts = ConcurrentHashMap<Uuid, SeriesLessonOptOut>()

    override suspend fun save(optOut: SeriesLessonOptOut): SeriesLessonOptOut {
        optOuts[optOut.id] = optOut
        return optOut
    }

    override suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut? =
        optOuts.values.find { it.reservationId == reservationId && it.instanceId == instanceId }

    override suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut> =
        optOuts.values.filter { it.reservationId == reservationId }
}