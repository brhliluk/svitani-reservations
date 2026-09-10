package cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase

import cz.svitaninymburk.projects.reservations.service.AppSettingsServiceInterface
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.settings.UpdateSettingsRequest

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Citlivé údaje se nesmí uložit, dokud neprošly testem — jinak by si admin mohl
 * zamknout odesílání e-mailů nebo párování platb špatným heslem a zjistil by to
 * až tím, že přestane fungovat. Co se nezměnilo, testovat netřeba.
 */
fun isSaveEnabled(
    isSaving: Boolean,
    isTesting: Boolean,
    credentialsChanged: Boolean,
    testPassed: Boolean,
): Boolean = !isSaving && !isTesting && (testPassed || !credentialsChanged)

/**
 * Každá karta ukládá jen svoje pole a zbytek posílá tak, jak je uložený —
 * `saveSettings` bere celý request, takže neposlat pole znamená ho přepsat.
 *
 * POZOR: `seasonResetMonth`, `seasonResetDay` ani `walletResetWarningDays` se
 * nepředávají, takže jim [UpdateSettingsRequest] nasadí defaulty (6 / 30 / 7) a
 * uložení nastavení je tím přepíše. Tohle chování má obrazovka odjakživa a
 * refaktoring ho zachovává — viz `settingsRequestDiscardsSeasonSettings`.
 */
fun emailSettingsRequest(
    stored: AppSettingsDisplayDto,
    senderEmail: String,
    gmailAppPassword: String?,
    senderDisplayName: String,
): UpdateSettingsRequest = UpdateSettingsRequest(
    bankAccountNumber = stored.bankAccountNumber,
    fioToken = null,
    senderEmail = senderEmail,
    gmailAppPassword = gmailAppPassword,
    senderDisplayName = senderDisplayName,
)

fun paymentSettingsRequest(
    stored: AppSettingsDisplayDto,
    bankAccountNumber: String,
    fioToken: String?,
): UpdateSettingsRequest = UpdateSettingsRequest(
    bankAccountNumber = bankAccountNumber,
    fioToken = fioToken,
    senderEmail = stored.senderEmail,
    gmailAppPassword = null,
    senderDisplayName = stored.senderDisplayName,
)

// --- UseCase třídy (tenké, vrací Either) ---

class AdminSettingsQueries(private val settings: AppSettingsServiceInterface) {
    suspend fun settings() = settings.getSettings()
}

class AdminSettingsMutations(private val settings: AppSettingsServiceInterface) {
    suspend fun testEmail(senderEmail: String, appPassword: String?, displayName: String) =
        settings.testEmailSettings(senderEmail, appPassword, displayName)

    suspend fun testFio(fioToken: String?) = settings.testFioSettings(fioToken)

    suspend fun save(request: UpdateSettingsRequest) = settings.saveSettings(request)
}
