package cz.svitaninymburk.projects.reservations.ui.wallet.usecase

import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Kód i e-mail musí být vyplněné — zůstatek se ukazuje jen tomu, kdo doloží
 * obojí, aby cizí kód sám nestačil.
 */
fun canLookUpWallet(code: String, email: String): Boolean =
    code.isNotBlank() && email.isNotBlank()

/**
 * Kód se z e-mailu nebo z papírku často kopíruje s mezerami okolo; na server
 * jde bez nich, jinak by se neshodl a uživatel by viděl "peněženka nenalezena".
 */
fun normalizeWalletLookupInput(value: String): String = value.trim()

// --- UseCase třídy (tenké, vrací Either) ---

class WalletLookupQueries(private val reservations: ReservationServiceInterface) {
    suspend fun info(code: String, email: String) =
        reservations.getWalletInfo(normalizeWalletLookupInput(code), normalizeWalletLookupInput(email))
}
