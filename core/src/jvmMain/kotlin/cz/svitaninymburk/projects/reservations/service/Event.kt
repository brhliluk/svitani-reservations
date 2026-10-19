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
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.DashboardData
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid


/**
 * Nový termín ze šablony. Úpravy a mazání jdou jen přes [AdminDashboardService] —
 * tady by obešly storno rezervací, oznámení i vracení kreditu.
 */
class AuthenticatedEventService(
    private val eventDefinitionRepository: EventDefinitionRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val audit: AuditService = AuditService(InMemoryAuditRepository()),
): AuthenticatedEventServiceInterface {
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
            waitlistCapacity = request.waitlistCapacity,
            allowedPaymentTypes = request.allowedPaymentTypes.ifEmpty { eventDefinition.allowedPaymentTypes },
            customFields = request.customFields.ifEmpty { eventDefinition.customFields },
            ownerEmails = parseOwnerEmails(request.ownerEmails),
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