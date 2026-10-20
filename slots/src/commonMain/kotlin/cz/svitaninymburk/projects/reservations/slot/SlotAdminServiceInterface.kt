package cz.svitaninymburk.projects.reservations.slot

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import dev.kilua.rpc.annotations.RpcService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Služba se svým rozvrhem — podklad pro admin detail. */
@Serializable
data class SlotServiceDetail(
    val service: SlotService,
    val rules: List<AvailabilityRule>,
    val exceptions: List<AvailabilityException>,
    /** Aktivní rezervace, které ještě neskončily; smazání služby je zruší. */
    val upcomingReservationCount: Int,
)

/** Aktivní rezervace úseku, jak ji ukazuje administrace (denní přehled, varování u rozvrhu). */
@Serializable
data class SlotReservationRow(
    val id: Uuid,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String?,
    val seatCount: Int,
    val status: Reservation.Status,
    val paymentType: PaymentType,
    val totalPrice: Double,
    val variableSymbol: String?,
    val customValues: Map<String, CustomFieldValue>,
)

/** Jeden den služby pro admina: kroky s obsazeností (bez uzávěrky a horizontu) a rezervace. */
@Serializable
data class SlotDayOverview(
    val date: LocalDate,
    val segments: List<SlotSegment>,
    /** Seřazené podle začátku. */
    val reservations: List<SlotReservationRow>,
)

@Serializable
sealed interface SlotAdminError {
    @Serializable data object ServiceNotFound : SlotAdminError
    @Serializable data class InvalidService(val problems: List<SlotServiceProblem>) : SlotAdminError
    @Serializable data class InvalidSchedule(val problems: List<ScheduleProblem>) : SlotAdminError
}

/** Správa hlídání. Routy jsou jen pro admina (jádro je pověsí pod `AdminAuthorization`). */
@RpcService
interface SlotAdminServiceInterface {
    /** Všechny služby včetně nepublikovaných, seřazené podle názvu. */
    suspend fun listServices(): List<SlotService>

    suspend fun getService(id: Uuid): Either<SlotAdminError, SlotServiceDetail>

    /** Založí službu; `id` v [service] se ignoruje. Vrací id nové služby. */
    suspend fun createService(service: SlotService): Either<SlotAdminError, Uuid>

    suspend fun updateService(service: SlotService): Either<SlotAdminError, Unit>

    suspend fun setPublished(id: Uuid, published: Boolean): Either<SlotAdminError, Unit>

    /**
     * Smaže službu i s rozvrhem. Jako smazání akce: všechny její nezrušené rezervace
     * se stornují, zákazníci dostanou oznámení a při [refund] kredit do peněženky.
     */
    suspend fun deleteService(id: Uuid, refund: Boolean): Either<SlotAdminError, Unit>

    /**
     * Které aktivní budoucí rezervace by po uložení rozvrhu ležely mimo okna.
     * Uložení je nezruší — admin je jen musí vidět, než potvrdí.
     */
    suspend fun previewSchedule(
        id: Uuid,
        rules: List<AvailabilityRule>,
        exceptions: List<AvailabilityException>,
    ): Either<SlotAdminError, List<SlotReservationRow>>

    /** Nahradí celý rozvrh (pravidla i výjimky). Rezervace neruší. */
    suspend fun saveSchedule(
        id: Uuid,
        rules: List<AvailabilityRule>,
        exceptions: List<AvailabilityException>,
    ): Either<SlotAdminError, Unit>

    suspend fun getDayOverview(id: Uuid, date: LocalDate): Either<SlotAdminError, SlotDayOverview>
}
