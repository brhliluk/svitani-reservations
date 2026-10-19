package cz.svitaninymburk.projects.reservations.ui.wallet

import cz.svitaninymburk.projects.reservations.ui.wallet.usecase.canLookUpWallet
import cz.svitaninymburk.projects.reservations.ui.wallet.usecase.normalizeWalletLookupInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletLookupUseCasesSpec {

    @Test
    fun lookupNeedsBothCodeAndEmail() {
        // Cizí kód sám nestačí — zůstatek se ukáže jen tomu, kdo doloží obojí.
        assertTrue(canLookUpWallet("SVIT-1234-5678", "a@b.cz"))
        assertFalse(canLookUpWallet("", "a@b.cz"))
        assertFalse(canLookUpWallet("SVIT-1234-5678", ""))
        assertFalse(canLookUpWallet("", ""))
    }

    @Test
    fun blanksDoNotCountAsFilledIn() {
        assertFalse(canLookUpWallet("   ", "a@b.cz"))
        assertFalse(canLookUpWallet("SVIT-1234-5678", "\t"))
    }

    @Test
    fun surroundingSpacesAreStrippedBeforeAsking() {
        // Kód se z e-mailu kopíruje s mezerami; bez trimu by se neshodl a
        // uživatel by viděl "peněženka nenalezena".
        assertEquals("SVIT-1234-5678", normalizeWalletLookupInput("  SVIT-1234-5678  "))
        assertEquals("a@b.cz", normalizeWalletLookupInput("\ta@b.cz\n"))
    }

    @Test
    fun innerCharactersAreLeftAlone() {
        // Trim je jen na okraje — kód se nijak nepřepisuje.
        assertEquals("SVIT-1234-5678", normalizeWalletLookupInput("SVIT-1234-5678"))
        assertEquals("svit 1234", normalizeWalletLookupInput("  svit 1234  "))
    }
}
