package cz.svitaninymburk.projects.reservations.ui.admin.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.text.text
import dev.kilua.html.*

@Composable
fun IComponent.AdminSettingsScreen() {
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember { buildAdminSettingsModel(scope) }

    LaunchedEffect(Unit) { model.load() }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.settingsTitle }

        when (val state = model.uiState) {
            is AdminSettingsUiState.Loading -> Loading()
            is AdminSettingsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminSettingsUiState.Success -> {
                model.email?.let { EmailSettingsCard(it, currentStrings, model) }
                model.payment?.let { PaymentSettingsCard(it, currentStrings, model) }
            }
        }
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}

/** Alert s výsledkem testu a varování "nejdřív otestuj" jsou v obou kartách stejné. */
@Composable
private fun IComponent.TestGateFeedback(gate: TestGate, needsTest: Boolean, currentStrings: AppStrings) {
    gate.result?.let { result ->
        div(className = "mt-4 alert ${if (result.isError) "alert-error" else "alert-success"}") {
            span(className = "icon-[heroicons--${if (result.isError) "x-circle" else "check-circle"}] size-5")
            span { +result.message }
        }
    }
    if (needsTest) {
        div(className = "mt-2 text-sm text-warning flex items-center gap-1") {
            span(className = "icon-[heroicons--exclamation-triangle] size-4")
            span { +currentStrings.settingsTestRequiredBeforeSave }
        }
    }
}

/** Zamaskovaná hodnota s tlačítkem "Změnit", které pole odemkne k editaci. */
@Composable
private fun IComponent.MaskedSecretField(masked: String, changeLabel: String, onStartChange: () -> Unit) {
    div(className = "flex gap-2 items-center") {
        span(className = "input input-bordered flex-1 font-mono text-base-content/60") { +masked }
        button(className = "btn btn-outline btn-sm") {
            onClick { onStartChange() }
            +changeLabel
        }
    }
}

@Composable
private fun IComponent.TestAndSaveActions(
    gate: TestGate,
    saveEnabled: Boolean,
    testLabel: String,
    saveLabel: String,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    div(className = "card-actions justify-end mt-4 gap-2") {
        button(className = "btn btn-outline${if (gate.isTesting) " loading" else ""}") {
            onClick { onTest() }
            +testLabel
        }
        button(className = "btn btn-primary${if (!saveEnabled) " btn-disabled" else ""}${if (gate.isSaving) " loading" else ""}") {
            onClick { onSave() }
            +saveLabel
        }
    }
}

@Composable
private fun IComponent.EmailSettingsCard(
    form: EmailSettingsForm,
    currentStrings: AppStrings,
    model: AdminSettingsModel,
) {
    div(className = "card bg-base-100 shadow-sm border border-base-200") {
        div(className = "card-body") {

            h2(className = "card-title text-xl font-bold mb-4") {
                span(className = "icon-[heroicons--envelope] size-5")
                +currentStrings.settingsEmailCard
            }

            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsSenderDisplayName }
                    }
                    text(value = form.senderDisplayName, className = "input input-bordered w-full") {
                        onInput { model.email?.setDisplayName(value ?: "") }
                    }
                }

                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsSenderEmail }
                    }
                    text(value = form.senderEmail, type = InputType.Email, className = "input input-bordered w-full") {
                        onInput { model.email?.setSenderEmail(value ?: "") }
                    }
                }

                div(className = "form-control md:col-span-2") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsGmailPassword }
                    }
                    if (form.passwordChanging) {
                        text(
                            value = form.newPassword ?: "",
                            type = InputType.Password,
                            className = "input input-bordered w-full",
                        ) {
                            onInput { model.email?.setPassword(value ?: "") }
                        }
                    } else {
                        MaskedSecretField(
                            masked = form.stored.gmailPasswordMasked,
                            changeLabel = currentStrings.settingsChangeButton,
                            onStartChange = { form.startPasswordChange() },
                        )
                    }
                    p(className = "text-xs text-base-content/50 mt-1 flex items-center gap-1") {
                        span(className = "icon-[heroicons--information-circle] size-3")
                        +currentStrings.settingsGmailPasswordHint
                    }
                }
            }

            TestGateFeedback(form.gate, form.needsTest, currentStrings)

            TestAndSaveActions(
                gate = form.gate,
                saveEnabled = form.saveEnabled,
                testLabel = currentStrings.settingsTestEmailButton,
                saveLabel = currentStrings.settingsSaveButton,
                onTest = { model.testEmail() },
                onSave = { model.saveEmail() },
            )
        }
    }
}

@Composable
private fun IComponent.PaymentSettingsCard(
    form: PaymentSettingsForm,
    currentStrings: AppStrings,
    model: AdminSettingsModel,
) {
    div(className = "card bg-base-100 shadow-sm border border-base-200") {
        div(className = "card-body") {

            h2(className = "card-title text-xl font-bold mb-4") {
                span(className = "icon-[heroicons--banknotes] size-5")
                +currentStrings.settingsPaymentCard
            }

            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsBankAccount }
                    }
                    text(value = form.bankAccountNumber, className = "input input-bordered w-full") {
                        onInput { model.payment?.setBankAccountNumber(value ?: "") }
                    }
                }

                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsFioToken }
                    }
                    if (form.fioTokenChanging) {
                        text(
                            value = form.newFioToken ?: "",
                            type = InputType.Password,
                            className = "input input-bordered w-full",
                        ) {
                            onInput { model.payment?.setFioToken(value ?: "") }
                        }
                    } else {
                        MaskedSecretField(
                            masked = form.stored.fioTokenMasked,
                            changeLabel = currentStrings.settingsChangeButton,
                            onStartChange = { form.startFioTokenChange() },
                        )
                    }
                    p(className = "text-xs text-base-content/50 mt-1 flex items-center gap-1") {
                        span(className = "icon-[heroicons--information-circle] size-3")
                        +currentStrings.settingsFioTokenHint
                    }
                }
            }

            TestGateFeedback(form.gate, form.needsTest, currentStrings)

            TestAndSaveActions(
                gate = form.gate,
                saveEnabled = form.saveEnabled,
                testLabel = currentStrings.settingsTestFioButton,
                saveLabel = currentStrings.settingsSaveButton,
                onTest = { model.testFio() },
                onSave = { model.savePayment() },
            )
        }
    }
}
