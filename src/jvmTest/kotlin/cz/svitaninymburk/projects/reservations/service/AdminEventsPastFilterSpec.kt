package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class AdminEventsPastFilterSpec {

    private fun service(
        defRepo: InMemoryEventDefinitionRepository,
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
    ) = AdminDashboardService(
        eventDefinitionRepository = defRepo,
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = InMemoryReservationRepository(),
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
    )

    private fun definition(id: Uuid, title: String = "Def") = EventDefinition(
        id = id, title = title, description = "", defaultPrice = 100.0, defaultCapacity = 10,
        defaultDuration = 1.hours, allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
        customFields = emptyList(), ownerEmails = emptyList(),
    )

    private fun instance(definitionId: Uuid, start: LocalDateTime, end: LocalDateTime) = EventInstance(
        id = Uuid.random(), definitionId = definitionId, title = "I", description = "D",
        startDateTime = start, endDateTime = end, price = 100.0, capacity = 10, isPublished = true,
    )

    private fun series(definitionId: Uuid, startDate: LocalDate, endDate: LocalDate) = EventSeries(
        id = Uuid.random(), definitionId = definitionId, title = "S", description = "D",
        price = 100.0, capacity = 10, startDate = startDate, endDate = endDate, lessonCount = 5,
        isPublished = true,
    )

    private val pastStart = LocalDateTime(2020, 1, 1, 9, 0)
    private val pastEnd = LocalDateTime(2020, 1, 1, 10, 0)
    private val futureStart = LocalDateTime(2099, 1, 1, 9, 0)
    private val futureEnd = LocalDateTime(2099, 1, 1, 10, 0)

    @Test
    fun `past instance hidden by default and shown with includePast`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val defId = Uuid.random()
        defRepo.create(definition(defId))
        instanceRepo.create(instance(defId, pastStart, pastEnd))
        val svc = service(defRepo, instanceRepo, InMemoryEventSeriesRepository())

        val hidden = svc.getAllEvents(0, 20, includePast = false).getOrNull()!!
        assertTrue(hidden.items.isEmpty(), "definice s jen proběhlými dětmi se nezobrazí")

        val shown = svc.getAllEvents(0, 20, includePast = true).getOrNull()!!
        val childRows = shown.items.filter { !it.isDefinitionOnly }
        assertEquals(1, childRows.size)
        assertTrue(childRows.first().isPast)
        assertEquals(1, shown.items.count { it.isDefinitionOnly })
    }

    @Test
    fun `past series hidden by default and shown with includePast`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val defId = Uuid.random()
        defRepo.create(definition(defId))
        seriesRepo.create(series(defId, LocalDate(2020, 1, 1), LocalDate(2020, 3, 1)))
        val svc = service(defRepo, InMemoryEventInstanceRepository(), seriesRepo)

        assertTrue(svc.getAllEvents(0, 20, includePast = false).getOrNull()!!.items.isEmpty())
        val shown = svc.getAllEvents(0, 20, includePast = true).getOrNull()!!
        assertEquals(1, shown.items.count { it.isSeries && it.isPast })
    }

    @Test
    fun `definition with mixed children shows only upcoming by default`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val defId = Uuid.random()
        defRepo.create(definition(defId))
        instanceRepo.create(instance(defId, pastStart, pastEnd))
        instanceRepo.create(instance(defId, futureStart, futureEnd))
        val svc = service(defRepo, instanceRepo, InMemoryEventSeriesRepository())

        val page = svc.getAllEvents(0, 20, includePast = false).getOrNull()!!
        val childRows = page.items.filter { !it.isDefinitionOnly }
        assertEquals(1, childRows.size)
        assertFalse(childRows.first().isPast)
        assertEquals(1, page.items.count { it.isDefinitionOnly }, "definice zůstává viditelná")
    }

    @Test
    fun `definition with no children stays visible by default`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val defId = Uuid.random()
        defRepo.create(definition(defId))
        val svc = service(defRepo, InMemoryEventInstanceRepository(), InMemoryEventSeriesRepository())

        val page = svc.getAllEvents(0, 20, includePast = false).getOrNull()!!
        assertEquals(1, page.items.count { it.isDefinitionOnly })
    }
}
