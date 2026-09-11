package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import web.console.console

/**
 * Kdo je přihlášený — jediný stav, který přežívá přepínání obrazovek, a proto
 * sedí nad routerem. Drží i hlášky, které vyskakují mimo konkrétní obrazovku
 * (přihlášení, odhlášení, změna hesla).
 */
class SessionModel(
    scope: CoroutineScope,
    private val auth: AuthServiceInterface,
) : ScreenModel(scope) {

    var currentUser by mutableStateOf<User?>(null); private set
    var walletCode by mutableStateOf<String?>(null); private set

    /**
     * Dokud nevíme, jestli je někdo přihlášený, nemá smysl nic vykreslovat —
     * jinak by adminovi problikla uživatelská verze stránky.
     */
    var isLoaded by mutableStateOf(false); private set

    val isAdmin: Boolean get() = currentUser?.role == User.Role.ADMIN

    fun refresh() {
        scope.launch {
            try {
                auth.getCurrentUser()
                    .onRight { user ->
                        currentUser = user
                        auth.getMyWalletCode().onRight { code -> walletCode = code }
                    }
                    .onLeft { error ->
                        // Nepřihlášený uživatel není chyba, kterou by měl vidět.
                        console.log(error.localizedMessage(currentStrings))
                        clear()
                    }
            } catch (e: Exception) {
                console.log(e.message ?: "getCurrentUser failed")
                clear()
            } finally {
                isLoaded = true
            }
        }
    }

    fun logout() {
        scope.launch {
            auth.logout()
            clear()
        }
    }

    /** Hlášky z hlavičky a přihlašovacích dialogů, které nepatří žádné obrazovce. */
    fun showMessage(message: String, type: ToastType = ToastType.Success) = showToast(message, type)

    private fun clear() {
        currentUser = null
        walletCode = null
    }
}

fun IComponent.buildSessionModel(scope: CoroutineScope): SessionModel =
    SessionModel(scope, getService<AuthServiceInterface>(RpcSerializersModules))
