package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService


@Composable
fun IComponent.ReservationDetailScreen(
    reservationId: String,
    onBackClick: () -> Unit
) {
    val reservationService = getService<ReservationServiceInterface>()

    val uiState by produceState<ReservationLoadingUiState>(initialValue = ReservationLoadingUiState.Loading, key1 = reservationId) {
        reservationService.getDetail(reservationId).fold(
            ifRight = { foundReservation ->
                value = ReservationLoadingUiState.Success(foundReservation)
            },
            ifLeft = { error ->
                value = ReservationLoadingUiState.Error(error.localizedMessage)
            }
        )
    }

    when (val state = uiState) {
        is ReservationLoadingUiState.Loading -> {
            // --- LOADING ---
            div(className = "min-h-screen flex items-center justify-center bg-base-200") {
                div(className = "loading loading-spinner loading-lg text-primary")
            }
        }

        is ReservationLoadingUiState.Success -> {
            ReservationDetailLayout(
                reservation = state.detail.reservation,
                target = state.detail.target,
                "", // Placeholder
                bankAccountNumber = "123456789/0100",
                onCancelReservation = { onBackClick() }, // TODO: Implement cancel logic
                onBackToDashboard = onBackClick,
            )
        }

        is ReservationLoadingUiState.Error -> {
            // --- ERROR (Not Found) ---
            div(className = "min-h-screen flex items-center justify-center bg-base-200 p-4") {
                div(className = "card w-full max-w-md bg-base-100 shadow-xl") {
                    div(className = "card-body items-center text-center") {
                        // Ikona chyby
                        div(className = "rounded-full bg-error/10 p-4 mb-2") {
                            span(className = "icon-[heroicons--exclamation-triangle] size-12 text-error")
                        }

                        h3(className = "card-title text-error") { +"Chyba načítání" }
                        p(className = "text-base-content/70 py-4") {
                            +state.message
                        }

                        div(className = "card-actions") {
                            button(className = "btn btn-primary") {
                                onClick { onBackClick() }
                                +"Zpět na přehled"
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ReservationLoadingUiState {
    data object Loading : ReservationLoadingUiState
    data class Success(val detail: ReservationDetail) : ReservationLoadingUiState
    data class Error(val message: String) : ReservationLoadingUiState
}