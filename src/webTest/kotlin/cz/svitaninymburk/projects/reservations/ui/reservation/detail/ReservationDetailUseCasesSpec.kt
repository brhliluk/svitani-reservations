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
    fun `nezaplacena rezervace kredit nedostane`() {
        assertEquals(
            CancellationPreview.NOT_PAID,
            cancellationPreview(paidAmount = 0.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun `vcasne storno zaplacene rezervace kredit vrati`() {
        assertEquals(
            CancellationPreview.REFUND_ELIGIBLE,
            cancellationPreview(paidAmount = 500.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun `kdyz vse odeslo za lekce, storno uz nic nevrati`() {
        assertEquals(
            CancellationPreview.NOTHING_LEFT,
            cancellationPreview(paidAmount = 500.0, refundableAmount = 0.0, deadline = deadline, now = beforeDeadline),
        )
    }

    @Test
    fun `po uzaverce kredit nevznikne`() {
        assertEquals(
            CancellationPreview.WINDOW_PASSED,
            cancellationPreview(paidAmount = 500.0, deadline = deadline, now = afterDeadline),
        )
    }

    @Test
    fun `smazana akce se chova jako po uzaverce`() {
        // Bez uzávěrky (akce už v systému není) není z čeho nárok odvodit.
        assertEquals(
            CancellationPreview.WINDOW_PASSED,
            cancellationPreview(paidAmount = 500.0, deadline = null, now = beforeDeadline),
        )
    }
}
