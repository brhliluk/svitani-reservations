package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.uuid.Uuid

/**
 * Stavy, ve kterých je člověk na akci přihlášený. Pozor, je to **jiná osa** než
 * [INACTIVE_RESERVATION_STATUSES][cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES]:
 * ta říká „drží místo“, a čekatel v pořadníku ho nedrží. Tahle říká „už je přihlášený“,
 * a čekatel v pořadníku přihlášený je — když se pokusí rezervovat znovu, chceme ho varovat.
 */
val ACTIVE_SIGNUP_STATUSES: List<Reservation.Status> = listOf(
    Reservation.Status.PENDING_PAYMENT,
    Reservation.Status.CONFIRMED,
    Reservation.Status.WAITLISTED,
)

interface ReservationRepository {
    suspend fun save(reservation: Reservation): Reservation
    suspend fun findById(id: Uuid): Reservation?
    suspend fun findByReference(reference: Reference): List<Reservation>

    /**
     * Aktivní přihlášky daného e-mailu na kterýkoli z uvedených cílů. Jedním dotazem,
     * protože u kurzu se ptáme i na všechny jeho lekce. E-mail se porovnává bez ohledu
     * na velikost písmen — implementace dostává už osekaný a zmenšený.
     */
    suspend fun findActiveByReferenceIdsAndEmail(referenceIds: List<Uuid>, email: String): List<Reservation>
    suspend fun findAwaitingPayment(vs: String): Reservation?
    suspend fun hasPendingReservations(): Boolean
    suspend fun countSeats(id: Uuid): Int
    suspend fun getAll(userId: Uuid): List<Reservation>
    suspend fun findAll(): List<Reservation>
    suspend fun findAllPaged(searchQuery: String?, page: Int, pageSize: Int, includeCancelled: Boolean = false): List<Reservation>
    suspend fun countAll(searchQuery: String?, includeCancelled: Boolean = false): Long
    suspend fun existsByVariableSymbol(variableSymbol: String): Boolean
    suspend fun updateStatus(id: Uuid, status: Reservation.Status): Boolean
}