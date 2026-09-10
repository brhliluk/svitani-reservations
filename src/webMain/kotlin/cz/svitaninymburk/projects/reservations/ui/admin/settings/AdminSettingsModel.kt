package cz.svitaninymburk.projects.reservations.ui.admin.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.SettingsError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.AdminSettingsMutations
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.AdminSettingsQueries
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.emailSettingsRequest
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.paymentSettingsRequest
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface AdminSettingsUiState {
    data object Loading : AdminSettingsUiState
    data class Success(val data: AppSettingsDisplayDto) : AdminSettingsUiState
    data class Error(val message: String) : AdminSettingsUiState
}

class AdminSettingsModel(
    scope: CoroutineScope,
    private val queries: AdminSettingsQueries,
    private val mutations: AdminSettingsMutations,
) : ScreenModel(scope) {

    var uiState: AdminSettingsUiState by mutableStateOf(AdminSettingsUiState.Loading); private set

    /** Formuláře vzniknou s prvním načtením a další načtení jim jen osvěží [EmailSettingsForm.stored] — rozepsaná editace se nezahazuje. */
    var email: EmailSettingsForm? by mutableStateOf(null); private set
    var payment: PaymentSettingsForm? by mutableStateOf(null); private set

    fun load() {
        uiState = AdminSettingsUiState.Loading
        scope.launch {
            queries.settings()
                .onRight { dto ->
                    uiState = AdminSettingsUiState.Success(dto)
                    email?.let { it.stored = dto } ?: run { email = EmailSettingsForm(dto) }
                    payment?.let { it.stored = dto } ?: run { payment = PaymentSettingsForm(dto) }
                }
                .onLeft { uiState = AdminSettingsUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    // --- E-mail ---

    fun testEmail() {
        val form = email ?: return
        if (form.gate.isTesting) return
        runTest(form.gate) {
            mutations.testEmail(
                senderEmail = form.senderEmail,
                appPassword = form.newPassword,
                displayName = form.senderDisplayName,
            )
        }
    }

    fun saveEmail() {
        val form = email ?: return
        if (!form.saveEnabled) return
        val request = emailSettingsRequest(
            stored = form.stored,
            senderEmail = form.senderEmail,
            gmailAppPassword = form.newPassword,
            senderDisplayName = form.senderDisplayName,
        )
        run(
            loading = { form.gate.saving(it) },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.save(request) },
            onSuccess = { showToast(currentStrings.settingsSavedSuccess); load() },
        )
    }

    // --- Platby ---

    fun testFio() {
        val form = payment ?: return
        if (form.gate.isTesting) return
        runTest(form.gate) { mutations.testFio(form.newFioToken) }
    }

    fun savePayment() {
        val form = payment ?: return
        if (!form.saveEnabled) return
        val request = paymentSettingsRequest(
            stored = form.stored,
            bankAccountNumber = form.bankAccountNumber,
            fioToken = form.newFioToken,
        )
        run(
            loading = { form.gate.saving(it) },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.save(request) },
            onSuccess = { showToast(currentStrings.settingsSavedSuccess); load() },
        )
    }

    /**
     * Selhání testu patří do alertu v kartě, ne do toastu — admin si u něj musí
     * přečíst, co přesně SMTP nebo FIO vrátilo. Proto to nejde přes [run].
     */
    private fun runTest(gate: TestGate, block: suspend () -> Either<SettingsError, Unit>) {
        gate.testing(true)
        scope.launch {
            try {
                block()
                    .onRight { gate.passed(currentStrings.settingsTestPassed) }
                    .onLeft { gate.failed(currentStrings.settingsTestFailed(it.localizedMessage(currentStrings))) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                gate.failed(currentStrings.settingsTestFailed(e.message ?: "Error"))
            } finally {
                gate.testing(false)
            }
        }
    }
}

fun IComponent.buildAdminSettingsModel(scope: CoroutineScope): AdminSettingsModel {
    val settings = getService<AppSettingsServiceInterface>(RpcSerializersModules)
    return AdminSettingsModel(
        scope = scope,
        queries = AdminSettingsQueries(settings),
        mutations = AdminSettingsMutations(settings),
    )
}
