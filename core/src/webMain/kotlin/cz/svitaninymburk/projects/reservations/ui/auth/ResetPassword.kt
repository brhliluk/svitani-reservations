package cz.svitaninymburk.projects.reservations.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import dev.kilua.form.Autocomplete
import dev.kilua.form.form
import dev.kilua.form.text.password
import dev.kilua.html.*
import web.events.Event

@Composable
fun IComponent.ResetPasswordScreen(
    token: String,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(token) { buildResetPasswordModel(scope, token) }

    div(className = "min-h-screen bg-base-200 flex items-center justify-center px-4") {

        // Karta
        div(className = "card w-full max-w-sm shadow-2xl bg-base-100") {
            div(className = "card-body") {
                h2(className = "card-title justify-center mb-4") { +currentStrings.resetPasswordTitle }

                form(className = "flex flex-col gap-2") {
                    onEvent<Event>("submit") { it.preventDefault() }

                    div(className = "form-control w-full") {
                        label(className = "label") {
                            span(className = "label-text") { +currentStrings.resetPasswordNewLabel }
                        }
                        password(value = model.password, className = "input input-bordered", name = "new-password") {
                            autocomplete(Autocomplete.NewPassword)
                            onInput { model.setPassword(value ?: "") }
                        }
                        label(className = "label pt-1") {
                            span(className = "label-text-alt text-base-content/50") { +currentStrings.passwordMinLengthNote }
                        }
                    }

                    div(className = "form-control w-full") {
                        label(className = "label") {
                            span(className = "label-text") { +currentStrings.resetPasswordConfirmLabel }
                        }
                        password(
                            value = model.passwordConfirm,
                            className = "input input-bordered ${if (model.showsMismatch) "input-error" else ""}",
                            name = "new-password-confirm",
                        ) {
                            autocomplete(Autocomplete.NewPassword)
                            onInput { model.setPasswordConfirm(value ?: "") }
                            onKeydown { if (it.key == "Enter") model.resetPassword(onReset = onSuccess) }
                        }
                        if (model.showsMismatch) {
                            label(className = "label pt-1") {
                                span(className = "label-text-alt text-error") { +currentStrings.passwordMismatchError }
                            }
                        }
                    }
                }

                AuthErrorAlert(model.errorMessage)

                div(className = "card-actions justify-end mt-4") {
                    button(className = "btn btn-primary w-full") {
                        disabled(model.isLoading || !model.isFormValid)
                        if (model.isLoading) span(className = "loading loading-spinner")
                        +currentStrings.resetPasswordSubmit
                        onClick { model.resetPassword(onReset = onSuccess) }
                    }
                }
            }
        }
    }
}
