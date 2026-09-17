package cz.svitaninymburk.projects.reservations.ui.admin.settings

import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.emailSettingsRequest
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.isSaveEnabled
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.paymentSettingsRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val STORED = AppSettingsDisplayDto(
    bankAccountNumber = "123456789/0800",
    fioTokenMasked = "•••abc",
    senderEmail = "info@svitani.cz",
    gmailPasswordMasked = "•••xyz",
    senderDisplayName = "Svítání",
    seasonResetMonth = 8,
    seasonResetDay = 15,
    walletResetWarningDays = 21,
)

class AdminSettingsUseCasesSpec {

    // --- isSaveEnabled ---

    @Test
    fun unchangedCredentialsSaveWithoutATest() {
        assertTrue(isSaveEnabled(isSaving = false, isTesting = false, credentialsChanged = false, testPassed = false))
    }

    @Test
    fun changedCredentialsNeedAPassedTest() {
        assertFalse(isSaveEnabled(isSaving = false, isTesting = false, credentialsChanged = true, testPassed = false))
        assertTrue(isSaveEnabled(isSaving = false, isTesting = false, credentialsChanged = true, testPassed = true))
    }

    @Test
    fun runningOperationBlocksSavingEvenWhenTheTestPassed() {
        assertFalse(isSaveEnabled(isSaving = true, isTesting = false, credentialsChanged = false, testPassed = true))
        assertFalse(isSaveEnabled(isSaving = false, isTesting = true, credentialsChanged = false, testPassed = true))
    }

    // --- Sestavení requestů: každá karta ukládá jen svoje pole ---

    @Test
    fun emailRequestKeepsPaymentFieldsAsStored() {
        val request = emailSettingsRequest(
            stored = STORED,
            senderEmail = "nove@svitani.cz",
            gmailAppPassword = "tajne",
            senderDisplayName = "Svítání Nymburk",
        )
        assertEquals("nove@svitani.cz", request.senderEmail)
        assertEquals("tajne", request.gmailAppPassword)
        assertEquals("Svítání Nymburk", request.senderDisplayName)
        assertEquals(STORED.bankAccountNumber, request.bankAccountNumber)
        // null = ponech uložený token, ne "vymaž ho"
        assertNull(request.fioToken)
    }

    @Test
    fun paymentRequestKeepsEmailFieldsAsStored() {
        val request = paymentSettingsRequest(
            stored = STORED,
            bankAccountNumber = "987654321/2010",
            fioToken = "fio-token",
        )
        assertEquals("987654321/2010", request.bankAccountNumber)
        assertEquals("fio-token", request.fioToken)
        assertEquals(STORED.senderEmail, request.senderEmail)
        assertEquals(STORED.senderDisplayName, request.senderDisplayName)
        assertNull(request.gmailAppPassword)
    }

    /**
     * Sezónní pole needituje žádná karta, ale `saveSettings` bere celý request —
     * dokud je obrazovka neposílala, každé uložení nastavení je přepsalo na
     * defaulty 6/30/7 a s nimi se posunula expirace peněženek. V UI se to nijak
     * neprojevilo, protože tahle pole obrazovka vůbec nezobrazuje.
     */
    @Test
    fun settingsRequestKeepsSeasonSettings() {
        val fromEmailCard = emailSettingsRequest(STORED, STORED.senderEmail, null, STORED.senderDisplayName)
        val fromPaymentCard = paymentSettingsRequest(STORED, STORED.bankAccountNumber, null)

        listOf(fromEmailCard, fromPaymentCard).forEach { request ->
            assertEquals(STORED.seasonResetMonth, request.seasonResetMonth)
            assertEquals(STORED.seasonResetDay, request.seasonResetDay)
            assertEquals(STORED.walletResetWarningDays, request.walletResetWarningDays)
        }
        // Uložené hodnoty se schválně liší od defaultů requestu (6/30/7).
        assertEquals(8, STORED.seasonResetMonth)
        assertEquals(15, STORED.seasonResetDay)
        assertEquals(21, STORED.walletResetWarningDays)
    }
}
