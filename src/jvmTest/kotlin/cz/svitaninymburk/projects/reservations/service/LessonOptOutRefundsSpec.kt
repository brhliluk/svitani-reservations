package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
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
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Kredit za lekci patří každé včasné omluvence právě jednou — bez ohledu na to,
 * jestli peníze přišly před omluvenkou, nebo po ní, a bez ohledu na to, co se
 * s rezervací děje potom (storno celé rezervace, vzetí omluvenky zpět).
 */
class LessonOptOutRefundsSpec {

    private val email = "host@test.com"

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletRepo = InMemoryWalletRepository()
    private val walletService = WalletService(walletRepo)
    private val settings = AppSettingsProvider.forTest(AppSettings(
        bankAccountNumber = "", fioToken = "", senderEmail = "",
        gmailAppPassword = "", senderDisplayName = "",
    ))
    private val refundService = RefundService(walletService, ConsoleEmailService(), settings)
    private val refunds = LessonOptOutRefunds(optOutRepo, seriesRepo, walletService, refundService)

    private val series = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 1000.0,
        capacity = 10,
        startDate = LocalDate(2099, 12, 1),
        endDate = LocalDate(2099, 12, 31),
        lessonCount = 4,
        lessonRefundAmount = 150.0,
    )
    private val lessons = (1..3).map { day ->
        EventInstance(
            id = Uuid.random(),
            definitionId = series.definitionId,
            seriesId = series.id,
            title = "Lekce",
            description = "",
            startDateTime = LocalDateTime(2099, 12, day * 7, 10, 0),
            endDateTime = LocalDateTime(2099, 12, day * 7, 11, 0),
            price = 100.0,
            capacity = 10,
        )
    }

    private val reservationService = ReservationService(
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
        refundService = refundService,
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        lessonOptOutRefunds = refunds,
    )

    private suspend fun enroll(paidAmount: Double): Reservation {
        seriesRepo.create(series)
        lessons.forEach { instanceRepo.create(it) }
        return reservationRepo.save(
            Reservation(
                id = Uuid.random(),
                reference = Reference.Series(series.id),
                contactName = "Host",
                contactEmail = email,
                seatCount = 1,
                totalPrice = 1000.0,
                paidAmount = paidAmount,
                status = if (paidAmount >= 1000.0) Reservation.Status.CONFIRMED else Reservation.Status.PENDING_PAYMENT,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )
    }

    private suspend fun balance(): Double = walletRepo.findAnonymousByEmail(email)?.balance ?: 0.0
    private suspend fun refundedFor(reservation: Reservation, lesson: EventInstance): Double? =
        optOutRepo.findByReservationAndInstance(reservation.id, lesson.id)?.refundedAmount

    @Test
    fun `omluvenka pred zaplacenim dostane kredit, jakmile admin oznaci platbu`() = runBlocking {
        val reservation = enroll(paidAmount = 0.0)

        val optOut = reservationService.cancelReservation(reservation.id, lessons[0].id)
        assertEquals(null, optOut.getOrNull()?.walletCreditAmount, "nezaplaceno — teď ještě nic")
        assertEquals(0.0, refundedFor(reservation, lessons[0]))

        assertTrue(admin.markReservationAsPaid(reservation.id).isRight())

        assertEquals(150.0, balance(), "kredit dorazí s platbou")
        assertEquals(150.0, refundedFor(reservation, lessons[0]))
        assertEquals(150.0, walletService.refundedForLessonOptOuts(reservation.id))
    }

    @Test
    fun `dorovnani je idempotentni`() = runBlocking {
        val reservation = enroll(paidAmount = 0.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)
        val paid = reservationRepo.save(reservation.copy(paidAmount = 1000.0))

        assertEquals(150.0, refunds.settleAfterPayment(paid))
        assertEquals(0.0, refunds.settleAfterPayment(paid), "druhé volání nesmí připsat znovu")
        assertEquals(150.0, balance())
    }

    @Test
    fun `nedoplatek dorovna jen do vyse toho, co prislo`() = runBlocking {
        val reservation = enroll(paidAmount = 0.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)

        assertEquals(100.0, refunds.settleAfterPayment(reservationRepo.save(reservation.copy(paidAmount = 100.0))))
        assertEquals(50.0, refunds.settleAfterPayment(reservationRepo.save(reservation.copy(paidAmount = 1000.0))))
        assertEquals(150.0, refundedFor(reservation, lessons[0]))
        assertEquals(150.0, balance())
    }

    @Test
    fun `pozdni omluvenka se nedoplaci ani po zaplaceni`() = runBlocking {
        val reservation = enroll(paidAmount = 0.0)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(), reservationId = reservation.id, instanceId = lessons[0].id,
                optedOutAt = Clock.System.now(), isLateCancellation = true, refundedAmount = 0.0,
            )
        )

        assertEquals(0.0, refunds.settleAfterPayment(reservationRepo.save(reservation.copy(paidAmount = 1000.0))))
    }

    @Test
    fun `omluvenka s neznamou castkou se nedoplaci`() = runBlocking {
        // Historický řádek, u kterého backfill nedohledal, kolik dostal — mohl dostat všechno.
        val reservation = enroll(paidAmount = 1000.0)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(), reservationId = reservation.id, instanceId = lessons[0].id,
                optedOutAt = Clock.System.now(), isLateCancellation = false, refundedAmount = null,
            )
        )

        assertEquals(0.0, refunds.settleAfterPayment(reservation))
    }

    @Test
    fun `storno cele rezervace nevraci podruhe to, co uz odeslo za omluvenku`() = runBlocking {
        val reservation = enroll(paidAmount = 1000.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)
        assertEquals(150.0, balance())

        val result = reservationService.cancelReservation(reservation.id, instanceId = null)

        assertEquals(850.0, assertNotNull(result.getOrNull()).walletCreditAmount)
        // Storno bez kódu zakládá hostovi novou peněženku, proto součet transakcí rezervace, ne zůstatek.
        val vraceno = walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.LESSON_OPT_OUT_REFUND) +
            walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.CANCELLATION_REFUND)
        assertEquals(1000.0, vraceno, "dohromady se vrátí přesně to, co bylo zaplaceno")
    }

    @Test
    fun `detail rezervace slibuje jen to, co storno opravdu vrati`() = runBlocking {
        val reservation = enroll(paidAmount = 1000.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)

        assertEquals(850.0, reservationService.getDetail(reservation.id).getOrNull()?.refundableAmount)
    }

    @Test
    fun `vzeti zpet neproplacene omluvenky nestrhne kredit za jinou`() = runBlocking {
        // První omluvenka ještě z nezaplaceného kurzu; platba pak přišla dřív,
        // než existovalo dorovnání, takže za ni nic neodešlo.
        val reservation = enroll(paidAmount = 0.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)
        reservationRepo.save(reservation.copy(paidAmount = 1000.0, status = Reservation.Status.CONFIRMED))
        optOutRepo.updateRefundedAmount(optOutRepo.findByReservationAndInstance(reservation.id, lessons[0].id)!!.id, 0.0)
        reservationService.cancelReservation(reservation.id, lessons[1].id)
        assertEquals(150.0, balance())

        assertTrue(admin.revokeLessonOptOut(reservation.id, lessons[0].id).isRight())

        assertEquals(150.0, balance(), "kredit za druhou lekci musí zůstat")
        assertEquals(
            150.0,
            walletRepo.getTransactions(walletRepo.findAnonymousByEmail(email)!!.id)
                .filter { it.reason == WalletTransactionReason.LESSON_OPT_OUT_REFUND }
                .sumOf { it.amount },
        )
    }

    @Test
    fun `vzeti zpet proplacene omluvenky strhne presne jeji castku`() = runBlocking {
        val reservation = enroll(paidAmount = 1000.0)
        reservationService.cancelReservation(reservation.id, lessons[0].id)
        reservationService.cancelReservation(reservation.id, lessons[1].id)
        assertEquals(300.0, balance())

        assertTrue(admin.revokeLessonOptOut(reservation.id, lessons[0].id).isRight())

        assertEquals(150.0, balance())
    }
}
