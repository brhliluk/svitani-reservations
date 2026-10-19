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
 * Události s vypnutým [EventInstance.allowMultipleSeats] se rezervují vždy po jednom místě —
 * webový i mobilní formulář pole "počet míst" vůbec nezobrazí, server to musí vynutit i pro
 * ostatní klienty.
 */
class AllowMultipleSeatsSpec {

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

    private fun instance(allowMultipleSeats: Boolean) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Jednorázová akce",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        isPublished = true,
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun series(allowMultipleSeats: Boolean) = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 100.0,
        capacity = 10,
        isPublished = true,
        startDate = LocalDate(2099, 12, 1),
        endDate = LocalDate(2099, 12, 22),
        lessonCount = 4,
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun instanceRequest(instanceId: Uuid, seatCount: Int) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = seatCount,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentType.BANK_TRANSFER,
        customValues = emptyMap(),
    )

    private fun seriesRequest(seriesId: Uuid, seatCount: Int) = CreateSeriesReservationRequest(
        eventSeriesId = seriesId,
        seatCount = seatCount,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentType.BANK_TRANSFER,
        customValues = emptyMap(),
    )

    @Test
    fun `instance reservation of two seats is rejected when multiple seats are not allowed`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = instance(allowMultipleSeats = false)
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, seatCount = 2), userId = null)

        assertEquals(ReservationError.MultipleSeatsNotAllowed, result.leftOrNull())
        assertEquals(0, instanceRepo.get(event.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
    }

    @Test
    fun `instance reservation of one seat succeeds when multiple seats are not allowed`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = instance(allowMultipleSeats = false)
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, seatCount = 1), userId = null)

        assertTrue(result.isRight(), "Expected Right but got $result")
        assertEquals(1, result.getOrNull()!!.seatCount)
    }

    @Test
    fun `instance reservation of two seats succeeds when multiple seats are allowed`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = instance(allowMultipleSeats = true)
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, seatCount = 2), userId = null)

        assertTrue(result.isRight(), "Expected Right but got $result")
        assertEquals(2, result.getOrNull()!!.seatCount)
    }

    @Test
    fun `series reservation of two seats is rejected when multiple seats are not allowed`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val course = series(allowMultipleSeats = false)
        seriesRepo.create(course)
        val service = makeService(seriesRepo = seriesRepo)

        val result = service.reserveSeries(seriesRequest(course.id, seatCount = 2), userId = null)

        assertEquals(ReservationError.MultipleSeatsNotAllowed, result.leftOrNull())
        assertEquals(0, seriesRepo.get(course.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
    }

    @Test
    fun `series reservation of one seat succeeds when multiple seats are not allowed`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val course = series(allowMultipleSeats = false)
        seriesRepo.create(course)
        val service = makeService(seriesRepo = seriesRepo)

        val result = service.reserveSeries(seriesRequest(course.id, seatCount = 1), userId = null)

        assertTrue(result.isRight(), "Expected Right but got $result")
        assertEquals(1, result.getOrNull()!!.seatCount)
    }

    @Test
    fun `instance reservation of zero seats is rejected`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val event = instance(allowMultipleSeats = true)
        instanceRepo.create(event)
        val service = makeService(instanceRepo = instanceRepo)

        val result = service.reserveInstance(instanceRequest(event.id, seatCount = 0), userId = null)

        assertEquals(ReservationError.InvalidSeatCount, result.leftOrNull())
    }

    @Test
    fun `series reservation of zero seats is rejected`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val course = series(allowMultipleSeats = true)
        seriesRepo.create(course)
        val service = makeService(seriesRepo = seriesRepo)

        val result = service.reserveSeries(seriesRequest(course.id, seatCount = 0), userId = null)

        assertEquals(ReservationError.InvalidSeatCount, result.leftOrNull())
    }
}
