package cz.svitaninymburk.projects.reservations.ui.admin.payments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.PaymentEventsPage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.payments.usecase.AdminPaymentsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.payments.usecase.PAYMENTS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface AdminPaymentsUiState {
    data object Loading : AdminPaymentsUiState
    data class Success(val data: PaymentEventsPage) : AdminPaymentsUiState
    data class Error(val message: String) : AdminPaymentsUiState
}

class AdminPaymentsModel(
    scope: CoroutineScope,
    private val queries: AdminPaymentsQueries,
) : ScreenModel(scope) {

    var uiState: AdminPaymentsUiState by mutableStateOf(AdminPaymentsUiState.Loading); private set
    var page by mutableIntStateOf(0); private set

    fun load() {
        uiState = AdminPaymentsUiState.Loading
        scope.launch {
            queries.payments(page, PAYMENTS_PAGE_SIZE)
                .onRight { uiState = AdminPaymentsUiState.Success(it) }
                .onLeft { uiState = AdminPaymentsUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun goToPage(target: Int) {
        if (target == page || target < 0) return
        page = target
        load()
    }
}

fun IComponent.buildAdminPaymentsModel(scope: CoroutineScope): AdminPaymentsModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminPaymentsModel(scope = scope, queries = AdminPaymentsQueries(admin))
}
