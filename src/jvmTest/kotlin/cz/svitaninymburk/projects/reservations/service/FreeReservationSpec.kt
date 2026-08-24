package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Akce zdarma nemá uživatele nikam posílat platit: rezervace na ni vzniká rovnou
 * potvrzená a s typem platby FREE, aby detail nenabízel QR kód na 0 Kč a aby
 * nepadala do admin přehledu nezaplacených.
 */
class FreeReservationSpec {

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

    private fun instance(
        price: Double,
        capacity: Int = 5,
        occupiedSpots: Int = 0,
        waitlistCapacity: Int = 0,
        occupiedWaitlist: Int = 0,
    ) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Beseda",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = price,
        capacity = capacity,
        occupiedSpots = occupiedSpots,
        waitlistCapacity = waitlistCapacity,
        occupiedWaitlist = occupiedWaitlist,
        isPublished = true,
    )

    private fun request(instanceId: Uuid) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyMap(),
    )

    @Test
    fun `reservation for a zero price event is confirmed immediately as free`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val free = instance(price = 0.0)
        instanceRepo.create(free)
        val service = makeService(instanceRepo, reservationRepo)

        val result = service.reserveInstance(request(free.id), userId = null)

        assertTrue(result.isRight(), "Expected Right but got $result")
        val reservation = result.getOrNull()!!
        assertEquals(0.0, reservation.totalPrice)
        assertEquals(Reservation.Status.CONFIRMED, reservation.status)
        assertEquals(PaymentInfo.Type.FREE, reservation.paymentType)
        assertEquals(0.0, reservation.unpaidAmount)
    }

    @Test
    fun `reservation for a paid event still waits for payment`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val paid = instance(price = 150.0)
        instanceRepo.create(paid)
        val service = makeService(instanceRepo, reservationRepo)

        val result = service.reserveInstance(request(paid.id), userId = null)

        val reservation = result.getOrNull()!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, reservation.status)
        assertEquals(PaymentInfo.Type.BANK_TRANSFER, reservation.paymentType)
    }

    @Test
    fun `promotion from the waitlist of a free event confirms the reservation as free`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val free = instance(
            price = 0.0,
            capacity = 1,
            occupiedSpots = 1,
            waitlistCapacity = 2,
            occupiedWaitlist = 1,
        )
        instanceRepo.create(free)

        val confirmed = Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(free.id),
            contactName = "Jana Novakova",
            contactEmail = "jana@test.com",
            seatCount = 1,
            totalPrice = 0.0,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.FREE,
        )
        val waitlisted = Reservation(
            id = Uuid.random(),
            reference = Reference.Instance(free.id),
            contactName = "Petr Svoboda",
            contactEmail = "petr@test.com",
            seatCount = 1,
            totalPrice = 0.0,
            status = Reservation.Status.WAITLISTED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.FREE,
        )
        reservationRepo.save(confirmed)
        reservationRepo.save(waitlisted)

        val service = makeService(instanceRepo, reservationRepo)
        val result = service.cancelReservation(confirmed.id, instanceId = null)
        assertNotNull(result.getOrNull(), "Expected Right but got $result")

        val promoted = reservationRepo.findById(waitlisted.id)!!
        assertEquals(Reservation.Status.CONFIRMED, promoted.status)
        assertEquals(PaymentInfo.Type.FREE, promoted.paymentType)
    }
}
