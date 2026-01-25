package cz.svitaninymburk.projects.reservations.ui.reservation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import cz.svitaninymburk.projects.reservations.debug.mockReservations
import cz.svitaninymburk.projects.reservations.debug.randomEventList
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import dev.kilua.core.IComponent
import dev.kilua.html.div

@Composable
fun IComponent.ReservationSuccessLoader(
    reservationId: String,
//    reservationService: ReservationServiceInterface,
    onBackClick: () -> Unit
) {
    val reservationState = produceState<Reservation?>(initialValue = null, key1 = reservationId) {
        // TODO:
//        val value = reservationService.getReservation(reservationId)

        // SIMULACE API:
        kotlinx.coroutines.delay(500) // loading
        value = mockReservations.random()
    }

    val reservation = reservationState.value

    if (reservation != null) {
        // Máme data -> Zobrazíme finální obrazovku
        ReservationSuccessScreen(
            reservation = reservation,
            target = ReservationTarget.Instance(randomEventList.first()), // Placeholder
            "",
            bankAccountNumber = "123456789/0100", onCancelReservation = { onBackClick() },
            onBackToDashboard = onBackClick,
        )
    } else {
        // Nemáme data -> Zobrazíme Loading
        div(className = "min-h-screen flex items-center justify-center") {
            div(className = "loading loading-spinner loading-lg text-primary")
        }
    }
}