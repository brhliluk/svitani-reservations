package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable @SerialName("admin") sealed interface AdminError : AppError {
    @Serializable @SerialName("get") sealed interface GetSummary : AdminError
    @Serializable @SerialName("mark_paid") sealed interface MarkReservationPaid : AdminError

    @Serializable data class FailedToGetSummary(val message: String) : GetSummary
    @Serializable data class FailedToMarkReservationPaid(val message: String) : MarkReservationPaid
    @Serializable data class ReservationNotFound(val id: Uuid): MarkReservationPaid
    @Serializable data class WrongReservationState(val state: Reservation.Status): MarkReservationPaid
}

val AdminError.localizedMessage: String get() = when (this) {
    is AdminError.FailedToGetSummary -> message
    is AdminError.FailedToMarkReservationPaid -> message
    is AdminError.ReservationNotFound -> "Rezervace nenalezena"
    is AdminError.WrongReservationState -> "Stav rezervace není k zaplacení, ale: ${state.name}"
}