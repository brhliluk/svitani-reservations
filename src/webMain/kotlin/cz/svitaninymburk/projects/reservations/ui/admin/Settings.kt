package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.settings.UpdateSettingsRequest
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.text.text
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch

private sealed interface AdminSettingsUiState {
    data object Loading : AdminSettingsUiState
    data class Success(val data: AppSettingsDisplayDto) : AdminSettingsUiState
    data class Error(val message: String) : AdminSettingsUiState
}

@Composable
fun IComponent.AdminSettingsScreen() {
    val settingsService = getService<AppSettingsServiceInterface>(RpcSerializersModules)
    val currentStrings by strings

    var refreshTrigger by remember { mutableStateOf(0) }
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    val uiState by produceState<AdminSettingsUiState>(
        initialValue = AdminSettingsUiState.Loading,
        key1 = refreshTrigger
    ) {
        value = AdminSettingsUiState.Loading
        settingsService.getSettings()
            .onRight { value = AdminSettingsUiState.Success(it) }
            .onLeft { value = AdminSettingsUiState.Error(it.localizedMessage(currentStrings)) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in") {

        // --- Page Title ---
        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.settingsTitle }

        when (val state = uiState) {
            is AdminSettingsUiState.Loading -> Loading()
            is AdminSettingsUiState.Error -> {
                div(className = "alert alert-error") {
                    span(className = "icon-[heroicons--x-circle] size-6")
                    span { +state.message }
                }
            }
            is AdminSettingsUiState.Success -> {
                val dto = state.data

                // --- Email Card ---
                EmailSettingsCard(
                    dto = dto,
                    currentStrings = currentStrings,
                    onSaveSuccess = {
                        toastData = ToastData(currentStrings.settingsSavedSuccess, ToastType.Success)
                        refreshTrigger++
                    },
                    onSaveError = { msg ->
                        toastData = ToastData(currentStrings.errorToast(msg), ToastType.Error)
                    },
                    settingsService = settingsService,
                )

                // --- Payment Card ---
                PaymentSettingsCard(
                    dto = dto,
                    currentStrings = currentStrings,
                    onSaveSuccess = {
                        toastData = ToastData(currentStrings.settingsSavedSuccess, ToastType.Success)
                        refreshTrigger++
                    },
                    onSaveError = { msg ->
                        toastData = ToastData(currentStrings.errorToast(msg), ToastType.Error)
                    },
                    settingsService = settingsService,
                )
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}

@Composable
private fun IComponent.EmailSettingsCard(
    dto: AppSettingsDisplayDto,
    currentStrings: AppStrings,
    onSaveSuccess: () -> Unit,
    onSaveError: (String) -> Unit,
    settingsService: AppSettingsServiceInterface,
) {
    val scope = rememberCoroutineScope()
    var senderDisplayName by remember { mutableStateOf(dto.senderDisplayName) }
    var senderEmail by remember { mutableStateOf(dto.senderEmail) }
    var newPassword by remember { mutableStateOf<String?>(null) }
    var passwordChanging by remember { mutableStateOf(false) }
    var testPassed by remember { mutableStateOf(false) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var testResultIsError by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val senderEmailChanged = senderEmail != dto.senderEmail
    val passwordChanged = passwordChanging && newPassword != null
    val credentialsChanged = senderEmailChanged || passwordChanged
    val saveEnabled = !isSaving && !isTesting && (testPassed || !credentialsChanged)

    div(className = "card bg-base-100 shadow-sm border border-base-200") {
        div(className = "card-body") {

            h2(className = "card-title text-xl font-bold mb-4") {
                span(className = "icon-[heroicons--envelope] size-5")
                +currentStrings.settingsEmailCard
            }

            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                // Sender display name
                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsSenderDisplayName }
                    }
                    text(value = senderDisplayName, className = "input input-bordered w-full") {
                        onInput {
                            senderDisplayName = value ?: ""
                            testPassed = false
                            testResultMessage = null
                        }
                    }
                }

                // Sender email
                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsSenderEmail }
                    }
                    text(value = senderEmail, type = InputType.Email, className = "input input-bordered w-full") {
                        onInput {
                            senderEmail = value ?: ""
                            testPassed = false
                            testResultMessage = null
                        }
                    }
                }

                // Gmail password (sensitive field)
                div(className = "form-control md:col-span-2") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsGmailPassword }
                    }
                    if (passwordChanging) {
                        text(
                            value = newPassword ?: "",
                            type = InputType.Password,
                            className = "input input-bordered w-full"
                        ) {
                            onInput {
                                newPassword = value ?: ""
                                testPassed = false
                                testResultMessage = null
                            }
                        }
                    } else {
                        div(className = "flex gap-2 items-center") {
                            span(className = "input input-bordered flex-1 font-mono text-base-content/60") {
                                +dto.gmailPasswordMasked
                            }
                            button(className = "btn btn-outline btn-sm") {
                                onClick {
                                    passwordChanging = true
                                    testPassed = false
                                    testResultMessage = null
                                }
                                +currentStrings.settingsChangeButton
                            }
                        }
                    }
                }
            }

            // Test result
            if (testResultMessage != null) {
                div(className = "mt-4 alert ${if (testResultIsError) "alert-error" else "alert-success"}") {
                    span(className = "icon-[heroicons--${if (testResultIsError) "x-circle" else "check-circle"}] size-5")
                    span { +testResultMessage!! }
                }
            }

            // Warning: test required
            if (credentialsChanged && !testPassed) {
                div(className = "mt-2 text-sm text-warning flex items-center gap-1") {
                    span(className = "icon-[heroicons--exclamation-triangle] size-4")
                    span { +currentStrings.settingsTestRequiredBeforeSave }
                }
            }

            // Actions
            div(className = "card-actions justify-end mt-4 gap-2") {
                button(className = "btn btn-outline${if (isTesting) " loading" else ""}") {
                    onClick {
                        if (!isTesting) {
                            scope.launch {
                                isTesting = true
                                testResultMessage = null
                                settingsService.testEmailSettings(
                                    senderEmail = senderEmail,
                                    appPassword = newPassword,
                                    displayName = senderDisplayName,
                                ).fold(
                                    ifLeft = { error ->
                                        testPassed = false
                                        testResultIsError = true
                                        testResultMessage = currentStrings.settingsTestFailed(error.localizedMessage(currentStrings))
                                    },
                                    ifRight = {
                                        testPassed = true
                                        testResultIsError = false
                                        testResultMessage = currentStrings.settingsTestPassed
                                    }
                                )
                                isTesting = false
                            }
                        }
                    }
                    +currentStrings.settingsTestEmailButton
                }

                button(className = "btn btn-primary${if (!saveEnabled) " btn-disabled" else ""}${if (isSaving) " loading" else ""}") {
                    onClick {
                        if (saveEnabled) {
                            scope.launch {
                                isSaving = true
                                settingsService.saveSettings(
                                    UpdateSettingsRequest(
                                        bankAccountNumber = dto.bankAccountNumber,
                                        fioToken = null,
                                        senderEmail = senderEmail,
                                        gmailAppPassword = newPassword,
                                        senderDisplayName = senderDisplayName,
                                    )
                                ).fold(
                                    ifLeft = { error ->
                                        onSaveError(error.localizedMessage(currentStrings))
                                    },
                                    ifRight = {
                                        onSaveSuccess()
                                    }
                                )
                                isSaving = false
                            }
                        }
                    }
                    +currentStrings.settingsSaveButton
                }
            }
        }
    }
}

