package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import cz.svitaninymburk.projects.reservations.admin.CancellationRefundRow
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.CancellationRefundsDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CancellationRefundsDraftSpec {

    private fun row(name: String, amount: Double, fromSeries: Boolean = false) =
        CancellationRefundRow(Uuid.random(), name, seatCount = 1, fromSeries = fromSeries, refundAmount = amount)

    private val aneta = row("Aneta", 150.0, fromSeries = true)
    private val patrik = row("Patrik", 170.0)
    private val anna = row("Anna", 0.0)
    private val draft = CancellationRefundsDraft(Uuid.random(), listOf(aneta, patrik, anna))

    @Test
    fun `everyone with something to refund is ticked by default`() {
        assertTrue(draft.isRefunded(aneta))
        assertTrue(draft.isRefunded(patrik))
        assertEquals(320.0, draft.totalRefund)
    }

    @Test
    fun `nothing to refund is never ticked`() {
        assertFalse(draft.isRefunded(anna))
    }

    @Test
    fun `unticking excludes the reservation and lowers the total`() {
        val updated = draft.toggle(patrik.reservationId)

        assertEquals(setOf(patrik.reservationId), updated.noRefundFor)
        assertFalse(updated.isRefunded(patrik))
        assertEquals(150.0, updated.totalRefund)
    }

    @Test
    fun `ticking again puts the reservation back`() {
        val updated = draft.toggle(patrik.reservationId).toggle(patrik.reservationId)

        assertTrue(updated.noRefundFor.isEmpty())
        assertEquals(320.0, updated.totalRefund)
    }
}
