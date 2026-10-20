package cz.svitaninymburk.projects.reservations.slot.ui.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.slot.CreateSlotReservationRequest
import cz.svitaninymburk.projects.reservations.slot.SlotServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

fun ReservationFormData.toCreateSlotReservationRequest(
    target: ReservationTarget.Slot,
    acknowledgedDuplicate: Boolean = false,
): CreateSlotReservationRequest = CreateSlotReservationRequest(
    serviceId = target.serviceId,
    start = target.startDateTime,
    end = target.endDateTime,
    seatCount = seats,
    contactName = "$name $surname",
    contactEmail = email,
    contactPhone = phone,
    paymentType = paymentType,
    customValues = customValues,
    locale = locale,
    walletCode = walletCode,
    acknowledgedDuplicate = acknowledgedDuplicate,
)

/**
 * Odmítnutí, po kterém už vybraný úsek nemusí platit (mezitím ho někdo zabral, prošla
 * uzávěrka, admin změnil rozvrh). Stránka pak zavře formulář a načte volná místa znovu.
 * Ostatní chyby (platba, peněženka…) se opraví ve formuláři, ten proto zůstane otevřený.
 */
fun invalidatesSelection(error: ReservationError.CreateReservation): Boolean = when (error) {
    ReservationError.SlotCapacityExceeded,
    ReservationError.SlotOutsideOpeningHours,
    ReservationError.SlotBeyondBookingHorizon,
    ReservationError.SlotNotAligned,
    ReservationError.SlotDurationOutOfRange,
    ReservationError.ReservationDeadlinePassed,
    ReservationError.EventAlreadyStarted,
    ReservationError.InvalidSeatCount,
    ReservationError.ReservationNotFound -> true
    else -> false
}

// --- UseCase třídy (tenké) ---

class SlotQueries(private val rpc: SlotServiceInterface) {
    suspend fun services() = rpc.listServices()
    suspend fun availability(serviceId: Uuid, range: ClosedRange<LocalDate>) =
        rpc.getAvailability(serviceId, range.start, range.endInclusive)
}

/** Odeslání formuláře pro úsek; jádro ho dostane jako `slotSubmitter` do `ReservationSubmitUseCase`. */
class SlotSubmitter(private val rpc: SlotServiceInterface) : ReservationSubmitter {
    override suspend fun submit(
        target: ReservationTarget,
        formData: ReservationFormData,
        userId: Uuid?,
        acknowledgedDuplicate: Boolean,
    ): Either<ReservationError.CreateReservation, Reservation> {
        val slot = target as? ReservationTarget.Slot ?: return Either.Left(ReservationError.ReservationNotFound)
        return rpc.reserveSlot(formData.toCreateSlotReservationRequest(slot, acknowledgedDuplicate), userId)
    }
}
