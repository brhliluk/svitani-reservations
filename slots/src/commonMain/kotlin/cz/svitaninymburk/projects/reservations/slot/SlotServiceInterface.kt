package cz.svitaninymburk.projects.reservations.slot

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationRequestData
import dev.kilua.rpc.annotations.RpcService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateSlotReservationRequest(
    val serviceId: Uuid,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Počet dětí. */
    override val seatCount: Int = 1,
    override val contactName: String,
    override val contactEmail: String,
    override val contactPhone: String,
    override val paymentType: PaymentType,
    override val customValues: Map<String, CustomFieldValue>,
    override val locale: String = "cs",
    val walletCode: String? = null,
    /** Viz stejné pole u `CreateInstanceReservationRequest`. */
    val acknowledgedDuplicate: Boolean = false,
) : ReservationRequestData

/**
 * Veřejná část hlídání. Jde i bez přihlášení; nepublikované služby vidí
 * a rezervuje jen admin, stejně jako nepublikované akce.
 */
@RpcService
interface SlotServiceInterface {
    /** Služby, které volající smí rezervovat, seřazené podle názvu. */
    suspend fun listServices(): List<SlotService>

    /**
     * Volná místa po krocích ve dnech [from]–[to]. Rozsah se ořízne na dnešek a horizont
     * služby a vrací se jen kroky, které jde ještě rezervovat (po uzávěrce, v horizontu).
     * Neznámá nebo skrytá služba = prázdný seznam.
     */
    suspend fun getAvailability(serviceId: Uuid, from: LocalDate, to: LocalDate): List<DayAvailability>

    suspend fun reserveSlot(
        request: CreateSlotReservationRequest,
        userId: Uuid?,
    ): Either<ReservationError.CreateReservation, Reservation>
}
