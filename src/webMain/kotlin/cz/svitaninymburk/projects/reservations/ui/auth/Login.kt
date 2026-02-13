package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.text.password
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

@Composable
fun IComponent.LoginDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onLogin: () -> Unit,
    onSwitchToRegister: () -> Unit,
) {
    if (!isOpen) return

    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isFormValid = email.isNotBlank() && password.isNotBlank()

    fun login() {
        if (isLoading) return
        isLoading = true
        errorMessage = null

        scope.launch {
            authService.login(LoginRequest(email, password))
                .onRight {
                    isLoading = false
                    onLogin()
                    onClose()
                }
                .onLeft {
                    isLoading = false
                    errorMessage = it.localizedMessage
                }
        }
    }

    div(className = "modal modal-open modal-bottom sm:modal-middle") {
        div(className = "modal-box") {
            button(className = "btn btn-sm btn-circle btn-ghost absolute right-2 top-2") {
                onClick { onClose() }
                +"✕"
            }

            h3(className = "font-bold text-lg mb-4") { +"Přihlášení" }

            div(className = "form-control w-full") {
                label(className = "label") { span(className = "label-text") { +"Email" } }

                text(value = email, className = "input input-bordered w-full") {
                    placeholder("vas@email.cz")
                    autocomplete(Autocomplete.Username)
                    onInput { email = value ?: "" }
                }
            }

            div(className = "form-control w-full mt-4") {
                label(className = "label pt-1") { span(className = "label-text font-medium") { +"Heslo" } }

                password(value = password, className = "input input-bordered w-full") {
                    placeholder("******")
                    autocomplete(Autocomplete.CurrentPassword)
                    onInput { password = value ?: "" }
                    onKeydown { e -> if (e.key == "Enter") login() }
                }

                label(className = "label p-0 mt-2") {
                    span(className = "label-text-alt") { +"" }

                    a(className = "label-text-alt link link-hover text-primary text-sm cursor-pointer") {
                        href("#")
                        // onClick { ... } // TODO: Logika pro reset hesla
                        +"Zapomněli jste heslo?"
                    }
                }
            }

            if (errorMessage != null) {
                div(className = "alert alert-error mt-4 text-sm py-2") {
                    span(className = "icon-[heroicons--exclamation-circle] size-5")
                    span { +errorMessage!! }
                }
            }

            // --- AKCE ---
            div(className = "modal-action") {
                button(className = "btn btn-primary w-full") {
                    disabled(isLoading || !isFormValid)
                    if (isLoading) span(className = "loading loading-spinner")
                    +"Přihlásit se"
                    onClick { login() }
                }
            }

            div(className = "text-center mt-4 text-sm") {
                +"Ještě nemáte účet? "
                a(className = "link link-primary cursor-pointer") {
                    onClick { onSwitchToRegister() }
                    +"Zaregistrujte se"
                }
            }
        }

        div(className = "modal-backdrop") {
            button { onClick { onClose() } }
        }
    }
}