package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Kredit za lekci kurzu u rezervace na víc míst. Ruční sazba kurzu platí za jedno
 * místo; bez ní se vrací poměrná část toho, co rezervace stojí (včetně příplatků
 * z vlastních polí). Omluvenka i zrušení lekce adminem musí počítat stejně
 * a dohromady nikdy nevrátit víc, než přišlo.
 */
class LessonCreditSeatCountSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletRepo = InMemoryWalletRepository()
    private val walletService = WalletService(walletRepo)
    private val settings = AppSettingsProvider.forTest(
        AppSettings(bankAccountNumber = "", fioToken = "", senderEmail = "", gmailAppPassword = "", senderDisplayName = "")
    )

    private val reservations = ReservationService(
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
    )

    private val admin = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = walletService,
        refundService = RefundService(walletService, ConsoleEmailService(), settings),
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        audit = AuditService(InMemoryAuditRepository()),
        auditRepository = InMemoryAuditRepository(),
    )

    private val lessons = (1..4).map { day ->
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            seriesId = seriesId,
            title = "Lekce",
            description = "",
            startDateTime = LocalDateTime(2099, 12, day, 10, 0),
            endDateTime = LocalDateTime(2099, 12, day, 11, 0),
            price = 300.0,
            capacity = 10,
            isPublished = true,
        )
    }

    private suspend fun setup(lessonRefundAmount: Double?) {
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Kurz",
                description = "",
                price = 1000.0,
                capacity = 10,
                startDate = LocalDate(2099, 12, 1),
                endDate = LocalDate(2099, 12, 4),
                lessonCount = lessons.size,
                isPublished = true,
                allowMultipleSeats = true,
                lessonRefundAmount = lessonRefundAmount,
                customFields = listOf(
                    BooleanFieldDefinition(key = "material", label = "Materiál", priceModifier = PriceModifier.FixedAmount(200.0)),
                ),
            )
        )
        lessons.forEach { instanceRepo.create(it) }
    }

    /** Zaplacený zápis na 3 místa: 3 × 1000 + materiál 200 = 3200 Kč, 4 lekce → 800 Kč za lekci. */
    private suspend fun enrolThreeSeats(paidAmount: Double = 3200.0): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = Reference.Series(seriesId),
            contactName = "Rodina",
            contactEmail = "rodina@example.com",
            seatCount = 3,
            totalPrice = 3200.0,
            paidAmount = paidAmount,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = mapOf("material" to BooleanValue("material", true)),
            paymentType = PaymentType.BANK_TRANSFER,
            lessonShare = 800.0,
        )
    )

    private suspend fun credited(reservation: Reservation) = walletService.refundedForLessonOptOuts(reservation.id)

    @Test
    fun `reservation fixes the proportional share of the price at creation including seats and custom fields`() = runBlocking {
        setup(lessonRefundAmount = null)

        val result = reservations.reserveSeries(
            CreateSeriesReservationRequest(
                eventSeriesId = seriesId,
                seatCount = 3,
                contactName = "Rodina",
                contactEmail = "rodina@example.com",
                contactPhone = "+420777000000",
                paymentType = PaymentType.BANK_TRANSFER,
                customValues = mapOf("material" to BooleanValue("material", true)),
            ),
            userId = null,
        )

        val saved = result.getOrNull()
        assertEquals(3200.0, saved?.totalPrice)
        assertEquals(800.0, reservationRepo.findById(saved!!.id)?.lessonShare, "3200 Kč ÷ 4 lekce")
    }

    @Test
    fun `lesson opt-out without a manual rate refunds the proportional share of the price`() = runBlocking {
        setup(lessonRefundAmount = null)
        val enrollment = enrolThreeSeats()

        val result = reservations.cancelReservation(enrollment.id, instanceId = lessons[0].id)

        assertTrue(result.isRight(), "omluvenka musí projít, dostal: $result")
        assertEquals(800.0, credited(enrollment))
    }

    @Test
    fun `lesson opt-out with a manual rate multiplies it by the seat count`() = runBlocking {
        setup(lessonRefundAmount = 150.0)
        val enrollment = enrolThreeSeats()

        reservations.cancelReservation(enrollment.id, instanceId = lessons[0].id)

        assertEquals(450.0, credited(enrollment))
    }

    @Test
    fun `lesson cancellation by admin refunds the same as a lesson opt-out`() = runBlocking {
        setup(lessonRefundAmount = 150.0)
        val enrollment = enrolThreeSeats()

        admin.cancelSeriesLesson(lessons[0].id)

        assertEquals(450.0, credited(enrollment), "sazba 150 Kč × 3 místa, ne jen za jedno")
    }

    @Test
    fun `lesson cancellation by admin without a manual rate refunds the proportional share of the price`() = runBlocking {
        setup(lessonRefundAmount = null)
        val enrollment = enrolThreeSeats()

        admin.cancelSeriesLesson(lessons[0].id)

        assertEquals(800.0, credited(enrollment))
    }

    @Test
    fun `lesson cancellation by admin does not refund more than was paid`() = runBlocking {
        setup(lessonRefundAmount = 150.0)
        val enrollment = enrolThreeSeats(paidAmount = 500.0)

        admin.cancelSeriesLesson(lessons[0].id)
        admin.cancelSeriesLesson(lessons[1].id)

        assertEquals(500.0, credited(enrollment), "2 × 450 Kč by přerostlo zaplacených 500 Kč")
    }

    @Test
    fun `zero rate means no refund`() = runBlocking {
        setup(lessonRefundAmount = 0.0)
        val enrollment = enrolThreeSeats()

        reservations.cancelReservation(enrollment.id, instanceId = lessons[0].id)
        admin.cancelSeriesLesson(lessons[1].id)

        assertEquals(0.0, credited(enrollment))
    }

    @Test
    fun `reservation detail preview shows credit for all seats`() = runBlocking {
        setup(lessonRefundAmount = null)
        val enrollment = enrolThreeSeats()

        val view = reservations.getSeriesLessons(enrollment.id).getOrNull()

        assertEquals(800.0, view?.lessonCredit)
    }
}
