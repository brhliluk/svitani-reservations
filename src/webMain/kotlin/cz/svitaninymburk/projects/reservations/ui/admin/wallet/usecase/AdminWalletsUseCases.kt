package cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase

import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import kotlin.uuid.Uuid

const val WALLETS_PAGE_SIZE = 20

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Ruční úprava kreditu chce kladnou částku a vyplněnou poznámku — poznámka je
 * jediný záznam o tom, proč admin do peněženky zasáhl, takže bez ní se úprava
 * nepustí. Původní obrazovka měla stejnou podmínku na třech místech (dvakrát
 * `disabled(...)` a ještě jednou uvnitř odesílání), tady je jednou.
 */
fun canAdjustWallet(amount: Double?, note: String): Boolean =
    amount != null && amount > 0 && note.isNotBlank()

// --- UseCase třídy (tenké, vrací Either) ---

class AdminWalletsQueries(
    private val admin: AdminServiceInterface,
    private val settings: AppSettingsServiceInterface,
) {
    suspend fun wallets(page: Int, pageSize: Int) = admin.getWallets(page, pageSize)
    suspend fun transactions(walletId: Uuid) = admin.getWalletTransactions(walletId.toString())
    suspend fun settings() = settings.getSettings()
}

class AdminWalletsMutations(private val admin: AdminServiceInterface) {
    suspend fun adjustBalance(walletId: Uuid, amount: Double, note: String, isCredit: Boolean) =
        admin.adjustWalletBalance(walletId.toString(), amount, note, isCredit)
}
