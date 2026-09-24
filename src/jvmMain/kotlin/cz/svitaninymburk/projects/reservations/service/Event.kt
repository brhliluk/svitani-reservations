package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.util.nowInAppTimeZone
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.fx.coroutines.parZip
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.error.EventError
import cz.svitaninymburk.projects.reservations.user.User
import cz.svitaninymburk.projects.reservations.util.currentCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.DashboardData
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid


/**
 * Zakládání a úpravy nad šablonou mimo [AdminDashboardService] — z UI sem vede
 * „nový termín ze šablony“. Do historie zapisuje stejné typy jako admin služba.
 */
class AuthenticatedEventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val seriesScheduleRefresher: SeriesScheduleRefresher,
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
): AuthenticatedEventServiceInterface {
    override suspend fun createEventDefinition(request: CreateEventDefinitionRequest): Either<EventError.CreateEventDefinition, Unit> = either {
        eventDefinitionRepository.create(
            EventDefinition(
                id = Uuid.random(),
                title = request.title,
                description = request.description,
                defaultPrice = request.defaultPrice,
                defaultCapacity = request.defaultCapacity,
                defaultDuration = request.defaultDuration,
                showAttendeeCount = request.showAttendeeCount,
                allowMultipleSeats = request.allowMultipleSeats,
            )
        )
        audit.record(type = AuditEventType.DEFINITION_CREATED, subjectLabel = request.title)
    }

    override suspend fun updateEventDefinition(definition: EventDefinition): Either<EventError.UpdateEventDefinition, Unit> = either {
        ensureNotNull(eventDefinitionRepository.get(definition.id)) { EventError.EventDefinitionNotFound(definition.id.toString()) }
        eventDefinitionRepository.update(definition)
        audit.record(type = AuditEventType.DEFINITION_UPDATED, subjectLabel = definition.title)
    }

    override suspend fun deleteEventDefinition(id: Uuid): Either<EventError.DeleteEventDefiniton, Boolean> = either {
        val definition = ensureNotNull(eventDefinitionRepository.get(id)) { EventError.EventDefinitionNotFound(id.toString()) }
        // Ještě před smazáním — pak už nebude z čeho vzít název.
        audit.record(
            type = AuditEventType.DEFINITION_DELETED,
            subjectLabel = definition.title,
            detail = "Smazáno: ${definition.title}",
        )
        eventInstanceRepository.deleteAllByDefinitionId(id)
        eventDefinitionRepository.delete(id)
    }

    override suspend fun createEventInstance(request: CreateEventInstanceRequest): Either<EventError.CreateEventInstance, Unit> = either {
        val eventDefinition = ensureNotNull(eventDefinitionRepository.get(request.definitionId)) { EventError.EventDefinitionNotFound(request.definitionId.toString()) }

        val instance = EventInstance(
            id = Uuid.random(),
            definitionId = eventDefinition.id,
            title = request.title ?: eventDefinition.title,
            description = request.description ?: eventDefinition.description,
            startDateTime = request.startDateTime,
            endDateTime =
                (request.startDateTime.toInstant(APP_TIMEZONE) + (request.duration
                    ?: eventDefinition.defaultDuration))
                    .toLocalDateTime(APP_TIMEZONE),
            price = request.price ?: eventDefinition.defaultPrice,
            capacity = request.capacity ?: eventDefinition.defaultCapacity,
            customFields = request.customFields.ifEmpty { eventDefinition.customFields },
            showAttendeeCount = request.showAttendeeCount,
            allowMultipleSeats = request.allowMultipleSeats,
            reservationDeadline = request.reservationDeadline,
            reservationDeadlineMessage = request.reservationDeadlineMessage,
            isPublished = request.isPublished,
        )
        eventInstanceRepository.create(instance)
        audit.record(
            type = AuditEventType.EVENT_CREATED,
            subjectLabel = instance.title,
            instanceId = instance.id,
            detail = "Termín ${instance.startDateTime}",
        )
    }

    override suspend fun updateEventInstance(instance: EventInstance): Either<EventError.UpdateEventInstance, Unit> = either {
        val existing = ensureNotNull(eventInstanceRepository.get(instance.id)) { EventError.EventInstanceNotFound(instance.id.toString()) }
        eventInstanceRepository.update(instance)
        existing.seriesId?.let { seriesScheduleRefresher.refresh(it) }
        audit.record(
            type = if (existing.seriesId != null) AuditEventType.LESSON_UPDATED else AuditEventType.EVENT_UPDATED,
            subjectLabel = instance.title,
            seriesId = existing.seriesId,
            instanceId = instance.id,
        )
    }

    override suspend fun deleteEventInstance(id: Uuid): Either<EventError.DeleteEventInstance, Boolean> = either {
        val existing = ensureNotNull(eventInstanceRepository.get(id)) { EventError.EventInstanceNotFound(id.toString()) }
        // Ještě před smazáním — pak už nebude z čeho vzít název.
        audit.record(
            type = if (existing.seriesId != null) AuditEventType.LESSON_DELETED else AuditEventType.EVENT_DELETED,
            subjectLabel = existing.title,
            seriesId = existing.seriesId,
            instanceId = id,
            detail = "Smazáno: ${existing.title} (${existing.startDateTime})",
        )
        val deleted = eventInstanceRepository.delete(id)
        existing.seriesId?.let { seriesScheduleRefresher.refresh(it) }
        deleted
    }
}

class EventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
): EventServiceInterface {
    private suspend fun isAdminCaller(): Boolean {
        val role = currentCall()?.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
        return role == User.Role.ADMIN.name
    }

    override suspend fun getDashboardData(): Either<EventError.GetDashboardData, DashboardData> = either {
        val now = nowInAppTimeZone()
        val today = now.date

        parZip(
            { eventInstanceRepository.getAll(null) },
            { getAllSeries() },
            { getAllDefinitions() }
        ) { allInstances, series, definitions ->
            val instances = allInstances
                .filter { it.isPublished }
                .filter { it.endDateTime > now && (it.seriesId == null || it.isDropIn) }
                .sortedBy { it.startDateTime }
            val series = series.getOrElse { raise(EventError.FailedToGetSeries) }
                .filter { it.endDate >= today }
                .sortedBy { it.startDate }
            val definitions = definitions.getOrElse { raise(EventError.FailedToGetDefinitions) }
                .filter { def -> instances.any { it.definitionId == def.id } || series.any { it.definitionId == def.id } }

            // Uzávěrku rezervace si prohlížeč spočítat neumí (nemá databázi
            // časových pásem), proto ji dostává hotovou; kurzy ji mají už z getAllSeries.
            DashboardData(instances.map { it.withReservationClosesAt() }, series, definitions)
        }
    }

    override suspend fun getAllInstances(): Either<EventError.GetInstances, List<EventInstance>> = either {
        eventInstanceRepository.getAllPublished().map { it.withReservationClosesAt() }
    }

    override suspend fun getAllSeries(): Either<EventError.GetSeries, List<EventSeries>> = either {
        eventSeriesRepository.getAllPublished().map { it.withReservationClosesAt() }
    }

    override suspend fun getAllDefinitions(): Either<EventError.GetDefinitions, List<EventDefinition>> = either {
        eventDefinitionRepository.getAll(null)
    }

    override suspend fun getInstance(id: Uuid): Either<EventError.GetInstance, EventInstance> = either {
        val instance = ensureNotNull(eventInstanceRepository.get(id)) { EventError.EventInstanceNotFound(id.toString()) }
        if (!isAdminCaller()) {
            ensure(instance.isPublished) { EventError.EventInstanceNotFound(id.toString()) }
        }
        instance.withReservationClosesAt()
    }

    override suspend fun getSeriesDetail(id: Uuid): Either<EventError.GetSeriesDetail, SeriesDetailResponse> = either {
        val series = ensureNotNull(eventSeriesRepository.get(id)) { EventError.EventSeriesNotFound(id.toString()) }
        if (!isAdminCaller()) {
            ensure(series.isPublished) { EventError.EventSeriesNotFound(id.toString()) }
        }
        val lessons = eventInstanceRepository.findBySeries(id).sortedBy { it.startDateTime }
        SeriesDetailResponse(series.withReservationClosesAt(), lessons.map { it.withReservationClosesAt() })
    }
}