package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid


class EventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
): EventServiceInterface {
    override suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<EventError.CreateEventDefinition, Unit> = either {
        eventDefinitionRepository.create(
            EventDefinition(
                id = Uuid.random().toString(),
                title = request.title,
                description = request.description,
                defaultPrice = request.defaultPrice,
                defaultCapacity = request.defaultCapacity,
                defaultDuration = request.defaultDuration,
                recurrenceType = request.recurrenceType,
                recurrenceEndDate = request.recurrenceEndDate,
            )
        )
    }

    override suspend fun updateEventDefinition(definition: EventDefinition): Either<EventError.UpdateEventDefinition, Unit> = either {
        ensureNotNull(eventDefinitionRepository.findById(definition.id)) { EventError.EventDefinitionNotFound(definition.id) }
        eventDefinitionRepository.update(definition)
    }

    override suspend fun deleteEventDefinition(id: String): Either<EventError.DeleteEventDefiniton, Boolean> = either {
        ensureNotNull(eventDefinitionRepository.findById(id)) { EventError.EventDefinitionNotFound(id) }
        eventInstanceRepository.deleteAllByDefinitionId(id)
        eventDefinitionRepository.delete(id)
    }

    override suspend fun createEventInstance(request: CreateEventInstanceRequest): Either<EventError.CreateEventInstance, Unit> = either {
        val eventDefinition = ensureNotNull(eventDefinitionRepository.findById(request.definitionId)) { EventError.EventDefinitionNotFound(request.definitionId) }

        eventInstanceRepository.create(
            EventInstance(
                id = Uuid.random().toString(),
                definitionId = eventDefinition.id,
                title = request.title ?: eventDefinition.title,
                description = request.description ?: eventDefinition.description,
                startDateTime = request.startDateTime,
                endDateTime =
                    (request.startDateTime.toInstant(TimeZone.currentSystemDefault()) + (request.duration
                        ?: eventDefinition.defaultDuration))
                        .toLocalDateTime(TimeZone.currentSystemDefault()),
                price = request.price ?: eventDefinition.defaultPrice,
                capacity = request.capacity ?: eventDefinition.defaultCapacity,
            )
        )
    }

    override suspend fun updateEventInstance(instance: EventInstance): Either<EventError.UpdateEventInstance, Unit> = either {
        ensureNotNull(eventInstanceRepository.get(instance.id)) { EventError.EventInstanceNotFound(instance.id) }
        eventInstanceRepository.update(instance)
    }

    override suspend fun deleteEventInstance(id: String): Either<EventError.DeleteEventInstance, Boolean> = either {
        ensureNotNull(eventInstanceRepository.get(id)) { EventError.EventInstanceNotFound(id) }
        eventInstanceRepository.delete(id)
    }
}
