package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import kotlinx.coroutines.CoroutineScope

/**
 * Obal administrace — sám nic nenačítá, drží jen dialog na změnu hesla
 * a hlášku po něm. Samotná změna hesla má vlastní model v `ui/auth`.
 */
class AdminLayoutModel(scope: CoroutineScope) : ScreenModel(scope) {

    var showChangePassword by mutableStateOf(false); private set

    fun openChangePassword() { showChangePassword = true }

    fun closeChangePassword() { showChangePassword = false }

    fun onPasswordChanged() {
        showChangePassword = false
        showToast(currentStrings.passwordChanged)
    }
}
