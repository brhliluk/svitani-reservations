package cz.svitaninymburk.projects.reservations.ui.admin.reservations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.admin.ReservationsPage
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.AdminReservationsMutations
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.AdminReservationsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.RESERVATIONS_PAGE_SIZE
import cz.svitaninymburk.projects.reservations.ui.admin.reservations.usecase.searchQueryOf
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface AdminReservationsUiState {
    data object Loading : AdminReservationsUiState
    data class Success(val data: ReservationsPage) : AdminReservationsUiState
    data class Error(val message: String) : AdminReservationsUiState
}

class AdminReservationsModel(
    scope: CoroutineScope,
    private val queries: AdminReservationsQueries,
    private val mutations: AdminReservationsMutations,
) : ScreenModel(scope) {

    var uiState: AdminReservationsUiState by mutableStateOf(AdminReservationsUiState.Loading); private set

    /** Co je napsané ve vyhledávacím poli — do dotazu se to překlopí až potvrzením. */
    var searchInput by mutableStateOf("")
    var activeSearchQuery: String? by mutableStateOf(null); private set
    var page by mutableIntStateOf(0); private set
    var includeCancelled by mutableStateOf(false); private set

    var expandedId: Uuid? by mutableStateOf(null); private set
    var pendingAction: PendingAction? by mutableStateOf(null); private set
    var isModalLoading by mutableStateOf(false); private set

    fun load() {
        uiState = AdminReservationsUiState.Loading
        scope.launch {
            queries.reservations(activeSearchQuery, page, RESERVATIONS_PAGE_SIZE, includeCancelled)
                .onRight { uiState = AdminReservationsUiState.Success(it) }
                .onLeft { uiState = AdminReservationsUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun submitSearch() {
        activeSearchQuery = searchQueryOf(searchInput)
        page = 0
        load()
    }

    fun clearSearch() {
        searchInput = ""
        activeSearchQuery = null
        page = 0
        load()
    }

    fun setIncludeCancelled(value: Boolean) {
        includeCancelled = value
        page = 0
        load()
    }

    /** Rozbalený detail se při stránkování zavírá — jinak by zůstal u řádku, který už není vidět. */
    fun goToPage(target: Int) {
        if (target == page || target < 0) return
        expandedId = null
        page = target
        load()
    }

    fun toggleExpanded(id: Uuid) {
        expandedId = if (expandedId == id) null else id
    }

    fun confirmPayment(item: AdminReservationListItem) {
        pendingAction = PendingAction(AdminActionType.CONFIRM_PAYMENT, item.id, item.contactName)
    }

    fun cancelReservation(item: AdminReservationListItem) {
        pendingAction = PendingAction(AdminActionType.CANCEL_RESERVATION, item.id, item.contactName)
    }

    fun dismissPendingAction() { pendingAction = null }

    fun confirmPendingAction(action: PendingAction) {
        when (action.type) {
            // Potvrzená platba může přeskládat řazení přehledu, proto se vrací na první stránku.
            AdminActionType.CONFIRM_PAYMENT -> run(
                loading = { isModalLoading = it },
                errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
                block = { mutations.markAsPaid(action.reservationId) },
                onSuccess = {
                    showToast(currentStrings.toastPaymentConfirmed(action.participantName))
                    page = 0
                    expandedId = null
                    load()
                },
            ).invokeOnCompletion { pendingAction = null }
            AdminActionType.CANCEL_RESERVATION -> run(
                loading = { isModalLoading = it },
                errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
                block = { mutations.cancel(action.reservationId) },
                onSuccess = {
                    showToast(currentStrings.toastReservationCancelled(action.participantName))
                    expandedId = null
                    load()
                },
            ).invokeOnCompletion { pendingAction = null }
        }
    }
}

fun IComponent.buildAdminReservationsModel(scope: CoroutineScope): AdminReservationsModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    val reservation = getService<ReservationServiceInterface>(RpcSerializersModules)
    return AdminReservationsModel(
        scope = scope,
        queries = AdminReservationsQueries(admin),
        mutations = AdminReservationsMutations(admin, reservation),
    )
}
