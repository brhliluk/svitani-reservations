package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/** Vratky do peněženky jsou vidět v historii akce nebo kurzu, ke kterému patří. */
class RefundHistorySpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletRepo = InMemoryWalletRepository()
    private val walletService = WalletService(walletRepo)
    private val auditRepo = InMemoryAuditRepository()
    private val settings = AppSettingsProvider.forTest(AppSettings(
        bankAccountNumber = "", fioToken = "", senderEmail = "",
        gmailAppPassword = "", senderDisplayName = "",
    ))
    private val refundService = RefundService(walletService, ConsoleEmailService(), settings, audit = AuditService(auditRepo))

    private val admin = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = walletService,
        refundService = refundService,
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        audit = AuditService(auditRepo),
        auditRepository = auditRepo,
    )

    private inner class Caller(private val admin: Boolean) : ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = optOutRepo,
        walletService = walletService,
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = settings,
        refundService = refundService,
        audit = AuditService(auditRepo),
    ) {
        override suspend fun isAdminCaller(): Boolean = admin
    }

    private fun asAdmin() = Caller(admin = true)
    private fun asCustomer() = Caller(admin = false)

    /** Čas vůči teď v pražském čase — lekce „před hodinou“ musí být opravdu minulá. */
    private fun at(offset: Duration): LocalDateTime = (Clock.System.now() + offset).toLocalDateTime(APP_TIMEZONE)

    private val series = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 1000.0,
        capacity = 10,
        startDate = LocalDate(2000, 1, 1),
        endDate = LocalDate(2099, 12, 31),
        lessonCount = 4,
        lessonRefundAmount = 150.0,
        isPublished = true,
    )

    private suspend fun lesson(offset: Duration): EventInstance = instanceRepo.create(
        EventInstance(
            id = Uuid.random(),
            definitionId = series.definitionId,
            seriesId = series.id,
            title = "Lekce",
            description = "",
            startDateTime = at(offset),
            endDateTime = at(offset + 1.hours),
            price = 100.0,
            capacity = 10,
            isPublished = true,
        )
    )

    private suspend fun enroll(
        paidAmount: Double = 1000.0,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = Reference.Series(series.id),
            contactName = "Host",
            contactEmail = "host-${Uuid.random()}@test.cz",
            seatCount = 1,
            totalPrice = 1000.0,
            paidAmount = paidAmount,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    private suspend fun lessonCredit(reservation: Reservation): Double =
        walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.LESSON_OPT_OUT_REFUND)

    // Vratka se zapisuje do historie kurzu (seriesId) — bez toho ji admin v detailu
    // kurzu neuvidí, i když kredit do peněženky odešel.

    @Test
    fun `refund for a whole cancellation before the course starts is in the course history`() = runBlocking {
        seriesRepo.create(series.copy(startDate = LocalDate(2099, 1, 1)))
        lesson(30.days)
        val reservation = enroll()

        asCustomer().cancelReservation(reservation.id)

        val refund = auditRepo.recordedEvents().single { it.type == AuditEventType.PAYMENT_REFUNDED }
        assertEquals(1000.0, refund.amount)
        assertEquals(series.id, refund.seriesId)
    }

    @Test
    fun `refund for a lesson excuse is in the history of the course and the lesson`() = runBlocking {
        seriesRepo.create(series)
        val next = lesson(30.days)
        val reservation = enroll()

        asCustomer().cancelReservation(reservation.id, instanceId = next.id)

        val refund = auditRepo.recordedEvents().single { it.type == AuditEventType.PAYMENT_REFUNDED }
        assertEquals(150.0, refund.amount)
        assertEquals(series.id, refund.seriesId)
        assertEquals(next.id, refund.instanceId)
    }

    @Test
    fun `refund for a single event cancellation is in the event history`() = runBlocking {
        val event = instanceRepo.create(
            EventInstance(
                id = Uuid.random(), definitionId = Uuid.random(), title = "Akce", description = "",
                startDateTime = at(30.days), endDateTime = at(30.days + 1.hours),
                price = 200.0, capacity = 10, isPublished = true,
            )
        )
        val reservation = reservationRepo.save(
            enroll().copy(reference = Reference.Instance(event.id), totalPrice = 200.0, paidAmount = 200.0)
        )

        asCustomer().cancelReservation(reservation.id)

        val refund = auditRepo.recordedEvents().single { it.type == AuditEventType.PAYMENT_REFUNDED }
        assertEquals(event.id, refund.instanceId)
    }
}
