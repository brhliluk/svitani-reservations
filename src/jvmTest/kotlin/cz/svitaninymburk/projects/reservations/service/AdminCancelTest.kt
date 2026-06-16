package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminCancelTest {

    private fun service(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        reservationRepo: InMemoryReservationRepository,
        walletRepo: InMemoryWalletRepository = InMemoryWalletRepository(),
    ) = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(walletRepo),
        refundService = RefundService(
            walletService = WalletService(walletRepo),
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

    private fun futureInstance(seriesId: Uuid? = null) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Test Event",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 31, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 31, 11, 0),
        price = 0.0,
        capacity = 10,
        isPublished = true,
    )

    private fun pastInstance(seriesId: Uuid? = null) = futureInstance(seriesId).copy(
        id = Uuid.random(),
        startDateTime = LocalDateTime(2000, 1, 1, 10, 0),
        endDateTime = LocalDateTime(2000, 1, 1, 11, 0),
    )

    private fun eventSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = Uuid.random(),
        title = "Test Series",
        description = "",
        price = 0.0,
        capacity = 10,
        startDate = LocalDate(2099, 1, 1),
        endDate = LocalDate(2099, 12, 31),
        lessonCount = 5,
        isPublished = true,
    )

    private suspend fun saveReservation(
        repo: InMemoryReservationRepository,
        reference: Reference,
        paidAmount: Double = 0.0,
    ): Reservation {
        val res = Reservation(
            id = Uuid.random(),
            reference = reference,
            contactName = "Test User",
            contactEmail = "test@example.com",
            seatCount = 1,
            totalPrice = paidAmount,
            paidAmount = paidAmount,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
        return repo.save(res)
    }

    // --- cancelEventInstance ---

    @Test
    fun `cancelEventInstance returns InstanceNotFoundForCancel for unknown id`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val id = Uuid.random()

        val result = service(instanceRepo, InMemoryEventSeriesRepository(), InMemoryReservationRepository())
            .cancelEventInstance(id)

        assertTrue(result.isLeft())
        assertEquals(AdminError.InstanceNotFoundForCancel(id), result.leftOrNull())
    }

    @Test
    fun `cancelEventInstance returns EventAlreadyPassed for past instance`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = pastInstance()
        instanceRepo.create(instance)

        val result = service(instanceRepo, InMemoryEventSeriesRepository(), InMemoryReservationRepository())
            .cancelEventInstance(instance.id)

        assertTrue(result.isLeft())
        assertEquals(AdminError.EventAlreadyPassed(instance.id), result.leftOrNull())
    }

    @Test
    fun `cancelEventInstance marks the instance as cancelled`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = futureInstance()
        instanceRepo.create(instance)

        val result = service(instanceRepo, InMemoryEventSeriesRepository(), InMemoryReservationRepository())
            .cancelEventInstance(instance.id)

        assertTrue(result.isRight())
        assertEquals(true, instanceRepo.get(instance.id)?.isCancelled)
    }

    @Test
    fun `cancelEventInstance cancels active reservations`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instance = futureInstance()
        instanceRepo.create(instance)
        val reservation = saveReservation(reservationRepo, Reference.Instance(instance.id))

        service(instanceRepo, InMemoryEventSeriesRepository(), reservationRepo)
            .cancelEventInstance(instance.id)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `cancelEventInstance does not change already cancelled reservations`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instance = futureInstance()
        instanceRepo.create(instance)
        val res = saveReservation(reservationRepo, Reference.Instance(instance.id))
        reservationRepo.updateStatus(res.id, Reservation.Status.CANCELLED)

        service(instanceRepo, InMemoryEventSeriesRepository(), reservationRepo)
            .cancelEventInstance(instance.id)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(res.id)?.status)
    }

    @Test
    fun `cancelEventInstance with refund=false still cancels reservations`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instance = futureInstance()
        instanceRepo.create(instance)
        val reservation = saveReservation(reservationRepo, Reference.Instance(instance.id), paidAmount = 100.0)

        service(instanceRepo, InMemoryEventSeriesRepository(), reservationRepo)
            .cancelEventInstance(instance.id, refund = false)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `cancelEventInstance with refund=false does not credit any wallet`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()
        val instance = futureInstance()
        instanceRepo.create(instance)
        saveReservation(reservationRepo, Reference.Instance(instance.id), paidAmount = 100.0)

        service(instanceRepo, InMemoryEventSeriesRepository(), reservationRepo, walletRepo)
            .cancelEventInstance(instance.id, refund = false)

        assertEquals(emptyList(), walletRepo.findAllWithPositiveBalance())
    }

    // --- cancelEventSeries ---

    @Test
    fun `cancelEventSeries returns SeriesNotFoundForCancel for unknown id`() = runBlocking {
        val seriesRepo = InMemoryEventSeriesRepository()
        val id = Uuid.random()

        val result = service(InMemoryEventInstanceRepository(), seriesRepo, InMemoryReservationRepository())
            .cancelEventSeries(id)

        assertTrue(result.isLeft())
        assertEquals(AdminError.SeriesNotFoundForCancel(id), result.leftOrNull())
    }

    @Test
    fun `cancelEventSeries returns EventAlreadyPassed when no future instances exist`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        instanceRepo.create(pastInstance(seriesId = series.id))

        val result = service(instanceRepo, seriesRepo, InMemoryReservationRepository())
            .cancelEventSeries(series.id)

        assertTrue(result.isLeft())
        assertEquals(AdminError.EventAlreadyPassed(series.id), result.leftOrNull())
    }

    @Test
    fun `cancelEventSeries marks series as cancelled`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        instanceRepo.create(futureInstance(seriesId = series.id))

        val result = service(instanceRepo, seriesRepo, InMemoryReservationRepository())
            .cancelEventSeries(series.id)

        assertTrue(result.isRight())
        assertEquals(true, seriesRepo.get(series.id)?.isCancelled)
    }

    @Test
    fun `cancelEventSeries marks only future instances as cancelled, not past ones`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        val past = pastInstance(seriesId = series.id)
        val future = futureInstance(seriesId = series.id)
        instanceRepo.create(past)
        instanceRepo.create(future)

        service(instanceRepo, seriesRepo, InMemoryReservationRepository())
            .cancelEventSeries(series.id)

        assertEquals(false, instanceRepo.get(past.id)?.isCancelled)
        assertEquals(true, instanceRepo.get(future.id)?.isCancelled)
    }

    @Test
    fun `cancelEventSeries cancels direct series reservations`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        instanceRepo.create(futureInstance(seriesId = series.id))
        val seriesReservation = saveReservation(reservationRepo, Reference.Series(series.id))

        service(instanceRepo, seriesRepo, reservationRepo).cancelEventSeries(series.id)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(seriesReservation.id)?.status)
    }

    @Test
    fun `cancelEventSeries with refund=false still cancels reservations`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        instanceRepo.create(futureInstance(seriesId = series.id))
        val reservation = saveReservation(reservationRepo, Reference.Series(series.id), paidAmount = 100.0)

        service(instanceRepo, seriesRepo, reservationRepo)
            .cancelEventSeries(series.id, refund = false)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `cancelEventSeries with refund=false does not credit any wallet`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()
        val series = eventSeries()
        seriesRepo.create(series)
        instanceRepo.create(futureInstance(seriesId = series.id))
        saveReservation(reservationRepo, Reference.Series(series.id), paidAmount = 100.0)

        service(instanceRepo, seriesRepo, reservationRepo, walletRepo)
            .cancelEventSeries(series.id, refund = false)

        assertEquals(emptyList(), walletRepo.findAllWithPositiveBalance())
    }
}
