package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.bank.BankTransaction
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * A minimal [EmailService] test double that captures the [ReservationTarget] passed to the
 * confirmation / waitlist-promotion emails, so tests can assert on it directly (this is exactly
 * what feeds [ICalGenerator.forSeries], which reads `series.lessonCount` for the RRULE COUNT).
 */
private class CapturingEmailService : EmailService {
    var lastConfirmationTarget: ReservationTarget? = null
    var lastPromotionTarget: ReservationTarget? = null

    override suspend fun sendReservationConfirmation(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendReservationConfirmation, Unit> {
        lastConfirmationTarget = target
        return Unit.right()
    }

    override suspend fun sendWaitlistPromotion(
        toEmail: String,
        reservation: Reservation,
        target: ReservationTarget,
        bankAccount: String,
        qrCodeImage: ByteArray?,
        icalBytes: ByteArray,
    ): Either<EmailError.SendWaitlistPromotion, Unit> {
        lastPromotionTarget = target
        return Unit.right()
    }

    override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String) = Unit.right()
    override suspend fun sendPaymentReceivedConfirmation(reservation: Reservation) = Unit.right()
    override suspend fun sendPaymentNotPaidInFull(reservation: Reservation, paymentInfo: BankTransaction, bankAccount: String, qrCodeImage: ByteArray) = Unit.right()
    override suspend fun sendPasswordResetEmail(toEmail: String, resetToken: String) = Unit.right()
    override suspend fun sendLessonRescheduledNotification(toEmail: String, contactName: String, seriesTitle: String, oldDateTime: LocalDateTime, newDateTime: LocalDateTime, locale: String) = Unit.right()
    override suspend fun sendLessonCancelledNotification(toEmail: String, contactName: String, seriesTitle: String, lessonDateTime: LocalDateTime, locale: String) = Unit.right()
    override suspend fun sendLessonOptOutNotice(toEmail: String, eventTitle: String, lessonDate: LocalDate, isLateCancellation: Boolean, locale: String) = Unit.right()
    override suspend fun sendWaitlistConfirmation(toEmail: String, eventTitle: String, contactName: String, reservationId: Uuid, locale: String) = Unit.right()
}

/**
 * Verifies that the two call sites in Reservation.kt which feed `ICalGenerator.forSeries`
 * (via `ReservationTarget.Series`) carry a derived `lessonCount` computed from the actual
 * `EventInstance` rows, not the stale stored value on the `EventSeries` — otherwise generated
 * .ics calendar invites for weekly-schedule courses would embed a wrong RRULE COUNT after
 * lessons are added/removed post-creation.
 */
class ReservationTargetLessonCountSpec {

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        reservationRepo: InMemoryReservationRepository,
        emailService: EmailService,
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = emailService,
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

    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = Uuid.random(),
        title = "Kurz jógy",
        description = "Popis",
        price = 100.0,
        capacity = 10,
        waitlistCapacity = 2,
        startDate = LocalDate(2099, 1, 1),
        endDate = LocalDate(2099, 3, 1),
        lessonCount = 8, // stale stored value — real active instance count below is 2
        isPublished = true,
    )

    private fun makeInstance(seriesId: Uuid, start: LocalDateTime, isCancelled: Boolean = false) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Jóga",
        description = "Popis",
        startDateTime = start,
        endDateTime = LocalDateTime(start.date, start.time),
        price = 100.0,
        capacity = 10,
        isCancelled = isCancelled,
        isPublished = true,
    )

    @Test
    fun `reserveSeries builds a ReservationTarget with derived lessonCount, not the stale stored value`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val series = makeSeries()
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 8, 9, 0)))
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 15, 9, 0)))
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 22, 9, 0), isCancelled = true))

        val emailService = CapturingEmailService()
        val service = makeService(instanceRepo, seriesRepo, reservationRepo, emailService)

        val result = service.reserveSeries(
            CreateSeriesReservationRequest(
                eventSeriesId = series.id,
                contactName = "Jan",
                contactEmail = "jan@test.com",
                contactPhone = "123",
                paymentType = PaymentInfo.Type.BANK_TRANSFER,
                customValues = emptyMap(),
            ),
            userId = null,
        )

        assertNotNull(result.getOrNull(), "Expected Right but got $result")
        val target = assertIs<ReservationTarget.Series>(emailService.lastConfirmationTarget)
        assertEquals(2, target.series.lessonCount)
    }

    @Test
    fun `promoteFromWaitlist builds a ReservationTarget with derived lessonCount, not the stale stored value`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val series = makeSeries().copy(capacity = 1, occupiedSpots = 1, waitlistCapacity = 2, occupiedWaitlist = 1)
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 8, 9, 0)))
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 15, 9, 0)))
        instanceRepo.create(makeInstance(series.id, LocalDateTime(2099, 1, 22, 9, 0), isCancelled = true))

        val confirmedReservation = Reservation(
            id = Uuid.random(),
            reference = Reference.Series(series.id),
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
            reference = Reference.Series(series.id),
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

        val emailService = CapturingEmailService()
        val service = makeService(instanceRepo, seriesRepo, reservationRepo, emailService)

        val result = service.cancelReservation(confirmedReservation.id, instanceId = null)

        assertNotNull(result.getOrNull(), "Expected Right but got $result")
        val target = assertIs<ReservationTarget.Series>(emailService.lastPromotionTarget)
        assertEquals(2, target.series.lessonCount)
    }
}
