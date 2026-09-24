package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Rezervace i zápis na pořadník procházejí stejnou bránou. Pořadník dřív neznal
 * zrušený kurz, uzávěrku ani proběhlý termín a rezervace kurzu neznala zrušení.
 */
class BookableGateSpec {
    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()

    private val service = ConsoleEmailService().let { console ->
        val auditRepo = InMemoryAuditRepository()
        val mails = AuditingEmailService(console, console, console, AuditService(auditRepo))
        ReservationService(
            audit = AuditService(auditRepo),
            eventInstanceRepository = instanceRepo,
            eventSeriesRepository = seriesRepo,
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            reservationRepository = InMemoryReservationRepository(),
            emailService = mails,
            lectorEmailService = mails,
            qrCodeService = StubQrCodeGenerator(),
            paymentTrigger = PaymentTrigger(),
            appBaseUrl = "https://test.example.com",
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            walletService = WalletService(InMemoryWalletRepository()),
            walletEmailService = mails,
            appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )),
        )
    }

    private fun fullInstance(start: LocalDateTime = LocalDateTime(2099, 12, 1, 10, 0)) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Plná akce",
        description = "",
        startDateTime = start,
        endDateTime = LocalDateTime(start.date, kotlinx.datetime.LocalTime(start.hour + 1, 0)),
        price = 100.0,
        capacity = 1,
        occupiedSpots = 1,
        waitlistCapacity = 3,
        isPublished = true,
    )

    private fun fullSeries() = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Plný kurz",
        description = "",
        price = 1000.0,
        capacity = 1,
        occupiedSpots = 1,
        waitlistCapacity = 3,
        isPublished = true,
        startDate = LocalDate(2099, 9, 1),
        endDate = LocalDate(2099, 12, 1),
        lessonCount = 10,
    )

    private fun instanceRequest(id: Uuid) = CreateInstanceReservationRequest(
        eventInstanceId = id, contactName = "Jan", contactEmail = "jan@test.com",
        contactPhone = "+420777111222", paymentType = PaymentType.BANK_TRANSFER, customValues = emptyMap(),
    )

    private fun seriesRequest(id: Uuid) = CreateSeriesReservationRequest(
        eventSeriesId = id, contactName = "Jan", contactEmail = "jan@test.com",
        contactPhone = "+420777111222", paymentType = PaymentType.BANK_TRANSFER, customValues = emptyMap(),
    )

    @Test
    fun `na poradnik probehle akce se zapsat nejde`() = runBlocking {
        val past = fullInstance(start = LocalDateTime(2020, 1, 1, 10, 0))
        instanceRepo.create(past)

        assertEquals(
            ReservationError.EventAlreadyFinished,
            service.joinWaitlistInstance(instanceRequest(past.id), userId = null).leftOrNull(),
        )
    }

    @Test
    fun `na poradnik akce po uzaverce se zapsat nejde`() = runBlocking {
        val closed = fullInstance().copy(reservationDeadline = 36500.days)
        instanceRepo.create(closed)

        assertEquals(
            ReservationError.ReservationDeadlinePassed,
            service.joinWaitlistInstance(instanceRequest(closed.id), userId = null).leftOrNull(),
        )
    }

    @Test
    fun `na zruseny kurz se nejde rezervovat ani zapsat na poradnik`() = runBlocking {
        val cancelled = fullSeries().copy(isCancelled = true)
        seriesRepo.create(cancelled)

        assertEquals(
            ReservationError.EventCancelled,
            service.reserveSeries(seriesRequest(cancelled.id), userId = null).leftOrNull(),
        )
        assertEquals(
            ReservationError.EventCancelled,
            service.joinWaitlistSeries(seriesRequest(cancelled.id), userId = null).leftOrNull(),
        )
    }

    @Test
    fun `na poradnik kurzu po uzaverce se zapsat nejde`() = runBlocking {
        val closed = fullSeries().copy(reservationDeadline = 36500.days)
        seriesRepo.create(closed)

        assertEquals(
            ReservationError.ReservationDeadlinePassed,
            service.joinWaitlistSeries(seriesRequest(closed.id), userId = null).leftOrNull(),
        )
    }
}
