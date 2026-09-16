package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.service.AuthServiceInterface
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
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
    private val reservations: AuthenticatedReservationServiceInterface,
) : ScreenModel(scope) {

    var currentUser by mutableStateOf<User?>(null); private set
    var walletCode by mutableStateOf<String?>(null); private set

    /**
     * Dokud nevíme, jestli je někdo přihlášený, nemá smysl nic vykreslovat —
     * jinak by adminovi problikla uživatelská verze stránky.
     */
    var isLoaded by mutableStateOf(false); private set

    val isAdmin: Boolean get() = currentUser?.role == User.Role.ADMIN

    /**
     * Rezervace bez účtu vedené na e-mail přihlášeného. Server vrací jen počet —
     * dokud uživatel neprokáže, že mu adresa patří, nemá co vidět cizí termíny.
     */
    var claimableCount by mutableStateOf(0); private set
    var isSendingClaimLink by mutableStateOf(false); private set
    var claimOfferDismissed by mutableStateOf(false); private set

    val showsClaimOffer: Boolean get() = claimableCount > 0 && !claimOfferDismissed

    /**
     * Volá se **jen** po přihlášení a po registraci, ne z [refresh] — ta běží při každém
     * načtení stránky a nabídka by vyskakovala pořád dokola.
     *
     * Chyba se nezobrazuje: je to akce na pozadí, kterou si uživatel nevyžádal, a červená
     * hláška hned po přihlášení by jen mátla.
     */
    fun checkClaimable() {
        scope.launch {
            try {
                reservations.countClaimableReservations()
                    .onRight { count ->
                        claimableCount = count
                        claimOfferDismissed = false
                    }
                    .onLeft { console.log(it.localizedMessage(currentStrings)) }
            } catch (e: Exception) {
                console.log(e.message ?: "countClaimableReservations failed")
            }
        }
    }

    /** Nechá si poslat potvrzovací odkaz. Nabídka se pak zavře — dál se pokračuje z mailu. */
    fun sendClaimLink() {
        if (isSendingClaimLink) return
        run(
            loading = { isSendingClaimLink = it },
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { reservations.requestReservationClaim() },
            onSuccess = {
                claimOfferDismissed = true
                showToast(currentStrings.claimOfferEmailSent)
            },
        )
    }

    fun dismissClaimOffer() {
        claimOfferDismissed = true
    }

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
        claimableCount = 0
    }
}

fun IComponent.buildSessionModel(scope: CoroutineScope): SessionModel =
    SessionModel(
        scope,
        getService<AuthServiceInterface>(RpcSerializersModules),
        getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules),
    )
