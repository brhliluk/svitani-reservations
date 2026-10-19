package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.text.password
import dev.kilua.form.text.text
import dev.kilua.html.*

@Composable
fun IComponent.LoginDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onLogin: () -> Unit,
    onSwitchToRegister: () -> Unit,
    onSwitchToForgottenPassword: () -> Unit,
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildLoginModel(scope) }

    fun login() = model.login(onLoggedIn = { onLogin(); onClose() })

    AuthDialog(title = currentStrings.loginTitle, onClose = onClose) {

        div(className = "form-control w-full") {
            label(className = "label") { span(className = "label-text") { +currentStrings.emailLabel } }

            text(value = model.email, className = "input input-bordered w-full") {
                placeholder("vas@email.cz")
                autocomplete(Autocomplete.Username)
                attribute("aria-required", "true")
                onInput { model.setEmail(value ?: "") }
            }
        }

        div(className = "form-control w-full mt-4") {
            label(className = "label pt-1") { span(className = "label-text font-medium") { +currentStrings.passwordLabel } }

            password(value = model.password, className = "input input-bordered w-full") {
                placeholder("······")
                autocomplete(Autocomplete.CurrentPassword)
                attribute("aria-required", "true")
                onInput { model.setPassword(value ?: "") }
                onKeydown { e -> if (e.key == "Enter") login() }
            }

            label(className = "label p-0 mt-2") {
                span(className = "label-text-alt") { +"" }

                a(className = "label-text-alt link link-hover text-primary text-sm cursor-pointer") {
                    href("#")
                    onClick { onSwitchToForgottenPassword() }
                    +currentStrings.forgotPasswordLink
                }
            }
        }

        AuthErrorAlert(model.errorMessage)

        div(className = "modal-action") {
            button(className = "btn btn-primary w-full") {
                disabled(model.isLoading || !model.isFormValid)
                if (model.isLoading) span(className = "loading loading-spinner")
                +currentStrings.logIn
                onClick { login() }
            }
        }

        div(className = "text-center mt-4 text-sm") {
            +currentStrings.noAccountYet
            +" "
            a(className = "link link-primary cursor-pointer") {
                onClick { onSwitchToRegister() }
                +currentStrings.signUpLink
            }
        }
    }
}
