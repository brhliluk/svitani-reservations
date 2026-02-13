package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.InputType
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

@Composable
fun IComponent.ForgotPasswordDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: (message: String) -> Unit,
) {
    if (!isOpen) return

    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    div(className = "modal modal-open modal-bottom sm:modal-middle") {
        div(className = "modal-box") {
            button(className = "btn btn-sm btn-circle btn-ghost absolute right-2 top-2") {
                onClick { onClose() }
                +"✕"
            }

            h3(className = "font-bold text-lg mb-4") { +"Obnova hesla" }
            p(className = "text-sm text-base-content/70 mb-4") {
                +"Zadejte email, se kterým jste se registrovali. Pošleme vám odkaz pro nastavení nového hesla."
            }

            div(className = "form-control w-full") {
                label(className = "label pb-1") { span(className = "label-text") { +"Email" } }
                text(value = email, type = InputType.Email, className = "input input-bordered w-full") {
                    placeholder("vas@email.cz")
                    autocomplete(Autocomplete.Email)
                    onInput { email = value ?: "" }
                }
            }

            div(className = "modal-action") {
                button(className = "btn btn-primary w-full") {
                    disabled(isLoading || email.isBlank())
                    if (isLoading) span(className = "loading loading-spinner")
                    +"Odeslat instrukce"

                    onClick {
                        isLoading = true
                        scope.launch {
                            authService.requestPasswordReset(email)
                                .onLeft {
                                    isLoading = false
                                    onFailure(it.localizedMessage)
                                }
                                .onRight {
                                    isLoading = false
                                    onSuccess()
                                }
                        }
                    }
                }
            }

            div(className = "text-center mt-4 text-sm") {
                a(className = "link link-primary cursor-pointer") {
                    onClick { onSwitchToLogin() }
                    +"Zpět na přihlášení"
                }
            }
        }
        div(className = "modal-backdrop") { button { onClick { onClose() } } }
    }
}