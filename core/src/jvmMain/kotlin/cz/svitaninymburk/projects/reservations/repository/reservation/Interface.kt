package cz.svitaninymburk.projects.reservations.repository.reservation

import cz.svitaninymburk.projects.reservations.reservation.ReservationKind
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.LocalDateTime
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

    /** Víc rezervací naráz; chybějící id se tiše vynechají. */
    suspend fun findByIds(ids: Collection<Uuid>): List<Reservation> = ids.mapNotNull { findById(it) }
    suspend fun findByReference(reference: Reference): List<Reservation>

    /**
     * Aktivní rezervace úseků služby [serviceId], které se překrývají s `[from, to)`.
     * Z nich se počítá obsazenost hlídání — čítač míst úseky nemají.
     */
    suspend fun findActiveSlotReservations(serviceId: Uuid, from: LocalDateTime, to: LocalDateTime): List<Reservation>

    /**
     * Aktivní přihlášky daného e-mailu na kterýkoli z uvedených cílů. Jedním dotazem,
     * protože u kurzu se ptáme i na všechny jeho lekce. E-mail se porovnává bez ohledu
     * na velikost písmen — implementace dostává už osekaný a zmenšený.
     */
    suspend fun findActiveByReferenceIdsAndEmail(referenceIds: List<Uuid>, email: String): List<Reservation>
    /**
     * Aktivní rezervace bez účtu na danou adresu — podklad pro nabídku „přidat k účtu“
     * po registraci. E-mail se porovnává bez ohledu na velikost písmen, implementace
     * dostává už osekaný a zmenšený.
     */
    suspend fun findUnclaimedByEmail(email: String): List<Reservation>

    suspend fun findAwaitingPayment(vs: String): Reservation?
    suspend fun hasPendingReservations(): Boolean
    suspend fun countSeats(id: Uuid): Int
    suspend fun getAll(userId: Uuid): List<Reservation>
    suspend fun findAll(): List<Reservation>
    /** [kind] = jen rezervace na akce, kurzy, nebo úseky; null = všechny. */
    suspend fun findAllPaged(searchQuery: String?, page: Int, pageSize: Int, includeCancelled: Boolean = false, kind: ReservationKind? = null): List<Reservation>
    suspend fun countAll(searchQuery: String?, includeCancelled: Boolean = false, kind: ReservationKind? = null): Long
    suspend fun existsByVariableSymbol(variableSymbol: String): Boolean
    suspend fun updateStatus(id: Uuid, status: Reservation.Status): Boolean

    /**
     * Naváže rezervaci bez účtu na uživatele. Úzký UPDATE, ne [save] — ten přepisuje
     * všechny sloupce z paměťové kopie a smazal by souběžné spárování platby.
     * Podmínka `registered_user_id IS NULL` je zároveň zámek proti dvojímu přivlastnění:
     * vrátí `false`, když rezervaci mezitím zabral někdo jiný.
     */
    suspend fun linkToUser(id: Uuid, userId: Uuid): Boolean
}