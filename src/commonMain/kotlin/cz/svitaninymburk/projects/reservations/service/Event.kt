package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.DashboardData
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import dev.kilua.rpc.annotations.RpcService
import kotlin.uuid.Uuid


@RpcService
interface EventServiceInterface {
    suspend fun getDashboardData(): Either<EventError.GetDashboardData, DashboardData>
    suspend fun getAllInstances(): Either<EventError.GetInstances, List<EventInstance>>
    suspend fun getAllSeries(): Either<EventError.GetSeries, List<EventSeries>>
    suspend fun getAllDefinitions(): Either<EventError.GetDefinitions, List<EventDefinition>>
}

@RpcService
interface AuthenticatedEventServiceInterface {
    suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<EventError.CreateEventDefinition, Unit>
    suspend fun updateEventDefinition(definition: EventDefinition): Either<EventError.UpdateEventDefinition, Unit>
    suspend fun deleteEventDefinition(id: Uuid): Either<EventError.DeleteEventDefiniton, Boolean>
    suspend fun createEventInstance(request: CreateEventInstanceRequest): Either<EventError.CreateEventInstance, Unit>
    suspend fun updateEventInstance(instance: EventInstance): Either<EventError.UpdateEventInstance, Unit>
    suspend fun deleteEventInstance(id: Uuid): Either<EventError.DeleteEventInstance, Boolean>
}