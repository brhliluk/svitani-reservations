package cz.svitaninymburk.projects.reservations.error


sealed interface ReservationError : AppError {
    sealed interface CreateReservation : ReservationError
    sealed interface CancelReservation : ReservationError
    sealed interface GetAll : ReservationError

    data object NotFound : CreateReservation, CancelReservation
    data object EventAlreadyFinished : CreateReservation, CancelReservation
    data object EventAlreadyStarted : CreateReservation, CancelReservation
    data object EventCancelled : CreateReservation
    data object CapacityExceeded : CreateReservation
    data object FailedToGetAllReservations : GetAll
    data class FailedToSendCancellationEmail(val cause: EmailError.SendCancellation) : CancelReservation
}

val ReservationError.localizedMessage: String get() = when (this) {
    is ReservationError.NotFound -> "Událost nebyla nalezena"
    is ReservationError.CapacityExceeded -> "Kapacita události překročena"
    is ReservationError.EventAlreadyFinished -> "Událost již skončila"
    is ReservationError.EventAlreadyStarted -> "Událost již začala"
    is ReservationError.EventCancelled -> "Událost byla zrušena"
    is ReservationError.FailedToGetAllReservations -> "Nelze získat seznam rezervací"
    is ReservationError.FailedToSendCancellationEmail -> "Nepodařilo se odeslat email o zrušení rezervace: ${cause.localizedMessage}"
}
