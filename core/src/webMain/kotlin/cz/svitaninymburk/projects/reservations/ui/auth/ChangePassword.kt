package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.text.password
import dev.kilua.html.*

@Composable
fun IComponent.ChangePasswordDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSuccess: () -> Unit,
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildChangePasswordModel(scope) }

    AuthDialog(title = currentStrings.changePasswordTitle, onClose = onClose) {

        div(className = "form-control w-full") {
            label(className = "label pb-1") { span(className = "label-text") { +currentStrings.oldPassword } }
            password(value = model.oldPassword, className = "input input-bordered w-full") {
                placeholder("······")
                autocomplete(Autocomplete.CurrentPassword)
                attribute("aria-required", "true")
                onInput { model.setOldPassword(value ?: "") }
            }
        }

        div(className = "form-control w-full mt-4") {
            label(className = "label pb-1") { span(className = "label-text") { +currentStrings.newPassword } }
            password(value = model.newPassword, className = "input input-bordered w-full") {
                placeholder("······")
                autocomplete(Autocomplete.NewPassword)
                attribute("aria-required", "true")
                onInput { model.setNewPassword(value ?: "") }
            }
            label(className = "label pt-1") {
                span(className = "label-text-alt text-base-content/50") { +currentStrings.passwordMinLengthNote }
            }
        }

        div(className = "form-control w-full mt-4") {
            label(className = "label pb-1") { span(className = "label-text") { +currentStrings.confirmNewPassword } }
            password(
                value = model.confirmPassword,
                className = "input input-bordered w-full ${if (model.showsMismatch) "input-error" else ""}",
            ) {
                placeholder("······")
                autocomplete(Autocomplete.NewPassword)
                attribute("aria-required", "true")
                onInput { model.setConfirmPassword(value ?: "") }
                onKeydown { e -> if (e.key == "Enter") model.submitChange(onChanged = onSuccess) }
            }
            if (model.showsMismatch) {
                label(className = "label pt-1") {
                    span(className = "label-text-alt text-error") { +currentStrings.passwordMismatchError }
                }
            }
        }

        AuthErrorAlert(model.errorMessage)

        div(className = "modal-action") {
            button(className = "btn btn-primary w-full") {
                disabled(model.isLoading || !model.isFormValid)
                if (model.isLoading) span(className = "loading loading-spinner")
                +currentStrings.changePassword
                onClick { model.submitChange(onChanged = onSuccess) }
            }
        }
    }
}
