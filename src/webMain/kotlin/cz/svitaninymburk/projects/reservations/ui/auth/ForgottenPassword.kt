package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.InputType
import dev.kilua.form.text.text
import dev.kilua.html.*

@Composable
fun IComponent.ForgotPasswordDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: (message: String) -> Unit,
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildForgotPasswordModel(scope) }

    AuthDialog(
        title = currentStrings.forgotPasswordTitle,
        subtitle = currentStrings.forgotPasswordSubtitle,
        onClose = onClose,
    ) {

        div(className = "form-control w-full") {
            label(className = "label pb-1") { span(className = "label-text") { +currentStrings.emailLabel } }
            text(value = model.email, type = InputType.Email, className = "input input-bordered w-full") {
                placeholder("vas@email.cz")
                autocomplete(Autocomplete.Email)
                attribute("aria-required", "true")
                onInput { model.setEmail(value ?: "") }
            }
        }

        div(className = "modal-action") {
            button(className = "btn btn-primary w-full") {
                disabled(model.isLoading || !model.isFormValid)
                if (model.isLoading) span(className = "loading loading-spinner")
                +currentStrings.sendInstructionsButton
                onClick { model.requestReset(onSent = onSuccess, onFailed = onFailure) }
            }
        }

        div(className = "text-center mt-4 text-sm") {
            a(className = "link link-primary cursor-pointer") {
                onClick { onSwitchToLogin() }
                +currentStrings.backToLoginLink
            }
        }
    }
}
