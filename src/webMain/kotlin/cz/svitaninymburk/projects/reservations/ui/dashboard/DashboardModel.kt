package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.DashboardQueries
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationSubmittingModel
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitUseCase
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val instances: List<EventInstance>,
        val series: List<EventSeries>,
        val definitions: List<EventDefinition>,
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}

class DashboardModel(
    scope: CoroutineScope,
    private val queries: DashboardQueries,
    submitter: ReservationSubmitter,
    router: Router,
) : ReservationSubmittingModel(scope, submitter, { router.navigate("/reservation/${it.id}") }) {

    var uiState by mutableStateOf<DashboardUiState>(DashboardUiState.Loading); private set

    /** Na rozcestníku ještě říkáme, že šlo o rezervaci — na náhledu akce je to zřejmé z kontextu. */
    override fun reservationErrorMessage(localized: String): String = currentStrings.reservationFailed(localized)

    fun load() {
        scope.launch {
            uiState = DashboardUiState.Loading
            try {
                queries.dashboardData()
                    .onRight { data -> uiState = DashboardUiState.Success(data.instances, data.series, data.definitions) }
                    .onLeft { error -> uiState = DashboardUiState.Error(error.localizedMessage(currentStrings)) }
            } catch (e: Exception) {
                uiState = DashboardUiState.Error(currentStrings.loadingError(e.message ?: "unknown"))
                e.printStackTrace()
            }
        }
    }
}

fun IComponent.buildDashboardModel(scope: CoroutineScope, router: Router): DashboardModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    val reservations = getService<ReservationServiceInterface>(RpcSerializersModules)
    return DashboardModel(
        scope = scope,
        queries = DashboardQueries(event),
        submitter = ReservationSubmitUseCase(reservations),
        router = router,
    )
}
