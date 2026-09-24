package cz.svitaninymburk.projects.reservations.service

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Storno celé rezervace a omluvenka z lekce sdílí jednu uzávěrku i jednu hranici:
 * přesně v 18:00 den předem se kredit ještě vrací, o chvilku později už ne.
 */
class RefundDeadlineBoundarySpec {
    private val start = LocalDateTime(2026, 10, 15, 9, 0)
    private val deadline = refundDeadlineFor(start)

    @Test
    fun `presne v uzaverce se jeste vraci`() {
        assertFalse(isPastRefundDeadline(start, now = deadline))
        assertFalse(isPastRefundDeadline(start, now = deadline - 1.milliseconds))
    }

    @Test
    fun `po uzaverce uz ne`() {
        assertTrue(isPastRefundDeadline(start, now = deadline + 1.milliseconds))
    }
}
