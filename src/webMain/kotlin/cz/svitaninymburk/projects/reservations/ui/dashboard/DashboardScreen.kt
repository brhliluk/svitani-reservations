package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.prompts.alert

@Composable
fun IComponent.DashboardScreen(
    user: User?,
    initialFilterId: String? = null,
) {
    var retryTrigger by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }

    val router = Router.current
    val scope = rememberCoroutineScope()
    val eventService = getService<EventServiceInterface>()
    val reservationService = getService<ReservationServiceInterface>()

    val uiState by produceState<DashboardUiState>(initialValue = DashboardUiState.Loading, key1 = retryTrigger) {
        value = DashboardUiState.Loading

        eventService.getDashboardData().fold(
            ifRight = { data -> value = DashboardUiState.Success(data.instances, data.series, data.definitions) },
            ifLeft = { error -> value = DashboardUiState.Error(error.localizedMessage) }
        )
    }

    suspend fun submitReservation(target: ReservationTarget, formData: ReservationFormData) {
        isSubmitting = true

        val result = when (target) {
            is ReservationTarget.Instance -> reservationService.reserveInstance(
                request = formData.toCreateInstanceReservationRequest(target.id),
                userId = user?.id
            )
            is ReservationTarget.Series -> reservationService.reserveSeries(
                request = formData.toCreateSeriesReservationRequest(target.id),
                userId = user?.id
            )
        }

        result
            .onRight { newReservation ->
                isSubmitting = false
                router.navigate("/reservation/${newReservation.id}")
            }
            .onLeft { error ->
                isSubmitting = false
                alert("Rezervace se nezdařila: ${error.localizedMessage}")
            }
    }


    if (isSubmitting) {
        div(className = "fixed inset-0 bg-black/50 z-[100] flex items-center justify-center") {
            span(className = "loading loading-spinner loading-lg text-primary")
        }
    }

    when (val state = uiState) {
        is DashboardUiState.Loading -> Loading()
        is DashboardUiState.Success -> DashboardLayout(
            user = user,
            events = state.instances,
            series = state.series,
            definitions = state.definitions,
            initialFilterId = initialFilterId,
            onSubmitReservation = { target, formData -> scope.launch { submitReservation(target, formData) } }
        )
        is DashboardUiState.Error -> {
            div(className = "min-h-screen flex items-center justify-center bg-base-200") {
                div(className = "alert alert-error max-w-md") {
                    span(className = "icon-[heroicons--exclamation-circle] size-6")
                    span { +state.message }
                    button(className = "btn btn-sm") {
                        onClick { retryTrigger++ }
                        +"Zkusit znovu"
                    }
                }
            }
        }
    }
}

private sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val instances: List<EventInstance>, val series: List<EventSeries>, val definitions: List<EventDefinition>) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}