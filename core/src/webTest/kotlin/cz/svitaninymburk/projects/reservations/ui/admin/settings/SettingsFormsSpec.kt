package cz.svitaninymburk.projects.reservations.ui.admin.settings

import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stored(
    senderEmail: String = "info@svitani.cz",
    bankAccountNumber: String = "123456789/0800",
) = AppSettingsDisplayDto(
    bankAccountNumber = bankAccountNumber,
    fioTokenMasked = "•••abc",
    senderEmail = senderEmail,
    gmailPasswordMasked = "•••xyz",
    senderDisplayName = "Svítání",
    seasonResetMonth = 6,
    seasonResetDay = 30,
    walletResetWarningDays = 7,
)

class TestGateSpec {

    @Test
    fun freshGateHasNoResultAndNoPassedTest() {
        val gate = TestGate()
        assertFalse(gate.testPassed)
        assertNull(gate.result)
        assertFalse(gate.isTesting)
        assertFalse(gate.isSaving)
    }

    @Test
    fun passedTestUnblocksSavingChangedCredentials() {
        val gate = TestGate()
        assertFalse(gate.saveEnabled(credentialsChanged = true))
        gate.passed("OK")
        assertTrue(gate.saveEnabled(credentialsChanged = true))
        assertEquals(false, gate.result?.isError)
    }

    @Test
    fun failedTestKeepsSavingBlockedAndReportsAsError() {
        val gate = TestGate()
        gate.failed("SMTP odmítl heslo")
        assertFalse(gate.testPassed)
        assertEquals(true, gate.result?.isError)
        assertEquals("SMTP odmítl heslo", gate.result?.message)
        assertFalse(gate.saveEnabled(credentialsChanged = true))
    }

    @Test
    fun editingAfterAPassedTestBlocksSavingAgain() {
        val gate = TestGate()
        gate.passed("OK")
        gate.invalidate()
        assertFalse(gate.testPassed)
        assertNull(gate.result)
        assertFalse(gate.saveEnabled(credentialsChanged = true))
    }

    @Test
    fun startingATestClearsThePreviousResultButFinishingDoesNot() {
        val gate = TestGate()
        gate.failed("stará chyba")
        gate.testing(true)
        assertNull(gate.result)
        gate.passed("OK")
        gate.testing(false)
        assertEquals("OK", gate.result?.message)
    }
}

class EmailSettingsFormSpec {

    @Test
    fun untouchedFormNeedsNoTest() {
        val form = EmailSettingsForm(stored())
        assertFalse(form.credentialsChanged)
        assertFalse(form.needsTest)
        assertTrue(form.saveEnabled)
    }

    @Test
    fun changingSenderEmailDemandsATest() {
        val form = EmailSettingsForm(stored())
        form.setSenderEmail("jine@svitani.cz")
        assertTrue(form.credentialsChanged)
        assertTrue(form.needsTest)
        assertFalse(form.saveEnabled)
    }

    @Test
    fun typingBackTheStoredEmailIsNotAChange() {
        val form = EmailSettingsForm(stored(senderEmail = "info@svitani.cz"))
        form.setSenderEmail("jine@svitani.cz")
        form.setSenderEmail("info@svitani.cz")
        assertFalse(form.credentialsChanged)
        assertTrue(form.saveEnabled)
    }

    @Test
    fun unlockingThePasswordFieldAloneIsNotACredentialChange() {
        val form = EmailSettingsForm(stored())
        form.startPasswordChange()
        assertTrue(form.passwordChanging)
        // Odemčené pole ještě nic nezměnilo — teprve napsaná hodnota.
        assertFalse(form.passwordChanged)
        assertFalse(form.credentialsChanged)
        form.setPassword("nove-heslo")
        assertTrue(form.passwordChanged)
        assertTrue(form.needsTest)
    }

    @Test
    fun displayNameEditInvalidatesThePassedTest() {
        val form = EmailSettingsForm(stored())
        form.setSenderEmail("jine@svitani.cz")
        form.gate.passed("OK")
        assertTrue(form.saveEnabled)
        form.setDisplayName("Jiné jméno")
        assertFalse(form.gate.testPassed)
        assertFalse(form.saveEnabled)
    }

    @Test
    fun refreshedStoredValuesClearTheChangedFlagAfterSaving() {
        val form = EmailSettingsForm(stored(senderEmail = "info@svitani.cz"))
        form.setSenderEmail("nove@svitani.cz")
        assertTrue(form.credentialsChanged)
        // Po uložení se nastavení načte znovu a formulář dostane nový stav.
        form.stored = stored(senderEmail = "nove@svitani.cz")
        assertFalse(form.credentialsChanged)
        assertTrue(form.saveEnabled)
    }
}

class PaymentSettingsFormSpec {

    @Test
    fun bankAccountChangeSavesWithoutATest() {
        val form = PaymentSettingsForm(stored())
        form.setBankAccountNumber("987654321/2010")
        assertFalse(form.fioTokenChanged)
        assertFalse(form.needsTest)
        assertTrue(form.saveEnabled)
    }

    @Test
    fun bankAccountEditDoesNotInvalidateAPassedTokenTest() {
        val form = PaymentSettingsForm(stored())
        form.startFioTokenChange()
        form.setFioToken("fio-token")
        form.gate.passed("OK")
        form.setBankAccountNumber("987654321/2010")
        assertTrue(form.gate.testPassed)
        assertTrue(form.saveEnabled)
    }

    @Test
    fun newFioTokenDemandsATest() {
        val form = PaymentSettingsForm(stored())
        form.startFioTokenChange()
        form.setFioToken("fio-token")
        assertTrue(form.needsTest)
        assertFalse(form.saveEnabled)
        form.gate.passed("OK")
        assertTrue(form.saveEnabled)
    }

    @Test
    fun rewritingTheTokenAfterAPassedTestBlocksSavingAgain() {
        val form = PaymentSettingsForm(stored())
        form.startFioTokenChange()
        form.setFioToken("fio-token")
        form.gate.passed("OK")
        form.setFioToken("jiny-token")
        assertFalse(form.gate.testPassed)
        assertFalse(form.saveEnabled)
    }
}
