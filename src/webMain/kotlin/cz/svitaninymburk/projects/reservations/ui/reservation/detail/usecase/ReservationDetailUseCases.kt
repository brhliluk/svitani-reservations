package cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase

import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import kotlin.time.Instant
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Co se stane s penězi při zrušení celé rezervace právě teď. */
enum class CancellationPreview {
    REFUND_ELIGIBLE,
    WINDOW_PASSED,
    NOT_PAID,

    /** Zaplaceno, ale všechno už odešlo za omluvenky a zrušené lekce. */
    NOTHING_LEFT,
}

/**
 * Uzávěrku (18:00 den předem) počítá server a posílá ji v ReservationDetail —
 * prohlížeč nemá databázi časových pásem, takže by ji v zóně návštěvníka spočítal
 * jinak, než jakou pak backend uplatní. Chybějící uzávěrka = akce už v systému
 * není, takže se chová jako po lhůtě.
 */
fun cancellationPreview(
    paidAmount: Double,
    refundableAmount: Double = paidAmount,
    deadline: Instant?,
    now: Instant,
): CancellationPreview = when {
    paidAmount <= 0.0 -> CancellationPreview.NOT_PAID
    deadline == null || now > deadline -> CancellationPreview.WINDOW_PASSED
    refundableAmount <= 0.0 -> CancellationPreview.NOTHING_LEFT
    else -> CancellationPreview.REFUND_ELIGIBLE
}

// --- UseCase třídy (tenké, vrací Either) ---

class ReservationDetailQueries(private val service: ReservationServiceInterface) {
    suspend fun detail(id: Uuid) = service.getDetail(id)
    suspend fun seriesLessons(id: Uuid) = service.getSeriesLessons(id)
}

class ReservationDetailMutations(
    private val service: ReservationServiceInterface,
    private val authenticated: AuthenticatedReservationServiceInterface,
) {
    suspend fun cancelWhole(id: Uuid, walletCode: String?, force: Boolean) =
        service.cancelReservation(reservationId = id, instanceId = null, walletCode = walletCode, force = force)

    /** Přivlastnění visí na službě za tvrdým JWT — identitu si server bere z tokenu. */
    suspend fun claim(id: Uuid) = authenticated.claimReservation(id)
}
