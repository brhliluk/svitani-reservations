package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.UpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter

/**
 * Admin nastavuje "povolit rezervaci více míst" na šabloně, kurzu i jednorázové akci —
 * hodnota musí projít vytvořením, editací i propagací ze šablony na potomky.
 */
class AdminAllowMultipleSeatsSpec {

    private fun makeService(
        defRepo: InMemoryEventDefinitionRepository = InMemoryEventDefinitionRepository(),
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
    ): AdminDashboardService {
        val walletService = WalletService(InMemoryWalletRepository())
        val settings = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        )
        return AdminDashboardService(
            eventDefinitionRepository = defRepo,
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = InMemoryReservationRepository(),
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = InMemoryPaymentEventRepository(),
            walletService = walletService,
            refundService = RefundService(
                walletService = walletService,
                walletEmailService = ConsoleEmailService(),
                appSettingsProvider = settings,
            ),
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, InMemoryReservationRepository()),
        )
    }

    private fun definition(allowMultipleSeats: Boolean = true) = EventDefinition(
        id = Uuid.random(),
        title = "Šablona",
        description = "desc",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = 1.hours,
        allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
        customFields = emptyList(),
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun instance(definitionId: Uuid, allowMultipleSeats: Boolean = true) = EventInstance(
        id = Uuid.random(),
        definitionId = definitionId,
        title = "Akce",
        description = "desc",
        startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun series(definitionId: Uuid, allowMultipleSeats: Boolean = true) = EventSeries(
        id = Uuid.random(),
        definitionId = definitionId,
        title = "Kurz",
        description = "desc",
        price = 100.0,
        capacity = 10,
        startDate = LocalDate(2099, 6, 1),
        endDate = LocalDate(2099, 7, 1),
        lessonCount = 4,
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun updateDefinitionRequest(allowMultipleSeats: Boolean, propagate: Boolean) =
        UpdateEventDefinitionRequest(
            title = "Šablona", description = "desc",
            defaultPrice = 100.0, defaultCapacity = 10,
            defaultDuration = 1.hours,
            allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
            customFields = emptyList(),
            propagateToChildren = propagate,
            allowMultipleSeats = allowMultipleSeats,
        )

    @Test
    fun `createEventInstance persists allowMultipleSeats`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val def = definition()
        defRepo.create(def)
        val service = AuthenticatedEventService(
            eventDefinitionRepository = defRepo,
            eventInstanceRepository = instanceRepo,
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        )

        val result = service.createEventInstance(
            CreateEventInstanceRequest(
                definitionId = def.id,
                startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
                title = "Akce", description = "desc",
                price = 100.0, capacity = 10,
                allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
                customFields = emptyList(),
                allowMultipleSeats = false,
            )
        )
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, instanceRepo.getAll(null).single().allowMultipleSeats)
    }

    @Test
    fun `createEventSeries persists allowMultipleSeats`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val def = definition()
        defRepo.create(def)

        val result = makeService(defRepo = defRepo, seriesRepo = seriesRepo).createEventSeries(
            CreateEventSeriesRequest(
                definitionId = def.id,
                title = "Kurz", description = "desc",
                price = 100.0, capacity = 10,
                startDate = LocalDate(2099, 6, 1), endDate = LocalDate(2099, 7, 1),
                lessonCount = 4,
                allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
                customFields = emptyList(),
                allowMultipleSeats = false,
            )
        )
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, seriesRepo.getAll(null).single().allowMultipleSeats)
    }

    @Test
    fun `updateEventInstance persists allowMultipleSeats`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = definition()
        defRepo.create(def)
        val event = instance(def.id, allowMultipleSeats = true)
        instanceRepo.create(event)

        val result = makeService(defRepo = defRepo, instanceRepo = instanceRepo).updateEventInstance(
            event.id,
            UpdateEventInstanceRequest(
                title = "Akce", description = "desc",
                startDateTime = event.startDateTime, endDateTime = event.endDateTime,
                price = 100.0, capacity = 10,
                allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
                customFields = emptyList(),
                allowMultipleSeats = false,
            )
        )
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, instanceRepo.get(event.id)?.allowMultipleSeats)
    }

    @Test
    fun `updateEventSeries persists allowMultipleSeats`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val def = definition()
        defRepo.create(def)
        val course = series(def.id, allowMultipleSeats = true)
        seriesRepo.create(course)

        val result = makeService(defRepo = defRepo, seriesRepo = seriesRepo).updateEventSeries(
            course.id,
            UpdateEventSeriesRequest(
                title = "Kurz", description = "desc",
                price = 100.0, capacity = 10,
                allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
                customFields = emptyList(),
                allowMultipleSeats = false,
            )
        )
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, seriesRepo.get(course.id)?.allowMultipleSeats)
    }

    @Test
    fun `updateEventDefinition propagates allowMultipleSeats to children when requested`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val def = definition(allowMultipleSeats = true)
        defRepo.create(def)
        val event = instance(def.id, allowMultipleSeats = true)
        instanceRepo.create(event)
        val course = series(def.id, allowMultipleSeats = true)
        seriesRepo.create(course)

        val result = makeService(defRepo = defRepo, instanceRepo = instanceRepo, seriesRepo = seriesRepo)
            .updateEventDefinition(def.id, updateDefinitionRequest(allowMultipleSeats = false, propagate = true))
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, defRepo.get(def.id)?.allowMultipleSeats)
        assertEquals(false, instanceRepo.get(event.id)?.allowMultipleSeats)
        assertEquals(false, seriesRepo.get(course.id)?.allowMultipleSeats)
    }

    @Test
    fun `updateEventDefinition leaves children alone when propagation is off`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = definition(allowMultipleSeats = true)
        defRepo.create(def)
        val event = instance(def.id, allowMultipleSeats = true)
        instanceRepo.create(event)

        val result = makeService(defRepo = defRepo, instanceRepo = instanceRepo)
            .updateEventDefinition(def.id, updateDefinitionRequest(allowMultipleSeats = false, propagate = false))
        assertTrue(result.isRight(), "Expected Right but got $result")

        assertEquals(false, defRepo.get(def.id)?.allowMultipleSeats)
        assertEquals(true, instanceRepo.get(event.id)?.allowMultipleSeats, "bez propagace se potomek nemění")
    }
}
