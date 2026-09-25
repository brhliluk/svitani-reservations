package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.event.AddSeriesLessonRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.UpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/** Založení, úpravy, zveřejnění a mazání akcí se musí objevit v historii. */
class AdminManagementAuditSpec {

    private val defRepo = InMemoryEventDefinitionRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val auditLog = InMemoryAuditRepository()

    /** Co ještě existovalo v okamžiku, kdy se zapisoval záznam o smazání. */
    private val existedWhenRecorded = mutableMapOf<AuditEventType, Boolean>()

    /**
     * Zapíše záznam jako obvykle, ale předtím se podívá, jestli mazaná entita
     * ještě je v repozitáři — mazání ji musí zapsat dřív, než zmizí.
     */
    private val probingAudit = object : AuditRepository by auditLog {
        override suspend fun record(event: NewAuditEvent) {
            val exists = when (event.type) {
                AuditEventType.DEFINITION_DELETED -> defRepo.getAll(null).isNotEmpty()
                AuditEventType.SERIES_DELETED -> event.seriesId?.let { seriesRepo.get(it) } != null
                AuditEventType.EVENT_DELETED, AuditEventType.LESSON_DELETED ->
                    event.instanceId?.let { instanceRepo.get(it) } != null
                else -> null
            }
            exists?.let { existedWhenRecorded[event.type] = it }
            auditLog.record(event)
        }
    }

