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
    @Serializable @SerialName("get_edit_data") sealed interface GetEditData : AdminError
    @Serializable @SerialName("update_definition") sealed interface UpdateDefinition : AdminError
    @Serializable @SerialName("update_event") sealed interface UpdateEvent : AdminError
    @Serializable @SerialName("update_series") sealed interface UpdateSeries : AdminError
    @Serializable @SerialName("delete_definition") sealed interface DeleteDefinition : AdminError
    @Serializable @SerialName("delete_event") sealed interface DeleteEvent : AdminError
    @Serializable @SerialName("delete_series") sealed interface DeleteSeries : AdminError

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
    @Serializable data class DefinitionNotFound(val id: Uuid) : GetEditData, UpdateDefinition, DeleteDefinition
    @Serializable data class InstanceNotFoundForEdit(val id: Uuid) : GetEditData, UpdateEvent, DeleteEvent
    @Serializable data class SeriesNotFoundForEdit(val id: Uuid) : GetEditData, UpdateSeries, DeleteSeries
    @Serializable data class FailedToUpdateDefinition(val message: String) : UpdateDefinition
    @Serializable data class FailedToUpdateEvent(val message: String) : UpdateEvent
    @Serializable data class FailedToUpdateSeries(val message: String) : UpdateSeries
    @Serializable data class FailedToDeleteDefinition(val message: String) : DeleteDefinition
    @Serializable data class FailedToDeleteEvent(val message: String) : DeleteEvent
    @Serializable data class FailedToDeleteSeries(val message: String) : DeleteSeries

    @Serializable @SerialName("get_instances") sealed interface GetInstances : AdminError {
        @Serializable @SerialName("failed") object Failed : GetInstances
    }

    @Serializable @SerialName("cancel_lesson") sealed interface CancelLesson : AdminError {
        @Serializable @SerialName("instance_not_found") object InstanceNotFound : CancelLesson
        @Serializable @SerialName("failed") object Failed : CancelLesson
    }
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
    is AdminError.DefinitionNotFound -> strings.errorAdminDefinitionNotFound
    is AdminError.InstanceNotFoundForEdit -> strings.errorAdminEventNotFound
    is AdminError.SeriesNotFoundForEdit -> strings.errorAdminCourseNotFound
    is AdminError.FailedToUpdateDefinition -> message
    is AdminError.FailedToUpdateEvent -> message
    is AdminError.FailedToUpdateSeries -> message
    is AdminError.FailedToDeleteDefinition -> message
    is AdminError.FailedToDeleteEvent -> message
    is AdminError.FailedToDeleteSeries -> message
    is AdminError.GetInstances.Failed -> strings.errorAdminGetInstancesFailed
    is AdminError.CancelLesson.InstanceNotFound -> strings.errorAdminCancelLessonInstanceNotFound
    is AdminError.CancelLesson.Failed -> strings.errorAdminCancelLessonFailed
}