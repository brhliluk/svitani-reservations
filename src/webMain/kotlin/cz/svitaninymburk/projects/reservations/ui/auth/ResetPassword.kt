package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.form
import dev.kilua.form.text.password
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.events.Event

@Composable
fun IComponent.ResetPasswordScreen(
    token: String,
    onSuccess: () -> Unit,
) {
    val authService = getService<AuthServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isFormValid = password.length >= 6 && password == passwordConfirm

    fun resetPassword() {
        isLoading = true
        errorMessage = null
        scope.launch {
            authService.resetPassword(token, password)
                .onRight { onSuccess() }
                .onLeft { errorMessage = it.localizedMessage }
            isLoading = false
        }
    }

    div(className = "min-h-screen bg-base-200 flex items-center justify-center px-4") {

        // Karta
        div(className = "card w-full max-w-sm shadow-2xl bg-base-100") {
            div(className = "card-body") {
                h2(className = "card-title justify-center mb-4") { +"Nové heslo" }

                form(className = "flex flex-col gap-2") {
                    onEvent<Event>("submit") { it.preventDefault() }

                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text") { +"Zadejte nové heslo" } }
                        password(value = password, className = "input input-bordered", name = "new-password") {
                            autocomplete(Autocomplete.NewPassword)
                            onInput { password = value ?: "" }
                        }
                    }

                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text") { +"Potvrzení hesla" } }
                        password(value = passwordConfirm, className = "input input-bordered", name = "new-password-confirm") {
                            autocomplete(Autocomplete.NewPassword)
                            onInput { passwordConfirm = value ?: "" }

                            onKeydown { if (it.key == "Enter" && isFormValid && !isLoading) { resetPassword() } }
                        }
                    }
                }

                if (errorMessage != null) {
                    div(className = "alert alert-error mt-2 text-sm") { +errorMessage!! }
                }

                div(className = "card-actions justify-end mt-4") {
                    button(className = "btn btn-primary w-full") {
                        disabled(isLoading || !isFormValid)
                        if (isLoading) span(className = "loading loading-spinner")
                        +"Uložit heslo"

                        onClick { resetPassword() }
                    }
                }
            }
        }
    }
}