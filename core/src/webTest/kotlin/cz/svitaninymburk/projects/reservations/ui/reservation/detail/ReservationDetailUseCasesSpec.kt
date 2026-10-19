package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.CancellationPreview
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.cancellationPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Náhled kreditu u zrušení celé rezervace. Uzávěrku počítá server a posílá ji
 * v ReservationDetail — prohlížeč nemá databázi časových pásem.
 */
class ReservationDetailUseCasesSpec {

    private val deadline = Instant.parse("2099-06-09T16:00:00Z")
    private val beforeDeadline = Instant.parse("2099-06-08T10:00:00Z")
    private val afterDeadline = Instant.parse("2099-06-10T06:00:00Z")

    @Test
    fun unpaidReservationGetsNoCredit() {
        assertEquals(
            CancellationPreview.NOT_PAID,
            cancellationPreview(paidAmount = 0.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun timelyCancellationOfPaidReservationRefundsCredit() {
        assertEquals(
            CancellationPreview.REFUND_ELIGIBLE,
            cancellationPreview(paidAmount = 500.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun cancellationRefundsNothingOnceEverythingWentToLessonCredits() {
        assertEquals(
            CancellationPreview.NOTHING_LEFT,
            cancellationPreview(paidAmount = 500.0, refundableAmount = 0.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun noCreditAfterDeadline() {
        assertEquals(
            CancellationPreview.WINDOW_PASSED,
            cancellationPreview(paidAmount = 500.0, deadline = deadline, now = afterDeadline),
        )
    }

    @Test
    fun deletedEventBehavesLikeAfterDeadline() {
        // Bez uzávěrky (akce už v systému není) není z čeho nárok odvodit.
        assertEquals(
            CancellationPreview.WINDOW_PASSED,
            cancellationPreview(paidAmount = 500.0, deadline = null, now = beforeDeadline),
        )
    }
}
