package cz.svitaninymburk.projects.reservations.ui.admin.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.AdminWalletsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.wallet.usecase.WALLETS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.wallet.Wallet
import cz.svitaninymburk.projects.reservations.wallet.WalletsPage
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface AdminWalletsUiState {
    data object Loading : AdminWalletsUiState
    data class Success(val data: WalletsPage) : AdminWalletsUiState
    data class Error(val message: String) : AdminWalletsUiState
}

class AdminWalletsModel(
    scope: CoroutineScope,
    private val queries: AdminWalletsQueries,
) : ScreenModel(scope) {

    var uiState: AdminWalletsUiState by mutableStateOf(AdminWalletsUiState.Loading); private set
    var page by mutableIntStateOf(0); private set

    /** Není-li null, místo seznamu se ukazuje detail té peněženky. */
    var selectedWallet: Wallet? by mutableStateOf(null); private set

    fun load() {
        uiState = AdminWalletsUiState.Loading
        scope.launch {
            queries.wallets(page, WALLETS_PAGE_SIZE)
                .onRight { uiState = AdminWalletsUiState.Success(it) }
                .onLeft { uiState = AdminWalletsUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun goToPage(target: Int) {
        if (target == page || target < 0) return
        page = target
        load()
    }

    fun openWallet(wallet: Wallet) { selectedWallet = wallet }
    fun closeWallet() { selectedWallet = null }
}

fun IComponent.buildAdminWalletsModel(scope: CoroutineScope): AdminWalletsModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    val settings = getService<AppSettingsServiceInterface>(RpcSerializersModules)
    return AdminWalletsModel(scope = scope, queries = AdminWalletsQueries(admin, settings))
}
