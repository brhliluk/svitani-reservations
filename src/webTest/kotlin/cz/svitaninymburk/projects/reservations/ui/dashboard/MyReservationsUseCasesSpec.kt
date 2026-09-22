package cz.svitaninymburk.projects.reservations.ui.dashboard

import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.MyReservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.cardOpensOnTitleOnly
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.myReservationPaymentMethod
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private fun item(
    totalPrice: Double = 500.0,
    paymentType: PaymentType = PaymentType.BANK_TRANSFER,
    isSeries: Boolean = false,
) = MyReservationListItem(
    id = Uuid.random(),
    eventTitle = "Hravé cvičení",
    startDateTime = LocalDateTime(2026, 9, 10, 9, 30),
    seatCount = 1,
    totalPrice = totalPrice,
    status = Reservation.Status.CONFIRMED,
    paymentType = paymentType,
    variableSymbol = null,
    isSeries = isSeries,
)

class MyReservationsUseCasesSpec {

    @Test
    fun freeReservationSaysNothingAboutPayment() {
        // "Převodem" vedle "Zdarma" by si protiřečilo.
        assertNull(myReservationPaymentMethod(item(totalPrice = 0.0)))
        assertNull(myReservationPaymentMethod(item(totalPrice = 0.0, paymentType = PaymentType.ON_SITE)))
    }

    @Test
    fun paidReservationSaysCashOrTransfer() {
        assertEquals(
            MyReservationPaymentMethod.CASH,
            myReservationPaymentMethod(item(paymentType = PaymentType.ON_SITE)),
        )
        assertEquals(
            MyReservationPaymentMethod.TRANSFER,
            myReservationPaymentMethod(item(paymentType = PaymentType.BANK_TRANSFER)),
        )
    }

    @Test
    fun courseCardOpensOnlyFromItsTitle() {
        // Pod hlavičkou kurzu je rozbalovací seznam lekcí; klikací celá plocha
        // by navigovala i při snaze rozkliknout termín.
        assertTrue(cardOpensOnTitleOnly(item(isSeries = true)))
        assertFalse(cardOpensOnTitleOnly(item(isSeries = false)))
    }
}
