package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AuthenticatedReservationServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.span
import dev.kilua.rpc.getService
import kotlin.uuid.Uuid

@Composable
fun IComponent.MyReservationsList(userId: Uuid) {
    val currentStrings by strings
    val router = Router.current
    val reservationService = getService<AuthenticatedReservationServiceInterface>(RpcSerializersModules)

    var retryTrigger by remember { mutableStateOf(0) }

    val uiState by produceState<MyReservationsUiState>(
        initialValue = MyReservationsUiState.Loading,
        key1 = retryTrigger,
        key2 = userId,
    ) {
        value = MyReservationsUiState.Loading
        try {
            reservationService.getReservations(userId)
                .onRight { items -> value = MyReservationsUiState.Success(items) }
                .onLeft { error -> value = MyReservationsUiState.Error(error.localizedMessage(currentStrings)) }
        } catch (e: Exception) {
            value = MyReservationsUiState.Error(currentStrings.loadingError(e.message ?: "unknown"))
        }
    }

    when (val state = uiState) {
        is MyReservationsUiState.Loading -> Loading()
        is MyReservationsUiState.Error -> {
            div(className = "alert alert-error") {
                span(className = "icon-[heroicons--exclamation-circle] size-6")
                span { +state.message }
                button(className = "btn btn-sm min-h-11") {
                    onClick { retryTrigger++ }
                    +currentStrings.close
                }
            }
        }
        is MyReservationsUiState.Success -> {
            if (state.items.isEmpty()) {
                div(className = "alert bg-base-100 shadow-sm animate-fade-in") {
                    span(className = "icon-[heroicons--information-circle] size-6 text-info")
                    +currentStrings.noUpcomingReservations
                }
            } else {
                div(className = "flex flex-col gap-3 animate-fade-in") {
                    state.items.forEach { item ->
                        ReservationCard(item, onCardClick = { router.navigate("/reservation/${item.id}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.ReservationCard(item: MyReservationListItem, onCardClick: () -> Unit) {
    val currentStrings by strings
    val isPaid = item.status == Reservation.Status.CONFIRMED
    val isOnSite = item.paymentType == PaymentInfo.Type.ON_SITE

    div(className = "card bg-base-100 shadow-sm border border-base-200 cursor-pointer hover:shadow-md transition-shadow") {
        onClick { onCardClick() }
        div(className = "card-body p-4 sm:p-5 gap-3") {
            div(className = "flex flex-col sm:flex-row justify-between gap-2 sm:items-start") {
                div(className = "flex flex-col gap-1 min-w-0") {
                    div(className = "font-bold text-base sm:text-lg text-base-content truncate") { +item.eventTitle }
                    div(className = "flex items-center gap-2 text-sm text-base-content/60") {
                        span(className = "icon-[heroicons--calendar] size-4 shrink-0")
                        span { +item.startDateTime.humanReadable }
                    }
                }
                div(className = "flex flex-col items-start sm:items-end gap-1 shrink-0") {
                    when {
                        isPaid -> div(className = "badge badge-success gap-1") {
                            span(className = "icon-[heroicons--check] size-3")
                            +currentStrings.paid
                        }
                        isOnSite -> div(className = "badge badge-info badge-outline gap-1") {
                            span(className = "icon-[heroicons--banknotes] size-3")
                            +currentStrings.statusOnSiteBadge
                        }
                        else -> div(className = "badge badge-warning gap-1") {
                            span(className = "icon-[heroicons--clock] size-3")
                            +currentStrings.statusWaiting
                        }
                    }
                }
            }

            div(className = "flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-sm") {
                div(className = "flex items-center gap-1 text-base-content/70") {
                    span(className = "icon-[heroicons--user-group] size-4")
                    +"${item.seatCount} ${currentStrings.persons}"
                }
                div(className = "flex items-center gap-3") {
                    span(className = "text-base-content/60") {
                        if (isOnSite) +currentStrings.paymentMethodCash else +currentStrings.bankTransfer
                    }
                    span(className = "font-bold text-primary") {
                        +"${item.totalPrice} ${currentStrings.currency}"
                    }
                }
            }
        }
    }
}

private sealed interface MyReservationsUiState {
    data object Loading : MyReservationsUiState
    data class Success(val items: List<MyReservationListItem>) : MyReservationsUiState
    data class Error(val message: String) : MyReservationsUiState
}
