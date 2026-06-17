package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

class WaitlistPromotionSpec {

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository,
        reservationRepo: InMemoryReservationRepository,
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = InMemoryEventSeriesRepository(),
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
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    )

    @Test
    fun `cancelling a reservation promotes the oldest waitlisted person`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instanceId = Uuid.random()

        val instance = EventInstance(
            id = instanceId,
            definitionId = Uuid.random(),
            title = "Full Event",
            description = "",
            startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
            price = 100.0,
            capacity = 2,
            occupiedSpots = 2,
            waitlistCapacity = 2,
            occupiedWaitlist = 1,
            isPublished = true,
        )
        instanceRepo.create(instance)

        val confirmedReservation = Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(instanceId),
            contactName = "Jana Novakova",
            contactEmail = "jana@test.com",
            seatCount = 1,
            totalPrice = 100.0,
            paidAmount = 100.0,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
            variableSymbol = "2612300001",
        )
        val waitlistedReservation = Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(instanceId),
            contactName = "Petr Svoboda",
            contactEmail = "petr@test.com",
            seatCount = 1,
            totalPrice = 100.0,
            status = Reservation.Status.WAITLISTED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
        reservationRepo.save(confirmedReservation)
        reservationRepo.save(waitlistedReservation)

        val service = makeService(instanceRepo, reservationRepo)
        val result = service.cancelReservation(confirmedReservation.id, instanceId = null)

        assertNotNull(result.getOrNull(), "Expected Right but got $result")

        val promoted = reservationRepo.findById(waitlistedReservation.id)!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, promoted.status)
        assertNotNull(promoted.variableSymbol)

        val updatedInstance = instanceRepo.get(instanceId)!!
        assertEquals(0, updatedInstance.occupiedWaitlist)
        assertEquals(2, updatedInstance.occupiedSpots)
    }

    @Test
    fun `cancelling when no waitlisted reservations does nothing extra`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instanceId = Uuid.random()

        val instance = EventInstance(
            id = instanceId,
            definitionId = Uuid.random(),
            title = "Event",
            description = "",
            startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
            price = 100.0,
            capacity = 2,
            occupiedSpots = 1,
            waitlistCapacity = 2,
            occupiedWaitlist = 0,
            isPublished = true,
        )
        instanceRepo.create(instance)

        val reservation = Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(instanceId),
            contactName = "Jana Novakova",
            contactEmail = "jana@test.com",
            seatCount = 1,
            totalPrice = 100.0,
            status = Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
            variableSymbol = "2612300002",
        )
        reservationRepo.save(reservation)

        val service = makeService(instanceRepo, reservationRepo)
        val result = service.cancelReservation(reservation.id, instanceId = null)

        assertNotNull(result.getOrNull(), "Expected Right but got $result")
        val updatedInstance = instanceRepo.get(instanceId)!!
        assertEquals(0, updatedInstance.occupiedWaitlist)
        assertEquals(0, updatedInstance.occupiedSpots)
    }
}
