package cz.svitaninymburk.projects.reservations.service

import arrow.core.right
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesLessonOptOutServiceTest {

    /**
     * A fixed user ID used as the "logged-in caller" in tests.
     * Tests set registeredUserId on reservations to this value so
     * the ownership check in cancelReservation passes.
     */
    private val testCallerId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

    /**
     * Subclass that bypasses the Ktor call context and returns a fixed caller ID,
     * allowing unit tests to run without a real HTTP request.
     */
    private inner class TestReservationService(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        defRepo: InMemoryEventDefinitionRepository,
        reservationRepo: InMemoryReservationRepository,
        optOutRepo: InMemorySeriesLessonOptOutRepository,
        private val callerId: Uuid? = testCallerId,
    ) : ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = defRepo,
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = optOutRepo,
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = callerId
    }

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        defRepo: InMemoryEventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
        optOutRepo: InMemorySeriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        callerId: Uuid? = testCallerId,
    ) = TestReservationService(
        instanceRepo = instanceRepo,
        seriesRepo = seriesRepo,
        defRepo = defRepo,
        reservationRepo = reservationRepo,
        optOutRepo = optOutRepo,
        callerId = callerId,
    )

    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = Uuid.random(),
        title = "Test Series",
        description = "",
        price = 500.0,
        capacity = 10,
        occupiedSpots = 1,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 12, 31),
        lessonCount = 10,
    )

    private fun makeInstance(
        seriesId: Uuid,
        id: Uuid = Uuid.random(),
        startDateTime: LocalDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        isCancelled: Boolean = false,
        ownerEmails: List<String> = emptyList(),
    ) = EventInstance(
        id = id,
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Test Lesson",
        description = "",
        startDateTime = startDateTime,
        endDateTime = startDateTime.let { LocalDateTime(it.year, it.month, it.dayOfMonth, it.hour + 1, 0) },
        price = 100.0,
        capacity = 10,
        occupiedSpots = 1,
        isCancelled = isCancelled,
        ownerEmails = ownerEmails,
    )

    private fun makeSeriesReservation(seriesId: Uuid, id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = Reference.Series(seriesId),
        registeredUserId = testCallerId,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        seatCount = 1,
        totalPrice = 500.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    private fun makeInstanceReservation(instanceId: Uuid, id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = Reference.Instance(instanceId),
        registeredUserId = testCallerId,
        contactName = "Jana Novakova",
        contactEmail = "jana@test.com",
        seatCount = 1,
        totalPrice = 100.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    @Test
    fun `opt out nesaha na ulozeny citac lekce`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        val savedOptOut = optOutRepo.findByReservationAndInstance(reservation.id, instance.id)
        assertNotNull(savedOptOut, "omluvenka se musí uložit — ta je nově tím odečtem")
        assertEquals(reservation.id, savedOptOut.reservationId)
        assertEquals(instance.id, savedOptOut.instanceId)

        assertEquals(
            instance.occupiedSpots,
            instanceRepo.get(instance.id)?.occupiedSpots,
            "uložený čítač lekce se omluvou nemění; obsazenost se dopočítává ze SeriesLessonLoad",
        )
        assertEquals(
            series.occupiedSpots,
            seriesRepo.get(series.id)?.occupiedSpots,
            "přihláška na kurz trvá dál, jen na jednu lekci nedorazí",
        )
    }

    @Test
    fun `opt out posune cekatele z poradniku lekce`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        // Čekatel v pořadníku té konkrétní lekce.
        val cekatel = reservationRepo.save(
            makeInstanceReservation(instance.id).copy(status = Reservation.Status.WAITLISTED)
        )

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        assertEquals(
            Reservation.Status.PENDING_PAYMENT,
            reservationRepo.findById(cekatel.id)?.status,
            "uvolněné místo má dostat první čekatel v pořadníku lekce",
        )
    }

    @Test
    fun `opt out fails when reservation is not a series reservation`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val instanceId = Uuid.random()
        val instance = makeInstance(Uuid.random(), id = instanceId)
        instanceRepo.create(instance)

        val reservation = makeInstanceReservation(instanceId)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instanceId)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.NotASeriesReservation, error)
        }
        Unit
    }

    @Test
    fun `opt out fails when instance does not belong to the series`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val otherSeriesId = Uuid.random()
        val instanceFromOtherSeries = makeInstance(otherSeriesId)
        instanceRepo.create(instanceFromOtherSeries)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instanceFromOtherSeries.id)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.InstanceNotInSeries, error)
        }
        Unit
    }

    @Test
    fun `opt out fails when already opted out`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries()
        seriesRepo.create(series)

        val instance = makeInstance(series.id, startDateTime = LocalDateTime(2099, 12, 1, 10, 0))
        instanceRepo.create(instance)

        val reservation = makeSeriesReservation(series.id)
        reservationRepo.save(reservation)

        // Pre-save an opt-out record to simulate already opted out
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = reservation.id,
                instanceId = instance.id,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        val service = makeService(
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            optOutRepo = optOutRepo,
        )

        val result = service.cancelReservation(reservation.id, instance.id)
        assertTrue(result.isLeft(), "Expected Left but got: $result")
        result.onLeft { error ->
            assertEquals(ReservationError.AlreadyOptedOut, error)
        }
        Unit
    }
}
