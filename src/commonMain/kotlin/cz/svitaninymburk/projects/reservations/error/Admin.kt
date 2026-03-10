package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable @SerialName("admin") sealed interface AdminError : AppError {
    @Serializable @SerialName("summary") sealed interface GetSummary : AdminError
    @Serializable @SerialName("mark_paid") sealed interface MarkReservationPaid : AdminError
    @Serializable @SerialName("event_detail") sealed interface GetEventDetail : AdminError
    @Serializable @SerialName("reservations") sealed interface GetReservations : AdminError

    @Serializable data class FailedToGetSummary(val message: String) : GetSummary
    @Serializable data class FailedToMarkReservationPaid(val message: String) : MarkReservationPaid
    @Serializable data class FailedToGetReservations(val message: String) : GetReservations
    @Serializable data class ReservationNotFound(val id: Uuid): MarkReservationPaid
    @Serializable data class WrongReservationState(val state: Reservation.Status): MarkReservationPaid
    @Serializable data class EventInstanceNotFound(val id: Uuid): GetEventDetail
    @Serializable data class EventSeriesNotFound(val id: Uuid): GetEventDetail
}

val AdminError.localizedMessage: String get() = when (this) {
    is AdminError.FailedToGetSummary -> message
    is AdminError.FailedToMarkReservationPaid -> message
    is AdminError.FailedToGetReservations -> message
    is AdminError.ReservationNotFound -> "Rezervace nenalezena"
    is AdminError.WrongReservationState -> "Stav rezervace není k zaplacení, ale: ${state.name}"
    is AdminError.EventInstanceNotFound -> "Událost nenalezena"
    is AdminError.EventSeriesNotFound -> "Kroužek nenalezen"
}