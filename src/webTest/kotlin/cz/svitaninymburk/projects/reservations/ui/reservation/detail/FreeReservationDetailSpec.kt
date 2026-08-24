package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.cs.CsStrings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Detail rezervace zdarma nesmí nabízet platbu — a to i u historických rezervací,
 * které v DB zůstaly ve stavu PENDING_PAYMENT.
 */
class FreeReservationDetailSpec {

    private val target = ReservationTarget.Instance(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Beseda",
            description = "",
            startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
            price = 0.0,
            capacity = 10,
        )
    )

    private fun reservation(
        totalPrice: Double,
        status: Reservation.Status,
        paymentType: PaymentInfo.Type,
    ) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(target.id),
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        seatCount = 1,
        totalPrice = totalPrice,
        status = status,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = paymentType,
    )

    @Test
    fun freeReservationStuckInPendingPaymentShowsNoPaymentInstructions() {
        val uiState = getReservationUiState(
            reservation(0.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER),
            target,
            CsStrings,
        )

        assertFalse(uiState.showPaymentInfo, "Rezervace zdarma nesmí nabízet QR platbu")
        assertFalse(uiState.showOnSiteInfo, "Rezervace zdarma nesmí nabízet platbu na místě")
        assertEquals(CsStrings.free, uiState.statusLabel)
        assertTrue(uiState.canBeCancelled, "Budoucí akci musí jít odhlásit i když je zdarma")
    }

    @Test
    fun confirmedFreeReservationShowsNoPaymentInstructions() {
        val uiState = getReservationUiState(
            reservation(0.0, Reservation.Status.CONFIRMED, PaymentInfo.Type.FREE),
            target,
            CsStrings,
        )

        assertFalse(uiState.showPaymentInfo)
        assertFalse(uiState.showOnSiteInfo)
        assertEquals(CsStrings.free, uiState.statusLabel)
    }

    @Test
    fun unpaidReservationWithAPriceStillShowsPaymentInstructions() {
        val uiState = getReservationUiState(
            reservation(150.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER),
            target,
            CsStrings,
        )

        assertTrue(uiState.showPaymentInfo)
        assertEquals(CsStrings.unpaid, uiState.statusLabel)
    }

    @Test
    fun cancelledFreeReservationStaysCancelled() {
        val uiState = getReservationUiState(
            reservation(0.0, Reservation.Status.CANCELLED, PaymentInfo.Type.FREE),
            target,
            CsStrings,
        )

        assertFalse(uiState.showPaymentInfo)
        assertEquals(CsStrings.cancelled, uiState.statusLabel)
        assertFalse(uiState.canBeCancelled)
    }

    @Test
    fun waitlistedFreeReservationKeepsWaitlistLabel() {
        val uiState = getReservationUiState(
            reservation(0.0, Reservation.Status.WAITLISTED, PaymentInfo.Type.FREE),
            target,
            CsStrings,
        )

        assertFalse(uiState.showPaymentInfo)
        assertEquals(CsStrings.waitlistedStatus, uiState.statusLabel)
    }

    @Test
    fun zeroPriceIsLabelledAsFreeInsteadOfZeroCrowns() {
        assertEquals(CsStrings.free, totalPriceLabel(0.0, CsStrings))
    }

    @Test
    fun nonZeroPriceKeepsTheAmountWithCurrency() {
        // Formát čísla se mezi JS a JVM liší ("150" vs "150.0"), takže se tu
        // ověřuje jen to, co je stejné: částka a za ní měna.
        val label = totalPriceLabel(150.0, CsStrings)
        assertTrue(label.startsWith("150"), "Čekal jsem částku, dostal '$label'")
        assertTrue(label.endsWith(CsStrings.currency), "Čekal jsem měnu, dostal '$label'")
    }
}
