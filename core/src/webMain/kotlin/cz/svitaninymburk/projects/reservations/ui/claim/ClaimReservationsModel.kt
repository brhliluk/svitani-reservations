package cz.svitaninymburk.projects.reservations.ui.claim

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.AppStrings
import cz.svitaninymburk.projects.reservations.reservation.ClaimResult
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope

sealed interface ClaimConfirmState {
    data object Loading : ClaimConfirmState
    data class Done(val result: ClaimResult) : ClaimConfirmState
    data class Failed(val message: String) : ClaimConfirmState
}

/**
 * Hláška k výsledku. Oddělená od UI, ať jde otestovat bez prohlížeče.
 *
 * Nula připsaných není chyba: buď odkaz někdo otevřel podruhé, nebo rezervacím mezitím
 * doběhla akce — v obou případech uživatel nemá co zachraňovat.
 */
fun claimResultMessage(result: ClaimResult, strings: AppStrings): String = when {
    result.alreadyDone -> strings.claimConfirmAlreadyDone
    result.claimed == 0 -> strings.claimConfirmNothingAdded
    result.skipped > 0 -> strings.claimConfirmPartial(result.claimed, result.skipped)
    else -> strings.claimConfirmSuccess(result.claimed)
}

class ClaimReservationsModel(
    scope: CoroutineScope,
    private val token: String,
    private val service: ReservationServiceInterface,
) : ScreenModel(scope) {

    var state: ClaimConfirmState by mutableStateOf(ClaimConfirmState.Loading); private set

    private var started = false

    /** Uplatní odkaz. Druhé zavolání (překreslení obrazovky) se ignoruje. */
    fun confirm() {
        if (started) return
        started = true
        run(
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { service.confirmReservationClaim(token) },
            onSuccess = { state = ClaimConfirmState.Done(it) },
            onError = { error ->
                state = ClaimConfirmState.Failed(error.localizedMessage(currentStrings))
                true // chybu ukazuje obrazovka, toast by ji jen zdvojil
            },
        )
    }
}

fun IComponent.buildClaimReservationsModel(scope: CoroutineScope, token: String) =
    ClaimReservationsModel(scope, token, getService<ReservationServiceInterface>(RpcSerializersModules))
