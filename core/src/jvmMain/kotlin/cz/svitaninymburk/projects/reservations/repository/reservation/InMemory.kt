package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.ReservationKind
import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.LocalDateTime
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

    override suspend fun findActiveSlotReservations(serviceId: Uuid, from: LocalDateTime, to: LocalDateTime): List<Reservation> =
        reservations.values.filter { reservation ->
            val ref = reservation.reference
            ref is Reference.Slot && ref.id == serviceId && reservation.status !in INACTIVE_RESERVATION_STATUSES &&
                ref.start < to && ref.end > from
        }

    override suspend fun findActiveByReferenceIdsAndEmail(
        referenceIds: List<Uuid>,
        email: String,
    ): List<Reservation> = reservations.values.filter {
        it.reference.id in referenceIds &&
            it.contactEmail.trim().lowercase() == email &&
            it.status in ACTIVE_SIGNUP_STATUSES
    }

    override suspend fun findUnclaimedByEmail(email: String): List<Reservation> = reservations.values.filter {
        it.contactEmail.trim().lowercase() == email &&
            it.registeredUserId == null &&
            it.status in ACTIVE_SIGNUP_STATUSES
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
            .filter { it.status !in INACTIVE_RESERVATION_STATUSES }
            .sumOf { it.seatCount }
    }

    override suspend fun getAll(userId: Uuid): List<Reservation> {
        return reservations.values.filter { it.registeredUserId == userId }
    }

    override suspend fun findAll(): List<Reservation> {
        return reservations.values.toList()
    }

    private fun adminList(searchQuery: String?, includeCancelled: Boolean, kind: ReservationKind?): List<Reservation> {
        val q = searchQuery?.takeIf { it.isNotBlank() }?.lowercase()
        return reservations.values.filter {
            (includeCancelled || (it.status != Reservation.Status.CANCELLED && it.status != Reservation.Status.REJECTED)) &&
                (kind == null || it.reference.kind == kind) &&
                (q == null || it.contactName.lowercase().contains(q) ||
                    it.contactEmail.lowercase().contains(q) ||
                    it.variableSymbol?.lowercase()?.contains(q) == true)
        }
    }

    override suspend fun findAllPaged(searchQuery: String?, page: Int, pageSize: Int, includeCancelled: Boolean, kind: ReservationKind?): List<Reservation> =
        adminList(searchQuery, includeCancelled, kind)
            .sortedByDescending { it.createdAt }
            .drop(page * pageSize)
            .take(pageSize)

    override suspend fun countAll(searchQuery: String?, includeCancelled: Boolean, kind: ReservationKind?): Long =
        adminList(searchQuery, includeCancelled, kind).size.toLong()

    override suspend fun existsByVariableSymbol(variableSymbol: String): Boolean {
        return reservations.values.any { it.variableSymbol == variableSymbol }
    }

    override suspend fun updateStatus(id: Uuid, status: Reservation.Status): Boolean {
        val reservation = reservations[id] ?: return false
        reservations[id] = reservation.copy(status = status)
        return true
    }

    override suspend fun linkToUser(id: Uuid, userId: Uuid): Boolean {
        val reservation = reservations[id] ?: return false
        if (reservation.registeredUserId != null) return false
        reservations[id] = reservation.copy(registeredUserId = userId)
        return true
    }
}

class InMemorySeriesLessonOptOutRepository : SeriesLessonOptOutRepository {
    private val optOuts = ConcurrentHashMap<Uuid, SeriesLessonOptOut>()

    override suspend fun save(optOut: SeriesLessonOptOut): SeriesLessonOptOut {
        optOuts[optOut.id] = optOut
        return optOut
    }

    override suspend fun saveIfAbsent(optOut: SeriesLessonOptOut): SeriesLessonOptOut? = synchronized(optOuts) {
        val duplicate = optOuts.values.any {
            it.reservationId == optOut.reservationId && it.instanceId == optOut.instanceId
        }
        if (duplicate) return@synchronized null
        optOuts[optOut.id] = optOut
        optOut
    }

    override suspend fun findByReservationAndInstance(reservationId: Uuid, instanceId: Uuid): SeriesLessonOptOut? =
        optOuts.values.find { it.reservationId == reservationId && it.instanceId == instanceId }

    override suspend fun findByReservation(reservationId: Uuid): List<SeriesLessonOptOut> =
        optOuts.values.filter { it.reservationId == reservationId }

    override suspend fun findByInstance(instanceId: Uuid): List<SeriesLessonOptOut> =
        optOuts.values.filter { it.instanceId == instanceId }

    override suspend fun updateRefundedAmount(id: Uuid, amount: Double) {
        optOuts.computeIfPresent(id) { _, optOut -> optOut.copy(refundedAmount = amount) }
    }

    override suspend fun delete(reservationId: Uuid, instanceId: Uuid): Boolean = synchronized(optOuts) {
        val found = optOuts.values.filter { it.reservationId == reservationId && it.instanceId == instanceId }
        found.forEach { optOuts.remove(it.id) }
        found.isNotEmpty()
    }
}

private val Reference.kind: ReservationKind
    get() = when (this) {
        is Reference.Instance -> ReservationKind.INSTANCE
        is Reference.Series -> ReservationKind.SERIES
        is Reference.Slot -> ReservationKind.SLOT
    }