    private val service = AdminDashboardService(
        eventDefinitionRepository = defRepo,
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        refundService = RefundService(
            walletService = WalletService(InMemoryWalletRepository()),
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(
                    bankAccountNumber = "", fioToken = "", senderEmail = "",
                    gmailAppPassword = "", senderDisplayName = "",
                )
            ),
        ),
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        audit = AuditService(probingAudit),
        auditRepository = probingAudit,
    )

    private val admin = AuditService.Actor(AuditActorType.ADMIN, "admin@svitani.cz")

    /** Jako by volal přihlášený admin — aktéra si AuditService bere z kontextu. */
    private fun asAdmin(block: suspend () -> Unit) = runBlocking {
        withContext(AuditService.ActorContextLocal.asContextElement(admin)) { block() }
    }

    private fun recorded(type: AuditEventType): List<AuditEvent> = auditLog.recordedEvents().filter { it.type == type }

    private val definition = EventDefinition(
        id = Uuid.random(), title = "Šablona jógy", description = "",
        defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = 1.hours,
    )

    private val series = EventSeries(
        id = Uuid.random(), definitionId = definition.id, title = "Jóga podzim", description = "",
        price = 1000.0, capacity = 10,
        startDate = LocalDate(2099, 9, 1), endDate = LocalDate(2099, 12, 1), lessonCount = 1,
    )

    private val lesson = EventInstance(
        id = Uuid.random(), definitionId = definition.id, seriesId = series.id,
        title = "Jóga podzim", description = "",
        startDateTime = LocalDateTime(2099, 9, 1, 17, 0), endDateTime = LocalDateTime(2099, 9, 1, 18, 0),
        price = 100.0, capacity = 10,
    )

    private val event = lesson.copy(id = Uuid.random(), seriesId = null, title = "Jarmark")

    private suspend fun seed() {
        defRepo.create(definition)
        seriesRepo.create(series)
        instanceRepo.create(lesson)
        instanceRepo.create(event)
    }

    // --- Založení ---

    @Test
    fun `creating a template and a one-off event is recorded with the actor`() = asAdmin {
        service.createEventAndInstances(
            CreateEventAndInstancesRequest(
                title = "Jarmark", description = "", defaultPrice = 0.0, defaultCapacity = 50,
                defaultDuration = 2.hours, dateTimes = listOf(LocalDateTime(2099, 5, 1, 10, 0)),
            )
        )

        val definitionCreated = recorded(AuditEventType.DEFINITION_CREATED).single()
        assertEquals("Jarmark", definitionCreated.subjectLabel)
        assertEquals("admin@svitani.cz", definitionCreated.actorLabel)
        assertEquals(AuditActorType.ADMIN, definitionCreated.actorType)

        val eventCreated = recorded(AuditEventType.EVENT_CREATED).single()
        assertEquals(instanceRepo.getAll(null).single().id, eventCreated.instanceId)
        assertEquals(AuditCategory.MANAGEMENT, eventCreated.category)
    }

    @Test
    fun `creating a course and a bare template is recorded`() = asAdmin {
        service.createEventDefinition(
            CreateEventDefinitionRequest(
                title = "Prázdná šablona", description = "", defaultPrice = 0.0,
                defaultCapacity = 5, defaultDuration = 1.hours,
            )
        )
        service.createEventAndSeries(
            CreateEventAndSeriesRequest(
                title = "Kurz", description = "", defaultPrice = 0.0, defaultCapacity = 5,
                defaultDuration = 1.hours, startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 1, 1),
                lessonCount = 0,
            )
        )

        assertEquals(listOf("Prázdná šablona", "Kurz"), recorded(AuditEventType.DEFINITION_CREATED).map { it.subjectLabel })
        val seriesCreated = recorded(AuditEventType.SERIES_CREATED).single()
        assertEquals(seriesRepo.getAll(null).single().id, seriesCreated.seriesId)
    }

    @Test
    fun `adding a lesson is recorded against both the course and the lesson`() = asAdmin {
        seed()
        val lessonId = service.addSeriesLesson(
            AddSeriesLessonRequest(series.id, LocalDateTime(2099, 9, 8, 17, 0), LocalDateTime(2099, 9, 8, 18, 0))
        ).getOrNull()

        val created = recorded(AuditEventType.LESSON_CREATED).single()
        assertEquals(series.id, created.seriesId)
        assertEquals(lessonId, created.instanceId)
    }

    /** Nový termín ze šablony jde přes AuthenticatedEventService, ne přes admin službu. */
    @Test
    fun `new event instance from a template is recorded`() = asAdmin {
        seed()
        val authService = AuthenticatedEventService(defRepo, instanceRepo, AuditService(probingAudit))
        authService.createEventInstance(
            CreateEventInstanceRequest(definitionId = definition.id, startDateTime = LocalDateTime(2099, 6, 1, 10, 0))
        )

        val created = recorded(AuditEventType.EVENT_CREATED).single()
        assertEquals("Šablona jógy", created.subjectLabel)
        assertEquals("admin@svitani.cz", created.actorLabel)
        val newInstance = instanceRepo.getAll(null).single { it.startDateTime == LocalDateTime(2099, 6, 1, 10, 0) }
        assertEquals(newInstance.id, created.instanceId)
    }

    // --- Úpravy ---

    @Test
    fun `editing a template, course, lesson and event is recorded`() = asAdmin {
        seed()
        service.updateEventDefinition(
            definition.id,
            UpdateEventDefinitionRequest(
                title = "Nová šablona", description = "", defaultPrice = 100.0, defaultCapacity = 10,
                defaultDuration = 1.hours, allowedPaymentTypes = listOf(PaymentType.ON_SITE),
                customFields = emptyList(), propagateToChildren = false,
            )
        )
        service.updateEventSeries(
            series.id,
            UpdateEventSeriesRequest(
                title = "Jóga zima", description = "", price = 1000.0, capacity = 10,
                allowedPaymentTypes = listOf(PaymentType.ON_SITE), customFields = emptyList(),
            )
        )
        fun instanceUpdate(of: EventInstance) = UpdateEventInstanceRequest(
            title = of.title, description = "nový popis", startDateTime = of.startDateTime,
            endDateTime = of.endDateTime, price = of.price, capacity = of.capacity,
            allowedPaymentTypes = of.allowedPaymentTypes, customFields = emptyList(),
        )
        service.updateEventInstance(lesson.id, instanceUpdate(lesson))
        service.updateEventInstance(event.id, instanceUpdate(event))

        assertEquals("Nová šablona", recorded(AuditEventType.DEFINITION_UPDATED).single().subjectLabel)
        assertEquals(series.id, recorded(AuditEventType.SERIES_UPDATED).single().seriesId)
        val lessonUpdated = recorded(AuditEventType.LESSON_UPDATED).single()
        assertEquals(lesson.id, lessonUpdated.instanceId)
        assertEquals(series.id, lessonUpdated.seriesId)
        assertEquals(event.id, recorded(AuditEventType.EVENT_UPDATED).single().instanceId)
    }

    // --- Zveřejnění ---

    @Test
    fun `publishing and unpublishing are distinguished by state and event kind`() = asAdmin {
        seed()
        service.setSeriesPublished(series.id, true)
        service.setSeriesPublished(series.id, false)
        service.setInstancePublished(event.id, true)
        service.setInstancePublished(event.id, false)
        service.setInstancePublished(lesson.id, true)
        service.setInstancePublished(lesson.id, false)

        assertEquals(series.id, recorded(AuditEventType.SERIES_PUBLISHED).single().seriesId)
        assertEquals(series.id, recorded(AuditEventType.SERIES_UNPUBLISHED).single().seriesId)
        assertEquals(event.id, recorded(AuditEventType.EVENT_PUBLISHED).single().instanceId)
        assertEquals(event.id, recorded(AuditEventType.EVENT_UNPUBLISHED).single().instanceId)
        assertEquals(lesson.id, recorded(AuditEventType.LESSON_PUBLISHED).single().instanceId)
        assertEquals(lesson.id, recorded(AuditEventType.LESSON_UNPUBLISHED).single().instanceId)
    }

    // --- Mazání ---

    @Test
    fun `deleting an event is recorded before the deletion and carries its title`() = asAdmin {
        seed()
        service.deleteEventInstance(event.id)

        val deleted = recorded(AuditEventType.EVENT_DELETED).single()
        assertEquals(true, existedWhenRecorded[AuditEventType.EVENT_DELETED])
        assertNull(instanceRepo.get(event.id))
        assertEquals("Jarmark", deleted.subjectLabel)
        assertTrue(deleted.detail.orEmpty().contains("Jarmark"))
        assertEquals("admin@svitani.cz", deleted.actorLabel)
    }

    @Test
    fun `deleting a lesson is recorded as a course lesson`() = asAdmin {
        seed()
        service.deleteEventInstance(lesson.id)

        val deleted = recorded(AuditEventType.LESSON_DELETED).single()
        assertEquals(true, existedWhenRecorded[AuditEventType.LESSON_DELETED])
        assertEquals(series.id, deleted.seriesId)
        assertTrue(recorded(AuditEventType.EVENT_DELETED).isEmpty())
    }

    @Test
    fun `deleting a course is recorded before the deletion and carries its title`() = asAdmin {
        seed()
        service.deleteEventSeries(series.id)

        val deleted = recorded(AuditEventType.SERIES_DELETED).single()
        assertEquals(true, existedWhenRecorded[AuditEventType.SERIES_DELETED])
        assertNull(seriesRepo.get(series.id))
        assertEquals("Jóga podzim", deleted.subjectLabel)
        assertTrue(deleted.detail.orEmpty().contains("Jóga podzim"))
    }

    @Test
    fun `deleting a template records it and everything that disappeared with it`() = asAdmin {
        seed()
        service.deleteEventDefinition(definition.id)

        val deleted = recorded(AuditEventType.DEFINITION_DELETED).single()
        assertEquals(true, existedWhenRecorded[AuditEventType.DEFINITION_DELETED])
        assertEquals("Šablona jógy", deleted.subjectLabel)
        assertEquals(series.id, recorded(AuditEventType.SERIES_DELETED).single().seriesId)
        assertEquals(event.id, recorded(AuditEventType.EVENT_DELETED).single().instanceId)
        assertEquals(lesson.id, recorded(AuditEventType.LESSON_DELETED).single().instanceId)
    }
}
