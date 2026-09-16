package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.ReservationDetailMutations
import cz.svitaninymburk.projects.reservations.ui.reservation.detail.usecase.ReservationDetailQueries
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface ReservationDetailUiState {
    data object Loading : ReservationDetailUiState

    /** [lessons] je null u jednorázových akcí i tam, kde na termíny kurzu není nárok. */
    data class Success(
        val detail: ReservationDetail,
        val lessons: SeriesLessonsView?,
    ) : ReservationDetailUiState

    data class Error(val message: String) : ReservationDetailUiState
}

class ReservationDetailModel(
    scope: CoroutineScope,
    private val reservationId: Uuid,
    private val queries: ReservationDetailQueries,
    private val mutations: ReservationDetailMutations,
) : ScreenModel(scope) {

    var uiState: ReservationDetailUiState by mutableStateOf(ReservationDetailUiState.Loading); private set

    // Stav potvrzovacího dialogu pro zrušení CELÉ rezervace.
    var walletCode by mutableStateOf(""); private set
    var showEmailMismatchWarning by mutableStateOf(false); private set
    var isCancelling by mutableStateOf(false); private set
    var cancelErrorMessage: String? by mutableStateOf(null); private set

    var isClaiming by mutableStateOf(false); private set

    fun load() {
        scope.launch {
            queries.detail(reservationId)
                .onRight { detail ->
                    uiState = ReservationDetailUiState.Success(detail, loadLessonsOrNull(detail))
                }
                .onLeft { error ->
                    uiState = ReservationDetailUiState.Error(error.localizedMessage(currentStrings))
                }
        }
    }

    /** Znovu načte jen termíny kurzu — po omluvence není důvod překreslovat celou stránku. */
    fun reloadLessons() {
        val current = uiState as? ReservationDetailUiState.Success ?: return
        scope.launch {
            uiState = current.copy(lessons = loadLessonsOrNull(current.detail))
        }
    }

    /**
     * getDetail bránu nemá, getSeriesLessons ano — kdo se dívá na cizí registrovanou
     * rezervaci, uvidí souhrn jako dosud a jen mu chybí sekce lekcí. Chyba lekcí proto
     * nesmí shodit celou stránku.
     */
    private suspend fun loadLessonsOrNull(detail: ReservationDetail): SeriesLessonsView? {
        if (detail.reservation.reference !is Reference.Series) return null
        return queries.seriesLessons(reservationId).getOrNull()
    }

    /**
     * Připíše rezervaci k účtu. Po úspěchu se detail načte znovu — server pak vrátí
     * `claimable = false` a sekce termínů kurzu se poprvé načte jako vlastníkovi.
     */
    fun claimReservation() {
        if (isClaiming) return
        run(
            loading = { isClaiming = it },
            errorMessage = { it.localizedMessage(currentStrings) },
            block = { mutations.claim(reservationId) },
            onSuccess = {
                showToast(currentStrings.claimReservationSuccess, ToastType.Success)
                load()
            },
        )
    }

    fun setWalletCode(value: String) {
        walletCode = value
    }

    fun resetCancelDialog() {
        walletCode = ""
        showEmailMismatchWarning = false
        cancelErrorMessage = null
    }

    /**
     * Neshoda e-mailu u peněženky není chyba k zobrazení, ale dotaz — server v tom
     * případě nic nezrušil, takže se dá potvrdit znovu s [force].
     */
    fun cancelWholeReservation(force: Boolean, onDone: () -> Unit) {
        if (isCancelling) return
        isCancelling = true
        cancelErrorMessage = null
        scope.launch {
            try {
                mutations.cancelWhole(reservationId, walletCode.ifBlank { null }, force)
                    .onRight { result ->
                        resetCancelDialog()
                        val credit = result.walletCreditAmount
                        val code = result.walletCode
                        if (credit != null && credit > 0.0 && code != null) {
                            showToast("${currentStrings.walletCreditIssued}: $code", ToastType.Success)
                        }
                        onDone()
                        load()
                    }
                    .onLeft { error ->
                        if (error is ReservationError.WalletEmailMismatch) {
                            showEmailMismatchWarning = true
                        } else {
                            cancelErrorMessage = error.localizedMessage(currentStrings)
                        }
                    }
            } finally {
                isCancelling = false
            }
        }
    }
}

fun IComponent.buildReservationDetailModel(scope: CoroutineScope, reservationId: Uuid): ReservationDetailModel {
    val service = getService<ReservationServiceInterface>(RpcSerializersModules)
    val authenticated = getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules)
    return ReservationDetailModel(
        scope = scope,
        reservationId = reservationId,
        queries = ReservationDetailQueries(service),
        mutations = ReservationDetailMutations(service, authenticated),
    )
}
