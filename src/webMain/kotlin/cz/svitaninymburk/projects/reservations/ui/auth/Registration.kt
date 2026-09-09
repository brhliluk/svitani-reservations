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
import dev.kilua.form.text.text
import dev.kilua.html.*
import web.events.Event

@Composable
fun IComponent.RegisterDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildRegisterModel(scope) }

    fun register() = model.register(
        onRegistered = { onRegisterSuccess(); onClose() },
        onNeedsManualLogin = onSwitchToLogin,
    )

    AuthDialog(
        title = currentStrings.registerTitle,
        subtitle = currentStrings.registerSubtitle,
        onClose = onClose,
    ) {

        form(className = "flex flex-col gap-2") {
            onEvent<Event>("submit") { it.preventDefault() }

            div(className = "flex gap-3") {
                div(className = "form-control w-1/2") {
                    label(className = "label pb-1") { span(className = "label-text") { +currentStrings.nameLabel } }
                    text(value = model.name, className = "input input-bordered w-full", name = "given-name") {
                        id("reg-name")
                        autocomplete(Autocomplete.GivenName)
                        attribute("aria-required", "true")
                        onInput { model.setName(value ?: "") }
                    }
                }
                div(className = "form-control w-1/2") {
                    label(className = "label pb-1") { span(className = "label-text") { +currentStrings.surnameLabel } }
                    text(value = model.surname, className = "input input-bordered w-full", name = "family-name") {
                        id("reg-surname")
                        autocomplete(Autocomplete.FamilyName)
                        attribute("aria-required", "true")
                        onInput { model.setSurname(value ?: "") }
                    }
                }
            }

            div(className = "form-control w-full") {
                label(className = "label pb-1") { span(className = "label-text") { +currentStrings.emailLabel } }
                text(value = model.email, className = "input input-bordered w-full", name = "email") {
                    id("reg-email")
                    autocomplete(Autocomplete.Email)
                    placeholder("vas@email.cz")
                    attribute("aria-required", "true")
                    onInput { model.setEmail(value ?: "") }
                }
            }

            div(className = "form-control w-full") {
                label(className = "label pb-1") {
                    span(className = "label-text") { +currentStrings.passwordLabel }
                    span(className = "label-text-alt text-base-content/50") { +"(${currentStrings.passwordMinLengthNote})" }
                }
                password(value = model.password, className = "input input-bordered w-full") {
                    id("reg-password")
                    autocomplete(Autocomplete.NewPassword)
                    attribute("aria-required", "true")
                    onInput { model.setPassword(value ?: "") }
                }
            }

            div(className = "form-control w-full") {
                label(className = "label pb-1") { span(className = "label-text") { +currentStrings.passwordConfirmLabel } }

                val mismatch = model.showsMismatch
                password(
                    value = model.passwordConfirm,
                    className = if (mismatch) "input input-bordered input-error w-full" else "input input-bordered w-full",
                ) {
                    id("reg-password-confirm")
                    autocomplete(Autocomplete.NewPassword)
                    attribute("aria-required", "true")
                    if (mismatch) {
                        attribute("aria-invalid", "true")
                        attribute("aria-describedby", "reg-password-error")
                    }
                    onInput { model.setPasswordConfirm(value ?: "") }
                    onKeydown { if (it.key == "Enter") register() }
                }

                if (mismatch) {
                    label(className = "label pb-0 pt-1") {
                        span(className = "label-text-alt text-error", id = "reg-password-error") {
                            +currentStrings.passwordMismatchError
                        }
                    }
                }
            }
        }

        AuthErrorAlert(model.errorMessage)

        div(className = "modal-action") {
            button(className = "btn btn-primary w-full") {
                disabled(model.isLoading || !model.isFormValid)
                if (model.isLoading) span(className = "loading loading-spinner")
                +currentStrings.createAccountButton
                onClick { register() }
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
}
