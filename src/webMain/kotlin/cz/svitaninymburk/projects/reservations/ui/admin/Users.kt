package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.form.form
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private sealed interface AdminUsersUiState {
    data object Loading : AdminUsersUiState
    data class Success(val data: List<AdminUserListItem>) : AdminUsersUiState
    data class Error(val message: String) : AdminUsersUiState
}

private enum class UserAction { CHANGE_ROLE, DELETE }

private data class PendingUserAction(
    val type: UserAction,
    val userId: Uuid,
    val userName: String,
    val currentRole: User.Role,
)

@Composable
fun IComponent.AdminUsersScreen(currentUserId: Uuid) {
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var pendingAction by remember { mutableStateOf<PendingUserAction?>(null) }

    var searchInput by remember { mutableStateOf("") }
    var activeSearchQuery by remember { mutableStateOf<String?>(null) }

    val uiState by produceState<AdminUsersUiState>(
        initialValue = AdminUsersUiState.Loading,
        key1 = refreshTrigger
    ) {
        value = AdminUsersUiState.Loading
        adminService.getAllUsers()
            .onRight { value = AdminUsersUiState.Success(it) }
            .onLeft { value = AdminUsersUiState.Error(it.localizedMessage) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HEADER + SEARCH ---
        div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.allUsers }
                p(className = "text-base-content/60 mt-1") { +currentStrings.usersSubtitle }
            }

            div(className = "join w-full md:w-auto") {
                div(className = "relative w-full md:w-80") {
                    span(className = "absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/50") {
                        span(className = "icon-[heroicons--magnifying-glass] size-5")
                    }
                    text(value = searchInput, className = "input input-bordered join-item w-full pl-10") {
                        placeholder(currentStrings.usersSearchPlaceholder)
                        onInput { searchInput = value ?: "" }
                        onKeyup { event ->
                            if (event.key == "Enter") activeSearchQuery = searchInput.takeIf { it.isNotBlank() }
                        }
                    }
                }
                button(className = "btn btn-primary join-item") {
                    onClick { activeSearchQuery = searchInput.takeIf { it.isNotBlank() } }
                    +currentStrings.search
                }
                if (!activeSearchQuery.isNullOrBlank()) {
                    button(className = "btn btn-ghost join-item tooltip") {
                        attribute("data-tip", currentStrings.clearSearch)
                        onClick {
                            searchInput = ""
                            activeSearchQuery = null
                        }
                        span(className = "icon-[heroicons--x-mark] size-5")
                    }
                }
            }
        }

        // --- 2. TABLE ---
        when (val state = uiState) {
            is AdminUsersUiState.Loading -> Loading()
            is AdminUsersUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminUsersUiState.Success -> {
                val data = if (!activeSearchQuery.isNullOrBlank()) {
                    val q = activeSearchQuery!!.lowercase()
                    state.data.filter {
                        it.name.lowercase().contains(q) ||
                        it.surname.lowercase().contains(q) ||
                        it.email.lowercase().contains(q)
                    }
                } else state.data

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body p-0") {
                        div(className = "overflow-x-auto") {
                            table(className = "table table-zebra w-full") {
                                thead {
                                    tr {
                                        th(className = "w-12") { }
                                        th { +currentStrings.tableHeaderParticipant }
                                        th { +currentStrings.tableHeaderAuthType }
                                        th { +currentStrings.status }
                                        th { +currentStrings.tableHeaderReservations }
                                        th(className = "text-right") { +currentStrings.tableHeaderActions }
                                    }
                                }
                                tbody {
                                    if (data.isEmpty()) {
                                        tr {
                                            td {
                                                attribute("colspan", "6")
                                                div(className = "text-center text-base-content/50 py-8") {
                                                    if (activeSearchQuery != null) +currentStrings.noUsersForSearch(activeSearchQuery!!)
                                                    else +currentStrings.noUsers
                                                }
                                            }
                                        }
                                    } else {
                                        data.forEach { user ->
                                            tr {
                                                // Avatar
                                                td {
                                                    div(className = "avatar placeholder") {
                                                        div(className = "bg-neutral text-neutral-content rounded-full w-10 flex items-center justify-center") {
                                                            span(className = "text-sm font-bold") {
                                                                +"${user.name.firstOrNull() ?: ""}${user.surname.firstOrNull() ?: ""}"
                                                            }
                                                        }
                                                    }
                                                }
                                                // Name + email
                                                td {
                                                    div(className = "font-bold") { +"${user.name} ${user.surname}" }
                                                    div(className = "text-xs text-base-content/50") { +user.email }
                                                }
                                                // Auth type
                                                td {
                                                    if (user.authType == AdminUserListItem.AuthType.GOOGLE) {
                                                        div(className = "badge badge-warning gap-1") {
                                                            span(className = "icon-[heroicons--globe-alt] size-3")
                                                            +currentStrings.authTypeGoogle
                                                        }
                                                    } else {
                                                        div(className = "badge badge-ghost gap-1") {
                                                            span(className = "icon-[heroicons--envelope] size-3")
                                                            +currentStrings.authTypeEmail
                                                        }
                                                    }
                                                }
                                                // Role
                                                td {
                                                    if (user.role == User.Role.ADMIN) {
                                                        div(className = "badge badge-primary gap-1") {
                                                            span(className = "icon-[heroicons--shield-check] size-3")
                                                            +currentStrings.roleAdmin
                                                        }
                                                    } else {
                                                        div(className = "badge badge-ghost gap-1") {
                                                            span(className = "icon-[heroicons--user] size-3")
                                                            +currentStrings.roleUser
                                                        }
                                                    }
                                                }
                                                // Reservation count
                                                td {
                                                    div(className = "flex items-center gap-1") {
                                                        span(className = "icon-[heroicons--ticket] size-4 text-base-content/40")
                                                        +"${user.reservationCount}"
                                                    }
                                                }
                                                // Actions
                                                td(className = "text-right") {
                                                    div(className = "flex justify-end gap-1") {
                                                        button(className = "btn btn-ghost btn-xs tooltip tooltip-left") {
                                                            attribute("data-tip", currentStrings.tooltipChangeRole)
                                                            onClick {
                                                                pendingAction = PendingUserAction(
                                                                    type = UserAction.CHANGE_ROLE,
                                                                    userId = user.id,
                                                                    userName = "${user.name} ${user.surname}",
                                                                    currentRole = user.role,
                                                                )
                                                            }
                                                            span(className = "icon-[heroicons--arrows-right-left] size-5")
                                                        }
                                                        if (user.id != currentUserId) {
                                                            button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                                attribute("data-tip", currentStrings.tooltipDeleteUser)
                                                                onClick {
                                                                    pendingAction = PendingUserAction(
                                                                        type = UserAction.DELETE,
                                                                        userId = user.id,
                                                                        userName = "${user.name} ${user.surname}",
                                                                        currentRole = user.role,
                                                                    )
                                                                }
                                                                span(className = "icon-[heroicons--trash] size-5")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 3. MODAL ---
    if (pendingAction != null) {
        val action = pendingAction!!
        val newRole = if (action.currentRole == User.Role.ADMIN) User.Role.USER else User.Role.ADMIN

        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") {
                    if (action.type == UserAction.CHANGE_ROLE) +currentStrings.modalChangeRoleTitle
                    else +currentStrings.modalDeleteUserTitle
                }
                p(className = "py-4") {
                    if (action.type == UserAction.CHANGE_ROLE) {
                        +currentStrings.modalChangeRoleMsgPre
                        strong { +action.userName }
                        +currentStrings.modalChangeRoleMsgMid
                        strong { +(if (newRole == User.Role.ADMIN) currentStrings.roleAdmin else currentStrings.roleUser) }
                        +currentStrings.modalChangeRoleMsgPost
                    } else {
                        +currentStrings.modalDeleteUserMsgPre
                        strong { +action.userName }
                        +currentStrings.modalDeleteUserMsgPost
                    }
                }
                div(className = "modal-action") {
                    button(className = "btn") {
                        onClick { pendingAction = null }
                        +currentStrings.modalBack
                    }
                    button(className = "btn ${if (action.type == UserAction.CHANGE_ROLE) "btn-primary" else "btn-error"}") {
                        onClick {
                            scope.launch {
                                if (action.type == UserAction.CHANGE_ROLE) {
                                    adminService.updateUserRole(action.userId, newRole)
                                        .onRight {
                                            toastData = ToastData(currentStrings.toastRoleChanged(action.userName), ToastType.Success)
                                            refreshTrigger++
                                        }
                                        .onLeft { error ->
                                            toastData = ToastData(currentStrings.errorToast(error.localizedMessage), ToastType.Error)
                                        }
                                } else {
                                    adminService.deleteUser(action.userId)
                                        .onRight {
                                            toastData = ToastData(currentStrings.toastUserDeleted(action.userName), ToastType.Success)
                                            refreshTrigger++
                                        }
                                        .onLeft { error ->
                                            toastData = ToastData(currentStrings.errorToast(error.localizedMessage), ToastType.Error)
                                        }
                                }
                                pendingAction = null
                            }
                        }
                        if (action.type == UserAction.CHANGE_ROLE) +currentStrings.modalConfirmAction else +currentStrings.modalConfirmCancelAction
                    }
                }
            }
            form(className = "modal-backdrop") {
                button { onClick { pendingAction = null }; +"close" }
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}
