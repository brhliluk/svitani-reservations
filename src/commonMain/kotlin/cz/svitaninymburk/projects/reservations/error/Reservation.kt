package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable @SerialName("reservation") sealed interface ReservationError : AppError {
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

fun ReservationError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is ReservationError.ReservationNotFound -> strings.errorReservationNotFound
    is ReservationError.EventInstanceNotFound -> strings.errorEventInstanceNotFound
    is ReservationError.EventSeriesNotFound -> strings.errorEventSeriesNotFound
    is ReservationError.CapacityExceeded -> strings.errorCapacityExceeded
    is ReservationError.EventAlreadyFinished -> strings.errorEventAlreadyFinished
    is ReservationError.EventAlreadyStarted -> strings.errorEventAlreadyStarted
    is ReservationError.EventCancelled -> strings.errorEventCancelled
    is ReservationError.FailedToGetAllReservations -> strings.errorFailedToGetReservations
    is ReservationError.FailedToSendCancellationEmail -> strings.errorFailedToSendCancellationEmail(cause.localizedMessage)
    is ReservationError.SystemError -> message
}
