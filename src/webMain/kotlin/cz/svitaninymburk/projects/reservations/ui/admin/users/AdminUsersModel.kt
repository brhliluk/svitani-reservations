package cz.svitaninymburk.projects.reservations.ui.admin.users

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.AdminUsersMutations
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.AdminUsersQueries
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.toggledRole
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.searchQueryOf
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface AdminUsersUiState {
    data object Loading : AdminUsersUiState
    data class Success(val data: List<AdminUserListItem>) : AdminUsersUiState
    data class Error(val message: String) : AdminUsersUiState
}

enum class UserAction { CHANGE_ROLE, DELETE }

data class PendingUserAction(
    val type: UserAction,
    val userId: Uuid,
    val userName: String,
    val currentRole: User.Role,
) {
    /** Role, do které potvrzení akce uživatele překlopí. */
    val newRole: User.Role get() = toggledRole(currentRole)
}

class AdminUsersModel(
    scope: CoroutineScope,
    private val queries: AdminUsersQueries,
    private val mutations: AdminUsersMutations,
    val currentUserId: Uuid,
) : ScreenModel(scope) {

    var uiState: AdminUsersUiState by mutableStateOf(AdminUsersUiState.Loading); private set

    /** Co je napsané ve vyhledávacím poli — do filtru se to překlopí až potvrzením. */
    var searchInput by mutableStateOf("")
    var activeSearchQuery: String? by mutableStateOf(null); private set

    var pendingAction: PendingUserAction? by mutableStateOf(null); private set
    var isModalLoading by mutableStateOf(false); private set
    var resettingPasswordForId: Uuid? by mutableStateOf(null); private set

    fun load() {
        uiState = AdminUsersUiState.Loading
        scope.launch {
            queries.users()
                .onRight { uiState = AdminUsersUiState.Success(it) }
                .onLeft { uiState = AdminUsersUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    // Hledá se lokálně nad už načteným seznamem, takže potvrzení dotazu nic nenačítá.
    fun submitSearch() { activeSearchQuery = searchQueryOf(searchInput) }

    fun clearSearch() {
        searchInput = ""
        activeSearchQuery = null
    }

    /** Uživatel nesmí smazat sám sebe — tlačítko se mu u vlastního řádku nenabízí. */
    fun canDelete(user: AdminUserListItem): Boolean = user.id != currentUserId

    fun requestRoleChange(user: AdminUserListItem) {
        pendingAction = PendingUserAction(
            type = UserAction.CHANGE_ROLE,
            userId = user.id,
            userName = "${user.name} ${user.surname}",
            currentRole = user.role,
        )
    }

    fun requestDelete(user: AdminUserListItem) {
        pendingAction = PendingUserAction(
            type = UserAction.DELETE,
            userId = user.id,
            userName = "${user.name} ${user.surname}",
            currentRole = user.role,
        )
    }

    fun dismissPendingAction() { pendingAction = null }

    fun confirmPendingAction(action: PendingUserAction) {
        when (action.type) {
            UserAction.CHANGE_ROLE -> run(
                loading = { isModalLoading = it },
                errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
                block = { mutations.updateRole(action.userId, action.newRole) },
                onSuccess = { showToast(currentStrings.toastRoleChanged(action.userName)); load() },
            ).invokeOnCompletion { pendingAction = null }
            UserAction.DELETE -> run(
                loading = { isModalLoading = it },
                errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
                block = { mutations.delete(action.userId) },
                onSuccess = { showToast(currentStrings.toastUserDeleted(action.userName)); load() },
            ).invokeOnCompletion { pendingAction = null }
        }
    }

    /** Spinner drží u konkrétního řádku, ne u celé tabulky. */
    fun resetPassword(user: AdminUserListItem) {
        resettingPasswordForId = user.id
        run(
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.requestPasswordReset(user.email) },
            onSuccess = { showToast(currentStrings.forgotPasswordEmailSent) },
        ).invokeOnCompletion { resettingPasswordForId = null }
    }
}

fun IComponent.buildAdminUsersModel(scope: CoroutineScope, currentUserId: Uuid): AdminUsersModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    val auth = getService<AuthServiceInterface>(RpcSerializersModules)
    return AdminUsersModel(
        scope = scope,
        queries = AdminUsersQueries(admin),
        mutations = AdminUsersMutations(admin, auth),
        currentUserId = currentUserId,
    )
}
