package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminDashboardData
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.usecase.AdminDashboardMutations
import cz.svitaninymburk.projects.reservations.ui.admin.usecase.AdminDashboardQueries
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface AdminDashboardUiState {
    data object Loading : AdminDashboardUiState
    data class Success(val data: AdminDashboardData) : AdminDashboardUiState
    data class Error(val message: String) : AdminDashboardUiState
}

class AdminDashboardModel(
    scope: CoroutineScope,
    private val queries: AdminDashboardQueries,
    private val mutations: AdminDashboardMutations,
) : ScreenModel(scope) {

    var uiState: AdminDashboardUiState by mutableStateOf(AdminDashboardUiState.Loading); private set

    /**
     * Na rozdíl od ostatních přehledů se tady při obnovení nepřepíná na Loading —
     * po potvrzení platby má dashboard zůstat vykreslený a jen se přepsat čísly.
     */
    fun load() {
        scope.launch {
            queries.summary()
                .onRight { uiState = AdminDashboardUiState.Success(it) }
                .onLeft { uiState = AdminDashboardUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun markAsPaid(reservationId: Uuid, contactName: String) = run(
        errorMessage = { it.localizedMessage(currentStrings) },
        block = { mutations.markAsPaid(reservationId) },
        onSuccess = {
            showToast(currentStrings.toastPaymentConfirmed(contactName))
            load()
        },
    )
}

fun IComponent.buildAdminDashboardModel(scope: CoroutineScope): AdminDashboardModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminDashboardModel(
        scope = scope,
        queries = AdminDashboardQueries(admin),
        mutations = AdminDashboardMutations(admin),
    )
}
