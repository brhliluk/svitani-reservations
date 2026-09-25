package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Server přijme jen způsob platby, který akce povoluje. Rodiči s akcí jen na hotovost
 * by jinak přišel e-mail s QR kódem k převodu.
 */
class PaymentTypeAllowedSpec {

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        reservationRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private fun onSiteOnlyInstance() = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Jen hotově",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        isPublished = true,
        allowedPaymentTypes = listOf(PaymentType.ON_SITE),
    )

    private fun onSiteOnlySeries() = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz jen hotově",
        description = "",
        price = 100.0,
        capacity = 10,
        isPublished = true,
        startDate = LocalDate(2099, 12, 1),
        endDate = LocalDate(2099, 12, 22),
        lessonCount = 4,
        allowedPaymentTypes = listOf(PaymentType.ON_SITE),
    )

    private fun instanceRequest(instanceId: Uuid, paymentType: PaymentType) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = paymentType,
        customValues = emptyMap(),
    )

    @Test
    fun `bank transfer is rejected for an on-site only event without taking a seat`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = onSiteOnlyInstance()
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, PaymentType.BANK_TRANSFER), userId = null)

        assertEquals(ReservationError.PaymentTypeNotAllowed, result.leftOrNull())
        assertEquals(0, instanceRepo.get(event.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
    }

    @Test
    fun `allowed payment type is accepted`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = onSiteOnlyInstance()
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, PaymentType.ON_SITE), userId = null)

        assertEquals(PaymentType.ON_SITE, result.getOrNull()?.paymentType)
    }

    @Test
    fun `bank transfer is rejected for an on-site only course`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val course = onSiteOnlySeries()
        seriesRepo.create(course)
        val service = makeService(seriesRepo = seriesRepo)

        val result = service.reserveSeries(
            CreateSeriesReservationRequest(
                eventSeriesId = course.id,
                seatCount = 1,
                contactName = "Jan Novak",
                contactEmail = "jan@test.com",
                contactPhone = "+420777111222",
                paymentType = PaymentType.BANK_TRANSFER,
                customValues = emptyMap(),
            ),
            userId = null,
        )

        assertEquals(ReservationError.PaymentTypeNotAllowed, result.leftOrNull())
        assertEquals(0, seriesRepo.get(course.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
    }
}
