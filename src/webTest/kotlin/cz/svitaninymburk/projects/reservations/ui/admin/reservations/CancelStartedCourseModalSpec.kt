package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CancelStartedCourseModalSpec {

    @Test
    fun emptyFieldBlocksCancellation() = assertNull(refundAmountOrNull(null))

    @Test
    fun negativeAmountBlocksCancellation() = assertNull(refundAmountOrNull(-1))

    @Test
    fun notANumberBlocksCancellation() = assertNull(refundAmountOrNull(Double.NaN))

    @Test
    fun zeroIsValidRefund() = assertEquals(0.0, refundAmountOrNull(0))

    @Test
    fun amountAbovePaidPriceIsAllowed() = assertEquals(1500.5, refundAmountOrNull(1500.5))
}