@Composable
private fun IComponent.PaymentSettingsCard(
    dto: AppSettingsDisplayDto,
    currentStrings: AppStrings,
    onSaveSuccess: () -> Unit,
    onSaveError: (String) -> Unit,
    settingsService: AppSettingsServiceInterface,
) {
    val scope = rememberCoroutineScope()
    var bankAccount by remember { mutableStateOf(dto.bankAccountNumber) }
    var newFioToken by remember { mutableStateOf<String?>(null) }
    var fioTokenChanging by remember { mutableStateOf(false) }
    var testPassed by remember { mutableStateOf(false) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var testResultIsError by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val fioTokenChanged = fioTokenChanging && newFioToken != null
    val saveEnabled = !isSaving && !isTesting && (testPassed || !fioTokenChanged)

    div(className = "card bg-base-100 shadow-sm border border-base-200") {
        div(className = "card-body") {

            h2(className = "card-title text-xl font-bold mb-4") {
                span(className = "icon-[heroicons--banknotes] size-5")
                +currentStrings.settingsPaymentCard
            }

            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                // Bank account (always editable, no test required)
                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsBankAccount }
                    }
                    text(value = bankAccount, className = "input input-bordered w-full") {
                        onInput { bankAccount = value ?: "" }
                    }
                }

                // FIO token (sensitive field)
                div(className = "form-control") {
                    label(className = "label") {
                        span(className = "label-text font-medium") { +currentStrings.settingsFioToken }
                    }
                    if (fioTokenChanging) {
                        text(
                            value = newFioToken ?: "",
                            type = InputType.Password,
                            className = "input input-bordered w-full"
                        ) {
                            onInput {
                                newFioToken = value ?: ""
                                testPassed = false
                                testResultMessage = null
                            }
                        }
                    } else {
                        div(className = "flex gap-2 items-center") {
                            span(className = "input input-bordered flex-1 font-mono text-base-content/60") {
                                +dto.fioTokenMasked
                            }
                            button(className = "btn btn-outline btn-sm") {
                                onClick {
                                    fioTokenChanging = true
                                    testPassed = false
                                    testResultMessage = null
                                }
                                +currentStrings.settingsChangeButton
                            }
                        }
                    }
                }
            }

            // Test result
            if (testResultMessage != null) {
                div(className = "mt-4 alert ${if (testResultIsError) "alert-error" else "alert-success"}") {
                    span(className = "icon-[heroicons--${if (testResultIsError) "x-circle" else "check-circle"}] size-5")
                    span { +testResultMessage!! }
                }
            }

            // Warning: test required only when FIO token changed
            if (fioTokenChanged && !testPassed) {
                div(className = "mt-2 text-sm text-warning flex items-center gap-1") {
                    span(className = "icon-[heroicons--exclamation-triangle] size-4")
                    span { +currentStrings.settingsTestRequiredBeforeSave }
                }
            }

            // Actions
            div(className = "card-actions justify-end mt-4 gap-2") {
                button(className = "btn btn-outline${if (isTesting) " loading" else ""}") {
                    onClick {
                        if (!isTesting) {
                            scope.launch {
                                isTesting = true
                                testResultMessage = null
                                settingsService.testFioSettings(fioToken = newFioToken).fold(
                                    ifLeft = { error ->
                                        testPassed = false
                                        testResultIsError = true
                                        testResultMessage = currentStrings.settingsTestFailed(error.localizedMessage(currentStrings))
                                    },
                                    ifRight = {
                                        testPassed = true
                                        testResultIsError = false
                                        testResultMessage = currentStrings.settingsTestPassed
                                    }
                                )
                                isTesting = false
                            }
                        }
                    }
                    +currentStrings.settingsTestFioButton
                }

                button(className = "btn btn-primary${if (!saveEnabled) " btn-disabled" else ""}${if (isSaving) " loading" else ""}") {
                    onClick {
                        if (saveEnabled) {
                            scope.launch {
                                isSaving = true
                                settingsService.saveSettings(
                                    UpdateSettingsRequest(
                                        bankAccountNumber = bankAccount,
                                        fioToken = newFioToken,
                                        senderEmail = dto.senderEmail,
                                        gmailAppPassword = null,
                                        senderDisplayName = dto.senderDisplayName,
                                    )
                                ).fold(
                                    ifLeft = { error ->
                                        onSaveError(error.localizedMessage(currentStrings))
                                    },
                                    ifRight = {
                                        onSaveSuccess()
                                    }
                                )
                                isSaving = false
                            }
                        }
                    }
                    +currentStrings.settingsSaveButton
                }
            }
        }
    }
}
