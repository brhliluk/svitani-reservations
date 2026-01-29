package cz.svitaninymburk.projects.reservations.ui.reservation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.reservation.ReservationDetail
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.dialogRef
import dev.kilua.html.div
import dev.kilua.html.h3
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch


@Composable
fun IComponent.ReservationDetailScreen(
    reservationId: String,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }

    val reservationService = getService<ReservationServiceInterface>()

    val uiState by produceState<ReservationLoadingUiState>(initialValue = ReservationLoadingUiState.Loading, key1 = refreshTrigger, key2 = reservationId) {
        value = ReservationLoadingUiState.Loading

        reservationService.getDetail(reservationId).fold(
            ifRight = { foundReservation -> value = ReservationLoadingUiState.Success(foundReservation) },
            ifLeft = { error -> value = ReservationLoadingUiState.Error(error.localizedMessage) }
        )
    }

    val confirmDialog = dialogRef(className = "modal") {
        div(className = "modal-box") {
            h3(className = "font-bold text-lg text-error flex items-center gap-2") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                +"Zrušit rezervaci?"
            }

            p(className = "py-4") { +"Opravdu chcete zrušit tuto rezervaci? Tato akce je nevratná." }

            div(className = "modal-action") {
                button(className = "btn") {
                    onClick { this@dialogRef.element.close() }
                    +"Ponechat"
                }

                button(className = "btn btn-error text-white") {
                    onClick {
                        this@dialogRef.element.close()

                        scope.launch {
                            reservationService.cancelReservation(reservationId)
                                .onRight { refreshTrigger++ }
                                .onLeft { error -> web.prompts.alert("Chyba storna: ${error.localizedMessage}") }
                        }
                    }
                    +"Ano, zrušit rezervaci"
                }
            }
        }

        onClick { event -> if (event.target == this@dialogRef.element) this@dialogRef.element.close() }
    }

    when (val state = uiState) {
        is ReservationLoadingUiState.Loading -> Loading()
        is ReservationLoadingUiState.Success -> {
            ReservationDetailLayout(
                reservation = state.detail.reservation,
                target = state.detail.target,
                onCancelReservation = { confirmDialog.element.showModal() },
                onBackToDashboard = onBackClick,
            )
        }
        is ReservationLoadingUiState.Error -> {
            div(className = "min-h-screen flex items-center justify-center bg-base-200 p-4") {
                div(className = "card w-full max-w-md bg-base-100 shadow-xl") {
                    div(className = "card-body items-center text-center") {
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