package cz.svitaninymburk.projects.reservations.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed interface ReservationError : AppError {
    @Serializable @SerialName("create") sealed interface CreateReservation : ReservationError
    @Serializable @SerialName("cancel") sealed interface CancelReservation : ReservationError
    @Serializable @SerialName("get_all") sealed interface GetAll : ReservationError
    @Serializable @SerialName("get") sealed interface Get : ReservationError
    @Serializable @SerialName("get_detail") sealed interface GetDetail: ReservationError

    @Serializable data object ReservationNotFound : CreateReservation, CancelReservation, Get, GetDetail
    @Serializable data object EventInstanceNotFound : GetDetail
    @Serializable data object EventSeriesNotFound : GetDetail
    @Serializable data object EventAlreadyFinished : CreateReservation, CancelReservation
    @Serializable data object EventAlreadyStarted : CreateReservation, CancelReservation
    @Serializable data object EventCancelled : CreateReservation
    @Serializable data object CapacityExceeded : CreateReservation
    @Serializable data object FailedToGetAllReservations : GetAll
    @Serializable data class FailedToSendCancellationEmail(val cause: EmailError.SendCancellation) : CancelReservation
    @Serializable data class SystemError(val message: String) : CreateReservation
}

val ReservationError.localizedMessage: String get() = when (this) {
    is ReservationError.ReservationNotFound -> "Rezervace nebyla nalezena"
    is ReservationError.EventInstanceNotFound -> "Událost nebyla nalezena"
    is ReservationError.EventSeriesNotFound -> "Kroužek nebyl nalezen"
    is ReservationError.CapacityExceeded -> "Kapacita události překročena"
    is ReservationError.EventAlreadyFinished -> "Událost již skončila"
    is ReservationError.EventAlreadyStarted -> "Událost již začala"
    is ReservationError.EventCancelled -> "Událost byla zrušena"
    is ReservationError.FailedToGetAllReservations -> "Nelze získat seznam rezervací"
    is ReservationError.FailedToSendCancellationEmail -> "Nepodařilo se odeslat email o zrušení rezervace: ${cause.localizedMessage}"
    is ReservationError.SystemError -> message
}
