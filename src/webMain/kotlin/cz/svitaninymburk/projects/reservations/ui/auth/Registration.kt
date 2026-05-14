package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.auth.LoginRequest
import cz.svitaninymburk.projects.reservations.auth.RegisterRequest
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
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
    val currentStrings by strings

    val isFormValid = name.isNotBlank() && surname.isNotBlank() &&
            email.isNotBlank() && password.length >= 6 &&
            password == passwordConfirm

    fun performRegister() {
        if (!isFormValid) return
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                authService.register(RegisterRequest(email, password, name, surname))
                    .onRight {
                        authService.login(LoginRequest(email, password))
                            .onRight {
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
                        errorMessage = it.localizedMessage(currentStrings)
                    }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = currentStrings.errorProcessingRequest
            }
        }
    }

    div(className = "modal modal-open modal-bottom sm:modal-middle") {
        div(className = "modal-box") {
            button(className = "btn btn-sm btn-circle btn-ghost absolute right-2 top-2") {
                attribute("aria-label", currentStrings.close)
                onClick { onClose() }
                +"✕"
            }

            h3(className = "font-bold text-lg mb-4") { +currentStrings.registerTitle }

            p(className = "text-sm text-base-content/70 mb-4") {
                +currentStrings.registerSubtitle
            }

            form(className = "flex flex-col gap-2") {
                onEvent<Event>("submit") { it.preventDefault() }

                div(className = "flex gap-3") {
                    div(className = "form-control w-1/2") {
                        label(className = "label pb-1") { span(className = "label-text") { +currentStrings.nameLabel } }
                        text(value = name, className = "input input-bordered w-full", name = "given-name") {
                            id("reg-name")
                            autocomplete(Autocomplete.GivenName)
                            attribute("aria-required", "true")
                            onInput { name = value ?: "" }
                        }
                    }
                    div(className = "form-control w-1/2") {
                        label(className = "label pb-1") { span(className = "label-text") { +currentStrings.surnameLabel } }
                        text(value = surname, className = "input input-bordered w-full", name = "family-name") {
                            id("reg-surname")
                            autocomplete(Autocomplete.FamilyName)
                            attribute("aria-required", "true")
                            onInput { surname = value ?: "" }
                        }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label pb-1") { span(className = "label-text") { +currentStrings.emailLabel } }
                    text(value = email, className = "input input-bordered w-full", name = "email") {
                        id("reg-email")
                        autocomplete(Autocomplete.Email)
                        placeholder("vas@email.cz")
                        attribute("aria-required", "true")
                        onInput { email = value ?: "" }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label pb-1") {
                        span(className = "label-text") { +currentStrings.passwordLabel }
                        span(className = "label-text-alt text-base-content/50") { +"(${currentStrings.passwordMinLengthNote})" }
                    }
                    password(value = password, className = "input input-bordered w-full") {
                        id("reg-password")
                        autocomplete(Autocomplete.NewPassword)
                        attribute("aria-required", "true")
                        onInput { password = value ?: "" }
                    }
                }

                div(className = "form-control w-full") {
                    label(className = "label pb-1") { span(className = "label-text") { +currentStrings.passwordConfirmLabel } }

                    val inputClass = if (passwordConfirm.isNotEmpty() && password != passwordConfirm)
                        "input input-bordered input-error w-full"
                    else
                        "input input-bordered w-full"

                    password(value = passwordConfirm, className = inputClass) {
                        id("reg-password-confirm")
                        autocomplete(Autocomplete.NewPassword)
                        attribute("aria-required", "true")
                        if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                            attribute("aria-invalid", "true")
                            attribute("aria-describedby", "reg-password-error")
                        }
                        onInput { passwordConfirm = value ?: "" }
                        onKeydown { if (it.key == "Enter") performRegister() }
                    }

                    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                        label(className = "label pb-0 pt-1") {
                            span(className = "label-text-alt text-error", id = "reg-password-error") {
                                +currentStrings.passwordMismatchError
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                div(className = "alert alert-error mt-4 text-sm py-2") {
                    span(className = "icon-[heroicons--exclamation-circle] size-5")
                    span { +errorMessage!! }
                }
            }

            div(className = "modal-action") {
                button(className = "btn btn-primary w-full") {
                    disabled(isLoading || !isFormValid)
                    if (isLoading) span(className = "loading loading-spinner")
                    +currentStrings.createAccountButton
                    onClick { performRegister() }
                }
            }

            p(className = "text-xs text-center text-base-content/60 mt-1") {
                +currentStrings.registrationPrivacyNote
                +" "
                a(href = "/privacy", className = "link link-primary") {
                    target("_blank")
                    +currentStrings.privacyPolicyLink
                }
                +"."
            }

            div(className = "text-center mt-4 text-sm") {
                +currentStrings.alreadyHaveAccount
                +" "
                a(className = "link link-primary cursor-pointer") {
                    onClick { onSwitchToLogin() }
                    +currentStrings.signInLink
                }
            }
        }
        div(className = "modal-backdrop") {
            button {
                attribute("aria-label", currentStrings.close)
                onClick { onClose() }
            }
        }
    }
}