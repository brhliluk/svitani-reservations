package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.admin.AdminDashboardData
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.admin.AdminUserListItem
import cz.svitaninymburk.projects.reservations.event.CreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid

@RpcService
interface AdminServiceInterface {
    suspend fun getDashboardSummary(): Either<AdminError.GetSummary, AdminDashboardData>
    suspend fun markReservationAsPaid(reservationId: Uuid): Either<AdminError.MarkReservationPaid, Unit>
    suspend fun getEventDetail(eventId: Uuid, isSeries: Boolean): Either<AdminError.GetEventDetail, AdminEventDetailData>
    suspend fun getAllReservations(searchQuery: String? = null): Either<AdminError.GetReservations, List<AdminReservationListItem>>
    suspend fun getAllEvents(): Either<AdminError.GetEvents, List<AdminEventListItem>>
    suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<AdminError.CreateEvent, Uuid>
    suspend fun createEventSeries(request: CreateEventSeriesRequest): Either<AdminError.CreateSeries, Uuid>
    suspend fun createEventAndInstances(request: CreateEventAndInstancesRequest): Either<AdminError.CreateEvent, Uuid>
    suspend fun createEventAndSeries(request: CreateEventAndSeriesRequest): Either<AdminError.CreateSeries, Uuid>
    suspend fun getAllUsers(): Either<AdminError.GetUsers, List<AdminUserListItem>>
    suspend fun updateUserRole(userId: Uuid, newRole: User.Role): Either<AdminError.UpdateUserRole, Unit>
    suspend fun deleteUser(userId: Uuid): Either<AdminError.DeleteUser, Unit>
    suspend fun getEventDefinitionForEdit(id: Uuid): Either<AdminError.GetEditData, EventDefinition>
    suspend fun getEventInstanceForEdit(id: Uuid): Either<AdminError.GetEditData, EventInstance>
    suspend fun getEventSeriesForEdit(id: Uuid): Either<AdminError.GetEditData, EventSeries>
    suspend fun updateEventDefinition(id: Uuid, request: UpdateEventDefinitionRequest): Either<AdminError.UpdateDefinition, Unit>
    suspend fun updateEventInstance(id: Uuid, request: UpdateEventInstanceRequest): Either<AdminError.UpdateEvent, Unit>
    suspend fun updateEventSeries(id: Uuid, request: UpdateEventSeriesRequest): Either<AdminError.UpdateSeries, Unit>
    suspend fun deleteEventDefinition(id: Uuid): Either<AdminError.DeleteDefinition, Unit>
    suspend fun deleteEventInstance(id: Uuid): Either<AdminError.DeleteEvent, Unit>
    suspend fun deleteEventSeries(id: Uuid): Either<AdminError.DeleteSeries, Unit>
    suspend fun getSeriesInstances(seriesId: Uuid): Either<AdminError.GetInstances, List<EventInstance>>
    suspend fun cancelSeriesLesson(instanceId: Uuid): Either<AdminError.CancelLesson, Unit>
}