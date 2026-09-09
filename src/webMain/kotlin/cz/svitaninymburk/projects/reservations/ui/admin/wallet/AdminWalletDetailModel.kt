package cz.svitaninymburk.projects.reservations.ui.admin.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.AdminWalletsMutations
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.AdminWalletsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.canAdjustWallet
import cz.svitaninymburk.projects.reservations.ui.util.walletResetDateLabel
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletTransaction
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface AdminWalletDetailUiState {
    data object Loading : AdminWalletDetailUiState
    data class Success(val transactions: List<WalletTransaction>) : AdminWalletDetailUiState
    data class Error(val message: String) : AdminWalletDetailUiState
}

class AdminWalletDetailModel(
    scope: CoroutineScope,
    private val queries: AdminWalletsQueries,
    private val mutations: AdminWalletsMutations,
    wallet: Wallet,
) : ScreenModel(scope) {

    var uiState: AdminWalletDetailUiState by mutableStateOf(AdminWalletDetailUiState.Loading); private set

    /** Úprava kreditu vrací aktualizovanou peněženku, takže hlavička se překreslí bez dalšího dotazu. */
    var wallet: Wallet by mutableStateOf(wallet); private set

    var resetDateLabel: String? by mutableStateOf(null); private set

    var adjustAmount: Number? by mutableStateOf(null)
    var adjustNote by mutableStateOf("")
    var isSubmitting by mutableStateOf(false); private set

    val canAdjust: Boolean get() = canAdjustWallet(adjustAmount?.toDouble(), adjustNote)

    fun load() {
        uiState = AdminWalletDetailUiState.Loading
        scope.launch {
            queries.transactions(wallet.id)
                .onRight { uiState = AdminWalletDetailUiState.Success(it) }
                .onLeft { uiState = AdminWalletDetailUiState.Error(it.localizedMessage(currentStrings)) }
            // Datum propadnutí kreditu je jen doplněk hlavičky — když se nenačte,
            // detail se kvůli tomu nerozbije, jen se nezobrazí.
            queries.settings().onRight {
                resetDateLabel = walletResetDateLabel(it.seasonResetDay, it.seasonResetMonth)
            }
        }
    }

    fun adjust(isCredit: Boolean) {
        val amount = adjustAmount?.toDouble()
        if (!canAdjustWallet(amount, adjustNote) || amount == null) return
        val note = adjustNote
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.adjustBalance(wallet.id, amount, note, isCredit) },
            onSuccess = { updated ->
                wallet = updated
                adjustAmount = null
                adjustNote = ""
                showToast(currentStrings.walletCreditIssued)
                load()
            },
        )
    }
}

fun IComponent.buildAdminWalletDetailModel(scope: CoroutineScope, wallet: Wallet): AdminWalletDetailModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    val settings = getService<AppSettingsServiceInterface>(RpcSerializersModules)
    return AdminWalletDetailModel(
        scope = scope,
        queries = AdminWalletsQueries(admin, settings),
        mutations = AdminWalletsMutations(admin),
        wallet = wallet,
    )
}
