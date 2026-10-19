package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Lekce si pole kurzu kopírují při založení. Úprava kurzu se do nich má propsat —
 * ale jen tam, kde lekce pořád dědí; samostatně upravenou lekci nepřepisuje.
 */
class SeriesEditPropagationSpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val walletService = WalletService(InMemoryWalletRepository())

    private val service = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = walletService,
        refundService = RefundService(
            walletService, ConsoleEmailService(),
            AppSettingsProvider.forTest(AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )),
        ),
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    private val series = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Cvičení pro maminky",
        description = "",
        price = 1000.0,
        capacity = 10,
        startDate = LocalDate(2099, 1, 1),
        endDate = LocalDate(2099, 3, 1),
        lessonCount = 2,
    )

    private fun lesson(day: Int) = EventInstance(
        id = Uuid.random(),
        definitionId = series.definitionId,
        seriesId = series.id,
        title = series.title,
        description = series.description,
        startDateTime = LocalDateTime(2099, 1, day, 10, 0),
        endDateTime = LocalDateTime(2099, 1, day, 11, 0),
        price = series.price,
        capacity = series.capacity,
        allowedPaymentTypes = series.allowedPaymentTypes,
    )

    private fun request(
        title: String = series.title,
        description: String = series.description,
        capacity: Int = series.capacity,
    ) = UpdateEventSeriesRequest(
        title = title,
        description = description,
        price = series.price,
        capacity = capacity,
        waitlistCapacity = series.waitlistCapacity,
        allowedPaymentTypes = series.allowedPaymentTypes,
        customFields = series.customFields,
        ownerEmails = series.ownerEmails,
        showAttendeeCount = series.showAttendeeCount,
        allowMultipleSeats = series.allowMultipleSeats,
        lessonPrice = series.lessonPrice,
        lessonRefundAmount = series.lessonRefundAmount,
        reservationDeadline = series.reservationDeadline,
        reservationDeadlineMessage = series.reservationDeadlineMessage,
    )

    @Test
    fun `description added later propagates to lessons`() = runBlocking {
        seriesRepo.create(series)
        val lessons = listOf(lesson(7), lesson(14)).onEach { instanceRepo.create(it) }

        assertTrue(service.updateEventSeries(series.id, request(description = "Přineste si podložku.")).isRight())

        lessons.forEach { assertEquals("Přineste si podložku.", instanceRepo.get(it.id)?.description) }
    }

    @Test
    fun `individually edited lesson keeps its own value`() = runBlocking {
        seriesRepo.create(series)
        val inheriting = lesson(7).also { instanceRepo.create(it) }
        val customized = lesson(14).copy(description = "Tahle lekce je venku.").also { instanceRepo.create(it) }

        service.updateEventSeries(series.id, request(description = "Přineste si podložku."))

        assertEquals("Přineste si podložku.", instanceRepo.get(inheriting.id)?.description)
        assertEquals("Tahle lekce je venku.", instanceRepo.get(customized.id)?.description)
    }

    @Test
    fun `only the field changed in the course propagates`() = runBlocking {
        // Lekce má vlastní název; úprava popisu kurzu ho nesmí srovnat zpátky.
        seriesRepo.create(series)
        val lessonInstance = lesson(7).copy(title = "Lekce venku").also { instanceRepo.create(it) }

        service.updateEventSeries(series.id, request(description = "Nový popis"))

        val stored = instanceRepo.get(lessonInstance.id)!!
        assertEquals("Lekce venku", stored.title)
        assertEquals("Nový popis", stored.description)
    }

    @Test
    fun `course capacity propagates to lessons`() = runBlocking {
        seriesRepo.create(series)
        val lessonInstance = lesson(7).also { instanceRepo.create(it) }

        service.updateEventSeries(series.id, request(capacity = 12))

        assertEquals(12, instanceRepo.get(lessonInstance.id)?.capacity)
    }

    @Test
    fun `payment methods propagate`() = runBlocking {
        seriesRepo.create(series)
        val lessonInstance = lesson(7).also { instanceRepo.create(it) }

        service.updateEventSeries(
            series.id,
            request().copy(allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER)),
        )

        assertEquals(listOf(PaymentType.BANK_TRANSFER), instanceRepo.get(lessonInstance.id)?.allowedPaymentTypes)
    }
}
