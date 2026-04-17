package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.auth.ForgotPasswordDialog
import cz.svitaninymburk.projects.reservations.ui.auth.LoginDialog
import cz.svitaninymburk.projects.reservations.ui.auth.RegisterDialog
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.header
import dev.kilua.html.span

enum class AuthModalState { Closed, Login, Register, ForgotPassword }

@Composable
fun IComponent.AppHeader(
    user: User?,
    onShowMessage: (String, ToastType) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
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

    header(className = "navbar min-h-14 bg-base-100 border-b border-base-200 px-3 sm:px-8") {

        div(className = "navbar-start gap-3 sm:gap-4") {
            div(className = "logo placeholder") {
                div(className = "bg-neutral text-neutral-content rounded-lg w-9 sm:w-10 grid place-items-center") {
                    span(className = "text-xl font-bold") { +"B" }
                }
            }
            span(className = "hidden sm:inline text-lg font-medium text-base-content") { +currentStrings.dashboard }
        }

        div(className = "navbar-end gap-3 sm:gap-4") {
            div(className = "flex items-center gap-3 sm:pl-4 sm:border-l sm:border-base-200") {
                if (user != null) {
                    div(className = "avatar placeholder") {
                        div(className = "bg-primary/10 text-primary w-10 rounded-full grid place-items-center") {
                            span(className = "icon-[heroicons--user] size-6")
                        }
                    }
                    div(className = "hidden sm:flex flex-col text-sm") {
                        span(className = "font-semibold text-base-content") {
                            +"${user.name} ${user.surname}"
                        }
                        button(className = "text-xs text-base-content/60 hover:text-primary text-left") {
                            onClick { onLogout() }
                            +currentStrings.logOut
                        }
                    }
                    button(className = "sm:hidden btn btn-ghost btn-sm min-h-11") {
                        onClick { onLogout() }
                        +currentStrings.logOut
                    }
                } else {
                    button(className = "btn btn-ghost btn-sm min-h-11") {
                        onClick { modalState = AuthModalState.Login }
                        +currentStrings.logIn
                    }
                }
            }
        }
    }
}