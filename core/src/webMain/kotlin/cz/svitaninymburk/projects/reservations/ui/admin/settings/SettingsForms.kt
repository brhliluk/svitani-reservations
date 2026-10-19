package cz.svitaninymburk.projects.reservations.ui.admin.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.settings.AppSettingsDisplayDto
import cz.svitaninymburk.projects.reservations.ui.admin.settings.usecase.isSaveEnabled

data class SettingsTestResult(val message: String, val isError: Boolean)

/**
 * Stav "otestuj, než uložíš", který měly obě karty nastavení rozepsaný zvlášť.
 *
 * Klíčové je [invalidate]: jakákoli editace zneplatní výsledek testu, aby se
 * nedalo protlačit uložení údajů, které prošly testem v jiné podobě —
 * otestovat heslo a pak ho přepsat je přesně ta cesta, kterou to hlídá.
 */
class TestGate {
    var isTesting by mutableStateOf(false); private set
    var isSaving by mutableStateOf(false); private set
    var testPassed by mutableStateOf(false); private set
    var result: SettingsTestResult? by mutableStateOf(null); private set

    fun invalidate() {
        testPassed = false
        result = null
    }

    /** Rozjetý test zahodí předchozí výsledek, aby v alertu nesvítil starý. */
    fun testing(value: Boolean) {
        isTesting = value
        if (value) result = null
    }

    fun saving(value: Boolean) { isSaving = value }

    fun passed(message: String) {
        testPassed = true
        result = SettingsTestResult(message, isError = false)
    }

    fun failed(message: String) {
        testPassed = false
        result = SettingsTestResult(message, isError = true)
    }

    fun saveEnabled(credentialsChanged: Boolean): Boolean =
        isSaveEnabled(isSaving, isTesting, credentialsChanged, testPassed)
}

/**
 * Formuláře drží [stored] jako proměnnou, ne jako zmrazenou kopii: po úspěšném
 * uložení se nastavení načte znovu a "změnil jsem e-mail" musí zhasnout, jinak
 * by karta chtěla nový test po každém dalším uložení.
 */
class EmailSettingsForm(stored: AppSettingsDisplayDto) {
    var stored by mutableStateOf(stored)

    var senderDisplayName by mutableStateOf(stored.senderDisplayName); private set
    var senderEmail by mutableStateOf(stored.senderEmail); private set
    var newPassword: String? by mutableStateOf(null); private set
    var passwordChanging by mutableStateOf(false); private set

    val gate = TestGate()

    val passwordChanged: Boolean get() = passwordChanging && newPassword != null
    val credentialsChanged: Boolean get() = senderEmail != stored.senderEmail || passwordChanged
    val saveEnabled: Boolean get() = gate.saveEnabled(credentialsChanged)
    val needsTest: Boolean get() = credentialsChanged && !gate.testPassed

    fun setDisplayName(value: String) { senderDisplayName = value; gate.invalidate() }
    fun setSenderEmail(value: String) { senderEmail = value; gate.invalidate() }
    fun setPassword(value: String) { newPassword = value; gate.invalidate() }
    fun startPasswordChange() { passwordChanging = true; gate.invalidate() }
}

class PaymentSettingsForm(stored: AppSettingsDisplayDto) {
    var stored by mutableStateOf(stored)

    var bankAccountNumber by mutableStateOf(stored.bankAccountNumber); private set
    var newFioToken: String? by mutableStateOf(null); private set
    var fioTokenChanging by mutableStateOf(false); private set

    val gate = TestGate()

    val fioTokenChanged: Boolean get() = fioTokenChanging && newFioToken != null
    val saveEnabled: Boolean get() = gate.saveEnabled(fioTokenChanged)
    val needsTest: Boolean get() = fioTokenChanged && !gate.testPassed

    /** Číslo účtu se nikam nepřihlašuje, takže se netestuje a test nezneplatňuje. */
    fun setBankAccountNumber(value: String) { bankAccountNumber = value }

    fun setFioToken(value: String) { newFioToken = value; gate.invalidate() }
    fun startFioTokenChange() { fioTokenChanging = true; gate.invalidate() }
}
