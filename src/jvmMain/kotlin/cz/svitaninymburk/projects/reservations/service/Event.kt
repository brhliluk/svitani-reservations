package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.fx.coroutines.parZip
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.DashboardData
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid


class AuthenticatedEventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
): AuthenticatedEventServiceInterface {
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
        ensureNotNull(eventDefinitionRepository.get(definition.id)) { EventError.EventDefinitionNotFound(definition.id) }
        eventDefinitionRepository.update(definition)
    }

    override suspend fun deleteEventDefinition(id: String): Either<EventError.DeleteEventDefiniton, Boolean> = either {
        ensureNotNull(eventDefinitionRepository.get(id)) { EventError.EventDefinitionNotFound(id) }
        eventInstanceRepository.deleteAllByDefinitionId(id)
        eventDefinitionRepository.delete(id)
    }

    override suspend fun createEventInstance(request: CreateEventInstanceRequest): Either<EventError.CreateEventInstance, Unit> = either {
        val eventDefinition = ensureNotNull(eventDefinitionRepository.get(request.definitionId)) { EventError.EventDefinitionNotFound(request.definitionId) }

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

class EventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
): EventServiceInterface {
    override suspend fun getDashboardData(): Either<EventError.GetDashboardData, DashboardData> = either {
        parZip(
            { getAllInstances() },
            { getAllSeries() },
            { getAllDefinitions() }
        ) { instances, series, definitions ->
            val instances = instances.getOrElse { raise(EventError.FailedToGetInstances) }
            val series = series.getOrElse { raise(EventError.FailedToGetSeries) }
            val definitions = definitions.getOrElse { raise(EventError.FailedToGetDefinitions) }

            DashboardData(instances, series, definitions)
        }
    }

    override suspend fun getAllInstances(): Either<EventError.GetInstances, List<EventInstance>> = either {
        eventInstanceRepository.getAll(null)
    }

    override suspend fun getAllSeries(): Either<EventError.GetSeries, List<EventSeries>> = either {
        eventSeriesRepository.getAll(null)
    }

    override suspend fun getAllDefinitions(): Either<EventError.GetDefinitions, List<EventDefinition>> = either {
        eventDefinitionRepository.getAll(null)
    }
}