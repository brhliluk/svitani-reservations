package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.UserServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.text.password
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

@Composable
fun IComponent.ChangePasswordDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSuccess: () -> Unit,
) {
    if (!isOpen) return

    val userService = getService<UserServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val passwordMismatch = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword
    val isFormValid = oldPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword

    fun submit() {
        if (isLoading || !isFormValid) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                userService.changePassword(oldPassword, newPassword)
                    .onRight {
                        isLoading = false
                        onSuccess()
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

            h3(className = "font-bold text-lg mb-4") { +currentStrings.changePasswordTitle }

            div(className = "form-control w-full") {
                label(className = "label pb-1") { span(className = "label-text") { +currentStrings.oldPassword } }
                password(value = oldPassword, className = "input input-bordered w-full") {
                    placeholder("······")
                    autocomplete(Autocomplete.CurrentPassword)
                    attribute("aria-required", "true")
                    onInput { oldPassword = value ?: "" }
                }
            }

            div(className = "form-control w-full mt-4") {
                label(className = "label pb-1") { span(className = "label-text") { +currentStrings.newPassword } }
                password(value = newPassword, className = "input input-bordered w-full") {
                    placeholder("······")
                    autocomplete(Autocomplete.NewPassword)
                    attribute("aria-required", "true")
                    onInput { newPassword = value ?: "" }
                }
                label(className = "label pt-1") {
                    span(className = "label-text-alt text-base-content/50") { +currentStrings.passwordMinLengthNote }
                }
            }

            div(className = "form-control w-full mt-4") {
                label(className = "label pb-1") { span(className = "label-text") { +currentStrings.confirmNewPassword } }
                password(value = confirmPassword, className = "input input-bordered w-full ${if (passwordMismatch) "input-error" else ""}") {
                    placeholder("······")
                    autocomplete(Autocomplete.NewPassword)
                    attribute("aria-required", "true")
                    onInput { confirmPassword = value ?: "" }
                    onKeydown { e -> if (e.key == "Enter") submit() }
                }
                if (passwordMismatch) {
                    label(className = "label pt-1") {
                        span(className = "label-text-alt text-error") { +currentStrings.passwordMismatchError }
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
                    +currentStrings.changePassword
                    onClick { submit() }
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
