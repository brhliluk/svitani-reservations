package cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase

import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import kotlin.time.Instant
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Co se stane s penězi při zrušení celé rezervace právě teď. */
enum class CancellationPreview { REFUND_ELIGIBLE, WINDOW_PASSED, NOT_PAID }

/**
 * Uzávěrku (18:00 den předem) počítá server a posílá ji v ReservationDetail —
 * prohlížeč nemá databázi časových pásem, takže by ji v zóně návštěvníka spočítal
 * jinak, než jakou pak backend uplatní. Chybějící uzávěrka = akce už v systému
 * není, takže se chová jako po lhůtě.
 */
fun cancellationPreview(paidAmount: Double, deadline: Instant?, now: Instant): CancellationPreview = when {
    paidAmount <= 0.0 -> CancellationPreview.NOT_PAID
    deadline == null || now > deadline -> CancellationPreview.WINDOW_PASSED
    else -> CancellationPreview.REFUND_ELIGIBLE
}

// --- UseCase třídy (tenké, vrací Either) ---

class ReservationDetailQueries(private val service: ReservationServiceInterface) {
    suspend fun detail(id: Uuid) = service.getDetail(id)
    suspend fun seriesLessons(id: Uuid) = service.getSeriesLessons(id)
}

class ReservationDetailMutations(private val service: ReservationServiceInterface) {
    suspend fun cancelWhole(id: Uuid, walletCode: String?, force: Boolean) =
        service.cancelReservation(reservationId = id, instanceId = null, walletCode = walletCode, force = force)
}
