package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
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
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
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
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Zrušení akce nebo lekce s výběrem, komu se vrací kredit. Admin v dialogu odškrtne
 * třeba toho, kdo platil převodem a peníze dostane zpět na účet; ostatní kredit
 * dostanou jako dřív a stornování ani e-maily se výběrem nemění.
 */
class SelectiveCancellationRefundSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletRepo = InMemoryWalletRepository()
    private val walletService = WalletService(walletRepo)
    private val settings = AppSettingsProvider.forTest(
        AppSettings(bankAccountNumber = "", fioToken = "", senderEmail = "", gmailAppPassword = "", senderDisplayName = "")
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
            price = 170.0,
            capacity = 10,
            isPublished = true,
            isDropIn = true,
        )
    }

    private suspend fun setup() {
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Angličtina",
                description = "",
                price = 600.0,
                capacity = 10,
                startDate = LocalDate(2099, 12, 1),
                endDate = LocalDate(2099, 12, 4),
                lessonCount = lessons.size,
                isPublished = true,
                lessonRefundAmount = 150.0,
            )
        )
        lessons.forEach { instanceRepo.create(it) }
    }

    private suspend fun reservation(
        name: String,
        reference: Reference,
        paidAmount: Double,
        paymentType: PaymentType = PaymentType.BANK_TRANSFER,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = reference,
            contactName = name,
            contactEmail = "${name.lowercase()}@example.com",
            seatCount = 1,
            totalPrice = paidAmount.coerceAtLeast(170.0),
            paidAmount = paidAmount,
            status = if (paidAmount > 0.0) Reservation.Status.CONFIRMED else Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = paymentType,
        )
    )

    private suspend fun enrollee() = reservation("Aneta", Reference.Series(seriesId), paidAmount = 600.0)
    private suspend fun paidDropIn() = reservation("Patrik", Reference.Instance(lessons[0].id), paidAmount = 170.0)
    private suspend fun onSiteDropIn() =
        reservation("Anna", Reference.Instance(lessons[0].id), paidAmount = 0.0, paymentType = PaymentType.ON_SITE)

    private suspend fun balance(res: Reservation) = walletService.findForReservation(res)?.balance ?: 0.0

    @Test
    fun `preview lists everyone who loses the lesson with the credit they would get`() = runBlocking {
        setup()
        val aneta = enrollee()
        val patrik = paidDropIn()
        val anna = onSiteDropIn()

        val rows = admin.getCancellationRefunds(lessons[0].id).getOrNull()!!.associateBy { it.reservationId }

        assertEquals(setOf(aneta.id, patrik.id, anna.id), rows.keys)
        assertEquals(150.0, rows.getValue(aneta.id).refundAmount, "zapsaný na kurz: sazba za lekci")
        assertTrue(rows.getValue(aneta.id).fromSeries)
        assertEquals(170.0, rows.getValue(patrik.id).refundAmount, "jednotlivá lekce: celá zaplacená částka")
        assertEquals(0.0, rows.getValue(anna.id).refundAmount, "platba na místě: není co vracet")
    }

    @Test
    fun `preview leaves out enrollees who already opted out of the lesson`() = runBlocking {
        setup()
        val aneta = enrollee()
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(), reservationId = aneta.id, instanceId = lessons[0].id,
                optedOutAt = Clock.System.now(), isLateCancellation = false, refundedAmount = 150.0,
            )
        )

        val rows = admin.getCancellationRefunds(lessons[0].id).getOrNull()!!

        assertTrue(rows.none { it.reservationId == aneta.id })
    }

    @Test
    fun `cancelling a lesson from the course skips credit only for the excluded drop-in`() = runBlocking {
        setup()
        val aneta = enrollee()
        val patrik = paidDropIn()

        val result = admin.cancelSeriesLesson(lessons[0].id, noRefundFor = setOf(patrik.id))

        assertTrue(result.isRight(), "zrušení musí projít, dostal: $result")
        assertEquals(150.0, balance(aneta), "zapsaná na kurz dostane kredit za lekci")
        assertEquals(0.0, balance(patrik), "vyloučený nedostane nic — peníze mu admin vrátí převodem")
        assertNull(walletService.findForReservation(patrik), "bez kreditu se mu nezakládá ani peněženka")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(patrik.id)?.status, "rezervace se zruší i tak")
    }

    @Test
    fun `cancelling a lesson from its detail skips credit only for the excluded enrollee`() = runBlocking {
        setup()
        val aneta = enrollee()
        val patrik = paidDropIn()

        admin.cancelEventInstance(lessons[0].id, refund = true, noRefundFor = setOf(aneta.id))

        assertEquals(0.0, balance(aneta))
        assertEquals(170.0, balance(patrik))
    }

    @Test
    fun `without exclusions everyone is credited as before`() = runBlocking {
        setup()
        val aneta = enrollee()
        val patrik = paidDropIn()

        admin.cancelSeriesLesson(lessons[0].id)

        assertEquals(150.0, balance(aneta))
        assertEquals(170.0, balance(patrik))
    }
}
