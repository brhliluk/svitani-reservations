package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.auth.ChangePasswordDialog
import cz.svitaninymburk.projects.reservations.ui.auth.ForgotPasswordDialog
import cz.svitaninymburk.projects.reservations.ui.auth.LoginDialog
import cz.svitaninymburk.projects.reservations.ui.auth.RegisterDialog
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.header
import dev.kilua.html.img
import dev.kilua.html.li
import dev.kilua.html.span
import dev.kilua.html.ul

enum class AuthModalState { Closed, Login, Register, ForgotPassword, ChangePassword }

@Composable
fun IComponent.AppHeader(
    user: User?,
    walletCode: String?,
    onShowMessage: (String, ToastType) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenMyReservations: () -> Unit,
    onOpenMyWallet: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdmin: (() -> Unit)? = null,
) {
    val currentStrings by strings
    var modalState by remember { mutableStateOf(AuthModalState.Closed) }

    LoginDialog(
        isOpen = modalState == AuthModalState.Login,
        onClose = { modalState = AuthModalState.Closed },
        onLogin = {
            modalState = AuthModalState.Closed
            onLogin()
        },
        onSwitchToRegister = { modalState = AuthModalState.Register },
        onSwitchToForgottenPassword = { modalState = AuthModalState.ForgotPassword },
    )

    RegisterDialog(
        isOpen = modalState == AuthModalState.Register,
        onClose = { modalState = AuthModalState.Closed },
        onSwitchToLogin = { modalState = AuthModalState.Login },
        onRegisterSuccess = {
            modalState = AuthModalState.Closed
            onLogin()
            onShowMessage(currentStrings.registrationSuccess, ToastType.Success)
        }
    )

    ForgotPasswordDialog(
        isOpen = modalState == AuthModalState.ForgotPassword,
        onClose = { modalState = AuthModalState.Closed },
        onSwitchToLogin = { modalState = AuthModalState.Login },
        onSuccess = {
            modalState = AuthModalState.Closed
            onShowMessage(currentStrings.forgotPasswordEmailSent, ToastType.Success)
        },
        onFailure = {
            modalState = AuthModalState.Closed
            onShowMessage(it, ToastType.Error)
        }
    )

    ChangePasswordDialog(
        isOpen = modalState == AuthModalState.ChangePassword,
        onClose = { modalState = AuthModalState.Closed },
        onSuccess = {
            modalState = AuthModalState.Closed
            onShowMessage(currentStrings.passwordChanged, ToastType.Success)
        }
    )

    header(className = "navbar min-h-16 bg-base-100 border-b border-primary/20 px-3 sm:px-8") {

        div(className = "navbar-start gap-3 sm:gap-4 cursor-pointer") {
            onClick { onNavigateToDashboard() }
            img(src = "logo.svg", alt = "Svítání", className = "h-9 sm:h-10 w-auto")
            span(className = "hidden sm:inline text-lg font-bold text-primary dark:text-base-content") { +currentStrings.dashboard }
        }

        div(className = "navbar-end gap-3 sm:gap-4") {
            div(className = "flex items-center gap-3 sm:pl-4 sm:border-l sm:border-base-200") {
                if (user != null) {
                    if (onNavigateToAdmin != null) {
                        button(className = "btn btn-outline btn-primary btn-sm min-h-11 gap-2") {
                            onClick { onNavigateToAdmin() }
                            span(className = "icon-[heroicons--shield-check] size-5")
                            span(className = "hidden sm:inline") { +currentStrings.adminPanelLink }
                        }
                    }
                    button(className = "btn btn-ghost btn-sm min-h-11 gap-2") {
                        onClick { onOpenMyReservations() }
                        span(className = "icon-[heroicons--ticket] size-5")
                        span(className = "hidden sm:inline") { +currentStrings.myReservations }
                    }
                    if (walletCode != null) {
                        button(className = "btn btn-ghost btn-sm min-h-11 gap-2") {
                            onClick { onOpenMyWallet() }
                            span(className = "icon-[heroicons--wallet] size-5")
                            span(className = "hidden sm:inline") { +currentStrings.wallet }
                        }
                    }

                    // User dropdown
                    div(className = "dropdown dropdown-end") {
                        div(className = "flex items-center gap-2 cursor-pointer hover:bg-base-200 rounded-lg px-2 py-1 transition-colors") {
                            attribute("tabindex", "0")
                            attribute("role", "button")
                            div(className = "avatar placeholder") {
                                attribute("aria-hidden", "true")
                                div(className = "bg-primary/10 text-primary w-10 rounded-full grid place-items-center") {
                                    span(className = "icon-[heroicons--user] size-6")
                                }
                            }
                            div(className = "hidden sm:flex flex-col text-sm") {
                                span(className = "font-semibold text-base-content") {
                                    +"${user.name} ${user.surname}"
                                }
                            }
                            span(className = "icon-[heroicons--chevron-down] size-4 text-base-content/50")
                        }
                        ul(className = "dropdown-content menu p-2 shadow-lg bg-base-100 rounded-box w-48 border border-base-200 z-50") {
                            attribute("tabindex", "0")
                            if (user is User.Email) {
                                li {
                                    div(className = "flex items-center gap-2") {
                                        onClick { modalState = AuthModalState.ChangePassword }
                                        span(className = "icon-[heroicons--key] size-4")
                                        +currentStrings.changePassword
                                    }
                                }
                            }
                            li {
                                div(className = "flex items-center gap-2 text-error") {
                                    onClick { onLogout() }
                                    span(className = "icon-[heroicons--arrow-right-on-rectangle] size-4")
                                    +currentStrings.logOut
                                }
                            }
                        }
                    }
                } else {
                    button(className = "btn btn-primary btn-sm min-h-11 px-4") {
                        onClick { modalState = AuthModalState.Login }
                        +currentStrings.logIn
                    }
                }
            }
        }
    }
}
