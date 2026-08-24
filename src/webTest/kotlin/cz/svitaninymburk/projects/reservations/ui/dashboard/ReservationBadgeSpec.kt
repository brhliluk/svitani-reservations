package cz.svitaninymburk.projects.reservations.ui.dashboard

import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Na dashboardu nesmí u akce zdarma svítit žluté „Čeká na platbu".
 */
class ReservationBadgeSpec {

    private fun item(
        totalPrice: Double,
        status: Reservation.Status,
        paymentType: PaymentInfo.Type,
    ) = MyReservationListItem(
        id = Uuid.random(),
        eventTitle = "Beseda",
        startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
        seatCount = 1,
        totalPrice = totalPrice,
        status = status,
        paymentType = paymentType,
        variableSymbol = null,
        isSeries = false,
    )

    @Test
    fun freeReservationStuckInPendingPaymentIsBadgedAsFree() {
        val badge = reservationBadge(
            item(0.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER)
        )
        assertEquals(ReservationBadge.FREE, badge)
    }

    @Test
    fun confirmedFreeReservationIsBadgedAsFreeNotPaid() {
        val badge = reservationBadge(
            item(0.0, Reservation.Status.CONFIRMED, PaymentInfo.Type.FREE)
        )
        assertEquals(ReservationBadge.FREE, badge)
    }

    @Test
    fun freeBeatsOnSiteWhenNothingIsToBePaid() {
        val badge = reservationBadge(
            item(0.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.ON_SITE)
        )
        assertEquals(ReservationBadge.FREE, badge)
    }

    @Test
    fun waitlistedFreeReservationIsNotBadgedAsFree() {
        val badge = reservationBadge(
            item(0.0, Reservation.Status.WAITLISTED, PaymentInfo.Type.FREE)
        )
        assertEquals(ReservationBadge.WAITING, badge)
    }

    @Test
    fun paidReservationKeepsThePaidBadge() {
        val badge = reservationBadge(
            item(150.0, Reservation.Status.CONFIRMED, PaymentInfo.Type.BANK_TRANSFER)
        )
        assertEquals(ReservationBadge.PAID, badge)
    }

    @Test
    fun onSiteReservationWithAPriceKeepsTheOnSiteBadge() {
        val badge = reservationBadge(
            item(150.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.ON_SITE)
        )
        assertEquals(ReservationBadge.ON_SITE, badge)
    }

    @Test
    fun unpaidTransferReservationKeepsTheWaitingBadge() {
        val badge = reservationBadge(
            item(150.0, Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER)
        )
        assertEquals(ReservationBadge.WAITING, badge)
    }
}
