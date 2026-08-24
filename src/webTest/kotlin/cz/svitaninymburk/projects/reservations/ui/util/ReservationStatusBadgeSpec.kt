package cz.svitaninymburk.projects.reservations.ui.util

import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Jedno rozhodnutí o stavu rezervace pro všechny přehledy. Náhradník se dřív
 * v admin tabulkách propadal do "Čeká" a byl k nerozeznání od nezaplacené
 * rezervace; akce zdarma se tvářila jako zaplacená převodem.
 */
class ReservationStatusBadgeSpec {

    private fun badge(
        status: Reservation.Status,
        paymentType: PaymentInfo.Type = PaymentInfo.Type.BANK_TRANSFER,
        totalPrice: Double = 150.0,
    ) = reservationStatusBadge(status, paymentType, totalPrice)

    @Test
    fun waitlistedIsItsOwnBadgeNotWaitingForPayment() {
        assertEquals(
            ReservationStatusBadge.WAITLISTED,
            badge(Reservation.Status.WAITLISTED),
        )
    }

    @Test
    fun waitlistedForAFreeEventStaysWaitlisted() {
        assertEquals(
            ReservationStatusBadge.WAITLISTED,
            badge(Reservation.Status.WAITLISTED, PaymentInfo.Type.FREE, totalPrice = 0.0),
        )
    }

    @Test
    fun cancelledWinsOverEverything() {
        assertEquals(
            ReservationStatusBadge.CANCELLED,
            badge(Reservation.Status.CANCELLED, PaymentInfo.Type.FREE, totalPrice = 0.0),
        )
    }

    @Test
    fun confirmedFreeReservationIsFreeNotPaid() {
        assertEquals(
            ReservationStatusBadge.FREE,
            badge(Reservation.Status.CONFIRMED, PaymentInfo.Type.FREE, totalPrice = 0.0),
        )
    }

    @Test
    fun pendingFreeReservationIsFreeNotWaiting() {
        assertEquals(
            ReservationStatusBadge.FREE,
            badge(Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER, totalPrice = 0.0),
        )
    }

    @Test
    fun confirmedPaidReservationIsPaid() {
        assertEquals(
            ReservationStatusBadge.PAID,
            badge(Reservation.Status.CONFIRMED),
        )
    }

    @Test
    fun unpaidOnSiteReservationIsOnSite() {
        assertEquals(
            ReservationStatusBadge.ON_SITE,
            badge(Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.ON_SITE),
        )
    }

    @Test
    fun unpaidTransferReservationIsWaiting() {
        assertEquals(
            ReservationStatusBadge.WAITING,
            badge(Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.BANK_TRANSFER),
        )
    }

    @Test
    fun rejectedIsNotShownAsWaitingForPayment() {
        assertEquals(
            ReservationStatusBadge.CANCELLED,
            badge(Reservation.Status.REJECTED),
        )
    }

    @Test
    fun markingAsPaidIsOfferedOnlyWhereItWouldWork() {
        // markReservationAsPaid vyžaduje PENDING_PAYMENT (service/Admin.kt), takže
        // u náhradníka, zrušené ani u akce zdarma nemá tlačítko co dělat.
        assertEquals(true, canBeMarkedAsPaid(badge(Reservation.Status.PENDING_PAYMENT)))
        assertEquals(true, canBeMarkedAsPaid(badge(Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.ON_SITE)))
        assertEquals(false, canBeMarkedAsPaid(badge(Reservation.Status.WAITLISTED)))
        assertEquals(false, canBeMarkedAsPaid(badge(Reservation.Status.CANCELLED)))
        assertEquals(false, canBeMarkedAsPaid(badge(Reservation.Status.CONFIRMED)))
        assertEquals(
            false,
            canBeMarkedAsPaid(badge(Reservation.Status.PENDING_PAYMENT, PaymentInfo.Type.FREE, totalPrice = 0.0)),
        )
    }
}
