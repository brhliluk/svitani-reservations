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

/**
 * Kurz, jehož první lekce už začala, se celý neruší. Rezervaci na něj nezruší
 * ani admin (zbývá omluvenka z jednotlivých lekcí) a zrušení kurzu zruší jen
 * lekce, které ještě nezačaly — zapsaní si rezervaci nechají a dostanou kredit
 * za zbývající lekce stejně, jako kdyby admin rušil lekci po lekci.
 */
class RunningCourseSpec {

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
    private val refundService = RefundService(walletService, ConsoleEmailService(), settings)

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

    private inner class AsAdmin : ReservationService(
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
    ) {
        override suspend fun isAdminCaller(): Boolean = true
    }

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

    // --- storno celé rezervace ---

    @Test
    fun `rezervaci na rozbehnuty kurz nezrusi ani admin`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        lesson(7.days)
        val reservation = enroll()

        val result = AsAdmin().cancelReservation(reservation.id)

        assertEquals(ReservationError.SeriesAlreadyStarted, result.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `admin zrusi rezervaci na kurz, ktery jeste nezacal`() = runBlocking {
        seriesRepo.create(series.copy(startDate = LocalDate(2099, 1, 1)))
        lesson(7.days)
        val reservation = enroll()

        assertTrue(AsAdmin().cancelReservation(reservation.id).isRight())
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `nahradnika jde z poradniku odebrat i u bezicho kurzu`() = runBlocking {
        seriesRepo.create(series.copy(occupiedWaitlist = 1))
        lesson((-7).days)
        lesson(7.days)
        val waitlisted = enroll(paidAmount = 0.0, status = Reservation.Status.WAITLISTED)

        assertTrue(AsAdmin().cancelReservation(waitlisted.id).isRight())
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(waitlisted.id)?.status)
    }

    // --- zrušení kurzu ---

    @Test
    fun `zruseni bezicho kurzu zrusi jen lekce, ktere jeste nezacaly`() = runBlocking {
        seriesRepo.create(series)
        val past = lesson((-7).days)
        val startedToday = lesson((-1).hours)
        val next = lesson(7.days)
        val later = lesson(14.days)
        enroll()

        assertTrue(admin.cancelEventSeries(series.id, refund = true).isRight())

        assertEquals(false, instanceRepo.get(past.id)?.isCancelled)
        assertEquals(false, instanceRepo.get(startedToday.id)?.isCancelled, "dnešní lekce už začala")
        assertEquals(true, instanceRepo.get(next.id)?.isCancelled)
        assertEquals(true, instanceRepo.get(later.id)?.isCancelled)
    }

    @Test
    fun `zapsany si rezervaci necha a dostane kredit za zbyvajici lekce`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        lesson(7.days)
        lesson(14.days)
        val reservation = enroll()

        admin.cancelEventSeries(series.id, refund = true)

        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
        assertEquals(300.0, lessonCredit(reservation), "2 zbývající lekce × 150")
        assertEquals(
            0.0,
            walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.CANCELLATION_REFUND),
            "celá rezervace se nevrací",
        )
    }

    @Test
    fun `za lekci, ze ktere se uz omluvil, podruhe nic nedostane`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        val omluvena = lesson(7.days)
        lesson(14.days)
        val reservation = enroll()
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(), reservationId = reservation.id, instanceId = omluvena.id,
                optedOutAt = Clock.System.now(), isLateCancellation = false, refundedAmount = 150.0,
            )
        )

        admin.cancelEventSeries(series.id, refund = true)

        assertEquals(150.0, lessonCredit(reservation), "jen za druhou zbývající lekci")
    }

    @Test
    fun `kredit za zbytek kurzu neprekroci zaplacenou castku`() = runBlocking {
        seriesRepo.create(series.copy(lessonRefundAmount = 400.0))
        lesson((-7).days)
        lesson(7.days)
        lesson(14.days)
        val reservation = enroll(paidAmount = 500.0)

        admin.cancelEventSeries(series.id, refund = true)

        assertEquals(500.0, lessonCredit(reservation))
    }

    @Test
    fun `bez vraceni kreditu zapsany nic nedostane`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        lesson(7.days)
        val reservation = enroll()

        admin.cancelEventSeries(series.id, refund = false)

        assertEquals(0.0, lessonCredit(reservation))
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
    }

    @Test
    fun `nahradnik v poradniku bezicho kurzu se pri zruseni kurzu odhlasi`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        lesson(7.days)
        val waitlisted = enroll(paidAmount = 0.0, status = Reservation.Status.WAITLISTED)

        admin.cancelEventSeries(series.id, refund = true)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(waitlisted.id)?.status)
    }

    @Test
    fun `zrusene lekce se zapisou do historie`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        val next = lesson(7.days)
        enroll()

        admin.cancelEventSeries(series.id, refund = true)

        val lessonCancelled = auditRepo.recordedEvents().filter { it.type == AuditEventType.LESSON_CANCELLED }
        assertEquals(listOf(next.id), lessonCancelled.map { it.instanceId })
    }

    @Test
    fun `kurz pred zacatkem se rusi jako dosud i s plnou vratkou`() = runBlocking {
        seriesRepo.create(series.copy(startDate = LocalDate(2099, 1, 1)))
        lesson(7.days)
        val reservation = enroll()

        admin.cancelEventSeries(series.id, refund = true)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        assertEquals(
            1000.0,
            walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.CANCELLATION_REFUND),
        )
    }

    @Test
    fun `detail kurzu adminovi rekne, ze kurz uz bezi`() = runBlocking {
        seriesRepo.create(series)
        lesson((-7).days)
        lesson(7.days)

        assertEquals(true, admin.getEventDetail(series.id, isSeries = true).getOrNull()?.isRunningCourse)
    }
}
