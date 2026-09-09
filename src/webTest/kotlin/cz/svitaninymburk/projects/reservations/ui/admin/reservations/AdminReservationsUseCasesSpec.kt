package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.ReservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.reservationPaymentMethod
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.searchQueryOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminReservationsUseCasesSpec {

    @Test
    fun searchQueryOfTreatsBlankInputAsNoSearch() {
        assertNull(searchQueryOf(""))
        assertNull(searchQueryOf("   "))
        assertEquals("Novák", searchQueryOf("Novák"))
    }

    @Test
    fun freeReservationHasNoPaymentMethod() {
        assertNull(reservationPaymentMethod(totalPrice = 0.0, walletDeductedAmount = 0.0, isOnSite = false))
        // I když je akce zdarma, na peněženku se nikdo nedívá.
        assertNull(reservationPaymentMethod(totalPrice = 0.0, walletDeductedAmount = 50.0, isOnSite = true))
    }

    @Test
    fun walletCoveringWholePriceWinsOverOnSite() {
        assertEquals(
            ReservationPaymentMethod.WALLET,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 200.0, isOnSite = true),
        )
        // Přeplatek z peněženky je pořád plná platba peněženkou.
        assertEquals(
            ReservationPaymentMethod.WALLET,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 250.0, isOnSite = false),
        )
    }

    @Test
    fun partialWalletKeepsTrackOfHowTheRestIsPaid() {
        assertEquals(
            ReservationPaymentMethod.CASH_AND_WALLET,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 50.0, isOnSite = true),
        )
        assertEquals(
            ReservationPaymentMethod.TRANSFER_AND_WALLET,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 50.0, isOnSite = false),
        )
    }

    @Test
    fun withoutWalletItIsCashOrTransfer() {
        assertEquals(
            ReservationPaymentMethod.CASH,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 0.0, isOnSite = true),
        )
        assertEquals(
            ReservationPaymentMethod.TRANSFER,
            reservationPaymentMethod(totalPrice = 200.0, walletDeductedAmount = 0.0, isOnSite = false),
        )
    }
}
