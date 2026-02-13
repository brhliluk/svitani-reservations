package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.form
import dev.kilua.form.text.password
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.events.Event

@Composable
fun IComponent.RegisterDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    if (!isOpen) return

    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isFormValid = name.isNotBlank() && surname.isNotBlank() &&
            email.isNotBlank() && password.length >= 6 &&
            password == passwordConfirm

    fun performRegister() {
        if (!isFormValid) return
        isLoading = true
        errorMessage = null

        scope.launch {
            authService.register(RegisterRequest(email, password, name, surname))
                .onRight {
                    authService.login(LoginRequest(email, password))
                        .onRight{
                            isLoading = false
                            onRegisterSuccess()
                            onClose()
                        }
                        .onLeft {
                            isLoading = false
                            onSwitchToLogin()
                        }
                }
                .onLeft {
                    isLoading = false
                    errorMessage = "Registrace se nezdařila. Email už možná existuje."
                }
        }
    }

    div(className = "modal modal-open modal-bottom sm:modal-middle") {
        div(className = "modal-box") {
            button(className = "btn btn-sm btn-circle btn-ghost absolute right-2 top-2") {
                onClick { onClose() }
                +"✕"
            }

            h3(className = "font-bold text-lg mb-4") { +"Nová registrace" }

            p(className = "text-sm text-base-content/70 mb-4") {
                +"Zadejte platný email, na který vám budeme posílat potvrzení a QR kódy."
            }

            form(className = "flex flex-col gap-2") {
                onEvent<Event>("submit") { it.preventDefault() }

                // --- Jméno a Příjmení ---
                div(className = "flex gap-3") {
                    div(className = "form-control w-1/2") {
                        label(className = "label pb-1") { span(className = "label-text") { +"Jméno" } }
                        text(value = name, className = "input input-bordered w-full", name = "given-name") {
                            id("reg-name")
                            autocomplete(Autocomplete.GivenName)
                            onInput { name = value ?: "" }
                        }
                    }
                    div(className = "form-control w-1/2") {
                        label(className = "label pb-1") { span(className = "label-text") { +"Příjmení" } }
                        text(value = surname, className = "input input-bordered w-full", name = "family-name") {
                            id("reg-surname")
                            autocomplete(Autocomplete.FamilyName)
                            onInput { surname = value ?: "" }
                        }
                    }
                }

                // --- Email ---
                div(className = "form-control w-full") {
                    label(className = "label pb-1") { span(className = "label-text") { +"Email" } }
                    text(value = email, className = "input input-bordered w-full", name = "email") {
                        id("reg-email")
                        autocomplete(Autocomplete.Email)
                        placeholder("vas@email.cz")
                        onInput { email = value ?: "" }
                    }
                }

                // --- Heslo (New Password) ---
                div(className = "form-control w-full") {
                    label(className = "label pb-1") { span(className = "label-text") { +"Heslo (min. 6 znaků)" } }
                    password(value = password, className = "input input-bordered w-full") {
                        id("reg-password")
                        autocomplete(Autocomplete.NewPassword)
                        onInput { password = value ?: "" }
                    }
                }

                // --- Heslo Znovu ---
                div(className = "form-control w-full") {
                    label(className = "label pb-1") { span(className = "label-text") { +"Heslo znovu" } }

                    val inputClass = if (passwordConfirm.isNotEmpty() && password != passwordConfirm)
                        "input input-bordered input-error w-full"
                    else
                        "input input-bordered w-full"

                    password(value = passwordConfirm, className = inputClass) {
                        id("reg-password-confirm")
                        autocomplete(Autocomplete.NewPassword)
                        onInput { passwordConfirm = value ?: "" }
                        onKeydown { if (it.key == "Enter") performRegister() }
                    }

                    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                        label(className = "label pb-0 pt-1") {
                            span(className = "label-text-alt text-error") { +"Hesla se neshodují" }
                        }
                    }
                }
            }

            // Error alert
            if (errorMessage != null) {
                div(className = "alert alert-error mt-4 text-sm py-2") {
                    span { +errorMessage!! }
                }
            }

            div(className = "modal-action") {
                button(className = "btn btn-primary w-full") {
                       disabled(isLoading || !isFormValid)
                    if (isLoading) span(className = "loading loading-spinner")
                    +"Vytvořit účet"
                    onClick { performRegister() }
                }
            }

            div(className = "text-center mt-4 text-sm") {
                +"Už máte účet? "
                a(className = "link link-primary cursor-pointer") {
                    onClick { onSwitchToLogin() }
                    +"Přihlaste se"
                }
            }
        }
        div(className = "modal-backdrop") { button { onClick { onClose() } } }
    }
}