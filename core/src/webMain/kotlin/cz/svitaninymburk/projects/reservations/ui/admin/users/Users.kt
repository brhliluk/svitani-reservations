package cz.svitaninymburk.projects.reservations.ui.admin.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.users.usecase.filterUsers
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.html.*
import kotlin.uuid.Uuid

@Composable
fun IComponent.AdminUsersScreen(currentUserId: Uuid) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminUsersModel(scope, currentUserId) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- 1. HEADER + SEARCH ---
        div(className = "flex flex-col md:flex-row justify-between items-start md:items-center gap-4") {
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.allUsers }
                p(className = "text-base-content/60 mt-1") { +currentStrings.usersSubtitle }
            }

            div(className = "flex items-center w-full md:w-auto gap-2") {
                div(className = "relative w-full md:w-80") {
                    span(className = "absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/50") {
                        span(className = "icon-[heroicons--magnifying-glass] size-5")
                    }
                    text(value = model.searchInput, className = "input input-bordered w-full pl-10") {
                        placeholder(currentStrings.usersSearchPlaceholder)
                        onInput { model.searchInput = value ?: "" }
                        onKeyup { event -> if (event.key == "Enter") model.submitSearch() }
                    }
                }
                button(className = "btn btn-primary") {
                    onClick { model.submitSearch() }
                    +currentStrings.search
                }
                if (!model.activeSearchQuery.isNullOrBlank()) {
                    button(className = "btn btn-ghost tooltip") {
                        attribute("data-tip", currentStrings.clearSearch)
                        onClick { model.clearSearch() }
                        span(className = "icon-[heroicons--x-mark] size-5")
                    }
                }
            }
        }

        // --- 2. TABLE ---
        when (val state = model.uiState) {
            is AdminUsersUiState.Loading -> Loading()
            is AdminUsersUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminUsersUiState.Success -> {
                val data = filterUsers(state.data, model.activeSearchQuery)

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
                                                    val query = model.activeSearchQuery
                                                    if (query != null) +currentStrings.noUsersForSearch(query)
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
                                                            onClick { model.requestRoleChange(user) }
                                                            span(className = "icon-[heroicons--arrows-right-left] size-5")
                                                        }
                                                        if (user.authType == AdminUserListItem.AuthType.EMAIL) {
                                                            val isResetting = model.resettingPasswordForId == user.id
                                                            button(className = "btn btn-ghost btn-xs tooltip tooltip-left") {
                                                                attribute("data-tip", currentStrings.tooltipResetPassword)
                                                                disabled(isResetting)
                                                                if (isResetting) {
                                                                    span(className = "loading loading-spinner loading-xs")
                                                                } else {
                                                                    span(className = "icon-[heroicons--envelope] size-5")
                                                                }
                                                                onClick { model.resetPassword(user) }
                                                            }
                                                        }
                                                        if (model.canDelete(user)) {
                                                            button(className = "btn btn-ghost btn-xs text-error tooltip tooltip-left") {
                                                                attribute("data-tip", currentStrings.tooltipDeleteUser)
                                                                onClick { model.requestDelete(user) }
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

    // --- 3. MODAL A TOAST ---
    model.pendingAction?.let { action ->
        UserActionModal(
            action = action,
            isLoading = model.isModalLoading,
            onConfirm = { model.confirmPendingAction(action) },
            onDismiss = { model.dismissPendingAction() },
        )
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
