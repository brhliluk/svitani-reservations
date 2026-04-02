package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.admin.AdminDashboardData
import cz.svitaninymburk.projects.reservations.admin.AdminEventDetailData
import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.AdminReservationListItem
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
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
}