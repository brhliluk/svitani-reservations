package cz.svitaninymburk.projects.reservations.ui.admin.wallet

import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.canAdjustWallet
import cz.svitaninymburk.projects.reservations.ui.util.walletResetDateLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminWalletsUseCasesSpec {

    @Test
    fun adjustmentNeedsBothAmountAndNote() {
        assertTrue(canAdjustWallet(amount = 100.0, note = "vratka za lekci"))
    }

    @Test
    fun emptyFormCannotBeSubmitted() {
        assertFalse(canAdjustWallet(amount = null, note = ""))
        assertFalse(canAdjustWallet(amount = null, note = "vratka"))
        assertFalse(canAdjustWallet(amount = 100.0, note = ""))
    }

    @Test
    fun noteMustCarryTextNotJustSpaces() {
        // Poznámka je jediný záznam o důvodu zásahu, mezery ho nenesou.
        assertFalse(canAdjustWallet(amount = 100.0, note = "   "))
        assertFalse(canAdjustWallet(amount = 100.0, note = "\t\n"))
    }

    @Test
    fun amountMustBePositive() {
        // Odebrání kreditu se posílá kladnou částkou a příznakem isCredit = false,
        // takže nula ani minus tady nemají smysl.
        assertFalse(canAdjustWallet(amount = 0.0, note = "vratka"))
        assertFalse(canAdjustWallet(amount = -50.0, note = "vratka"))
        assertTrue(canAdjustWallet(amount = 0.5, note = "vratka"))
    }

    @Test
    fun resetDateLabelIsDayThenMonth() {
        assertEquals("15. 8.", walletResetDateLabel(day = 15, month = 8))
        assertEquals("30. 6.", walletResetDateLabel(day = 30, month = 6))
        // Bez vycpávání nulou — takhle se datum v češtině píše.
        assertEquals("1. 1.", walletResetDateLabel(day = 1, month = 1))
    }
}
