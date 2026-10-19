package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonsView
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.MyReservationsQueries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.SeriesLessonsQueries
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface MyReservationsUiState {
    data object Loading : MyReservationsUiState
    data class Success(val items: List<MyReservationListItem>) : MyReservationsUiState
    data class Error(val message: String) : MyReservationsUiState
}

class MyReservationsModel(
    scope: CoroutineScope,
    private val queries: MyReservationsQueries,
    private val userId: Uuid,
) : ScreenModel(scope) {

    var uiState: MyReservationsUiState by mutableStateOf(MyReservationsUiState.Loading); private set

    fun load() {
        uiState = MyReservationsUiState.Loading
        scope.launch {
            try {
                queries.reservations(userId)
                    .onRight { uiState = MyReservationsUiState.Success(it) }
                    .onLeft { uiState = MyReservationsUiState.Error(it.localizedMessage(currentStrings)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState = MyReservationsUiState.Error(currentStrings.loadingError(e.message ?: "unknown"))
            }
        }
    }

    /** Tlačítko "Zkusit znovu" v chybovém stavu. */
    fun retry() = load()
}

sealed interface LessonsLoadState {
    data object Idle : LessonsLoadState
    data object Loading : LessonsLoadState
    data class Success(val view: SeriesLessonsView) : LessonsLoadState
    data class Error(val message: String) : LessonsLoadState
}

/**
 * Stav jedné karty rezervace: rozbalení a k němu načtené termíny kurzu.
 *
 * Termíny se tahají teprve při rozbalení — karet je na obrazovce tolik, kolik má
 * uživatel rezervací, a načítat lekce všem předem by znamenalo dotaz na každou.
 */
class ReservationCardModel(
    scope: CoroutineScope,
    private val queries: SeriesLessonsQueries,
    private val reservationId: Uuid,
    private val isSeries: Boolean,
) : ScreenModel(scope) {

    var isExpanded by mutableStateOf(false); private set
    var lessonsState: LessonsLoadState by mutableStateOf(LessonsLoadState.Idle); private set

    fun toggleExpanded() {
        isExpanded = !isExpanded
        // Sbalená karta si poslední odpověď drží; rozbalení proto přepne na
        // Loading rovnou, aby na okamžik neprosvitly staré termíny.
        if (isExpanded) loadLessons()
    }

    /** Po omluvence z lekce se termíny načtou znovu, aby se odraz projevil hned. */
    fun reloadLessons() {
        if (isExpanded) loadLessons()
    }

    private fun loadLessons() {
        if (!isSeries) return
        lessonsState = LessonsLoadState.Loading
        scope.launch {
            queries.lessons(reservationId)
                .onRight { lessonsState = LessonsLoadState.Success(it) }
                .onLeft { lessonsState = LessonsLoadState.Error(it.localizedMessage(currentStrings)) }
        }
    }
}

fun IComponent.buildMyReservationsModel(scope: CoroutineScope, userId: Uuid): MyReservationsModel {
    val authenticated = getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules)
    return MyReservationsModel(
        scope = scope,
        queries = MyReservationsQueries(authenticated),
        userId = userId,
    )
}

fun IComponent.buildReservationCardModel(
    scope: CoroutineScope,
    reservationId: Uuid,
    isSeries: Boolean,
): ReservationCardModel {
    val reservations = getService<ReservationServiceInterface>(RpcSerializersModules)
    return ReservationCardModel(
        scope = scope,
        queries = SeriesLessonsQueries(reservations),
        reservationId = reservationId,
        isSeries = isSeries,
    )
}
