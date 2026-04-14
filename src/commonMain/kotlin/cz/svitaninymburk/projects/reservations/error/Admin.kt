package cz.svitaninymburk.projects.reservations.error

import cz.svitaninymburk.projects.reservations.i18n.ErrorStrings
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable @SerialName("admin") sealed interface AdminError : AppError {
    @Serializable @SerialName("summary") sealed interface GetSummary : AdminError
    @Serializable @SerialName("mark_paid") sealed interface MarkReservationPaid : AdminError
    @Serializable @SerialName("event_detail") sealed interface GetEventDetail : AdminError
    @Serializable @SerialName("reservations") sealed interface GetReservations : AdminError
    @Serializable @SerialName("events") sealed interface GetEvents : AdminError
    @Serializable @SerialName("create_event") sealed interface CreateEvent : AdminError
    @Serializable @SerialName("create_series") sealed interface CreateSeries : AdminError
    @Serializable @SerialName("get_users") sealed interface GetUsers : AdminError
    @Serializable @SerialName("update_user_role") sealed interface UpdateUserRole : AdminError
    @Serializable @SerialName("delete_user") sealed interface DeleteUser : AdminError

    @Serializable data class FailedToGetSummary(val message: String) : GetSummary
    @Serializable data class FailedToMarkReservationPaid(val message: String) : MarkReservationPaid
    @Serializable data class FailedToGetReservations(val message: String) : GetReservations
    @Serializable data class FailedToGetEvents(val message: String) : GetEvents
    @Serializable data class FailedToCreateEvent(val message: String) : CreateEvent
    @Serializable data class FailedToCreateSeries(val message: String) : CreateSeries
    @Serializable data class ReservationNotFound(val id: Uuid): MarkReservationPaid
    @Serializable data class WrongReservationState(val state: Reservation.Status): MarkReservationPaid
    @Serializable data class EventInstanceNotFound(val id: Uuid): GetEventDetail
    @Serializable data class EventSeriesNotFound(val id: Uuid): GetEventDetail
    @Serializable data class FailedToGetUsers(val message: String) : GetUsers
    @Serializable data class FailedToUpdateUserRole(val message: String) : UpdateUserRole
    @Serializable data class FailedToDeleteUser(val message: String) : DeleteUser
    @Serializable data class UserNotFound(val id: Uuid) : UpdateUserRole, DeleteUser
}

fun AdminError.localizedMessage(strings: ErrorStrings): String = when (this) {
    is AdminError.FailedToGetSummary -> message
    is AdminError.FailedToMarkReservationPaid -> message
    is AdminError.FailedToGetReservations -> message
    is AdminError.FailedToGetEvents -> message
    is AdminError.FailedToCreateEvent -> message
    is AdminError.FailedToCreateSeries -> message
    is AdminError.ReservationNotFound -> strings.errorAdminReservationNotFound
    is AdminError.WrongReservationState -> strings.errorWrongReservationState(state.name)
    is AdminError.EventInstanceNotFound -> strings.errorAdminEventNotFound
    is AdminError.EventSeriesNotFound -> strings.errorAdminCourseNotFound
    is AdminError.FailedToGetUsers -> message
    is AdminError.FailedToUpdateUserRole -> message
    is AdminError.FailedToDeleteUser -> message
    is AdminError.UserNotFound -> strings.errorAdminUserNotFound
}