package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import dev.kilua.rpc.annotations.RpcService


@RpcService
interface EventServiceInterface {
    suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<EventError.CreateEventDefinition, Unit>
    suspend fun updateEventDefinition(definition: EventDefinition): Either<EventError.UpdateEventDefinition, Unit>
    suspend fun deleteEventDefinition(id: String): Either<EventError.DeleteEventDefiniton, Boolean>
    suspend fun createEventInstance(request: CreateEventInstanceRequest): Either<EventError.CreateEventInstance, Unit>
    suspend fun updateEventInstance(instance: EventInstance): Either<EventError.UpdateEventInstance, Unit>
    suspend fun deleteEventInstance(id: String): Either<EventError.DeleteEventInstance, Boolean>
}