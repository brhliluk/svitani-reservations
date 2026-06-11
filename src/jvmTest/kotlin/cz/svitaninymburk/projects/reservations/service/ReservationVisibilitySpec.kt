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
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ReservationVisibilitySpec {

    private fun service(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = InMemoryReservationRepository(),
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    )

    private fun instanceRequest(id: Uuid) = CreateInstanceReservationRequest(
        eventInstanceId = id, contactName = "Jan", contactEmail = "jan@test.com",
        contactPhone = "123", paymentType = PaymentInfo.Type.BANK_TRANSFER, customValues = emptyMap(),
    )

    private fun seriesRequest(id: Uuid) = CreateSeriesReservationRequest(
        eventSeriesId = id, contactName = "Jan", contactEmail = "jan@test.com",
        contactPhone = "123", paymentType = PaymentInfo.Type.BANK_TRANSFER, customValues = emptyMap(),
    )

    @Test
    fun `reserveInstance on hidden instance returns ReservationNotFound`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val hidden = EventInstance(
            id = Uuid.random(), definitionId = Uuid.random(), title = "I", description = "D",
            startDateTime = LocalDateTime(2099, 1, 1, 9, 0), endDateTime = LocalDateTime(2099, 1, 1, 10, 0),
            price = 100.0, capacity = 10, isPublished = false,
        )
        instanceRepo.create(hidden)
        val result = service(instanceRepo, InMemoryEventSeriesRepository())
            .reserveInstance(instanceRequest(hidden.id), userId = null)
        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
    }

    @Test
    fun `reserveSeries on hidden series returns ReservationNotFound`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val hidden = EventSeries(
            id = Uuid.random(), definitionId = Uuid.random(), title = "S", description = "D",
            price = 100.0, capacity = 10,
            startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 3, 1), lessonCount = 5,
            isPublished = false,
        )
        seriesRepo.create(hidden)
        val result = service(InMemoryEventInstanceRepository(), seriesRepo)
            .reserveSeries(seriesRequest(hidden.id), userId = null)
        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
    }
}
