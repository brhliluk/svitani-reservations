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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Omluvenku z lekce si host vzít zpět nemůže — překlik opraví jedině admin.
 * Účastník se vrátí do lekce a kredit, který za omluvenku dostal, jde z peněženky
 * zpátky. Strop je to, co rezervace opravdu dostala: sazba kurzu se mezitím mohla
 * změnit a pozdní omluvenka nedostala nic.
 */
class AdminRevokeLessonOptOutSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletRepo = InMemoryWalletRepository()
    private val walletService = WalletService(walletRepo)

    private fun service() = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = walletService,
        refundService = RefundService(
            walletService = walletService,
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(
                    bankAccountNumber = "", fioToken = "", senderEmail = "",
                    gmailAppPassword = "", senderDisplayName = "",
                )
            ),
        ),
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    private suspend fun prepare(
        capacity: Int = 10,
        occupiedSpots: Int = 0,
        lessonRefundAmount: Double = 150.0,
    ) {
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = definitionId,
                title = "Kurz",
                description = "",
                price = 1000.0,
                capacity = capacity,
                startDate = LocalDate(2030, 1, 1),
                endDate = LocalDate(2030, 3, 1),
                lessonCount = 8,
                lessonRefundAmount = lessonRefundAmount,
            )
        )
        instanceRepo.create(
            EventInstance(
                id = lessonId,
                definitionId = definitionId,
                seriesId = seriesId,
                title = "Lekce",
                description = "",
                startDateTime = LocalDateTime(2030, 1, 8, 10, 0),
                endDateTime = LocalDateTime(2030, 1, 8, 11, 0),
                price = 100.0,
                capacity = capacity,
                occupiedSpots = occupiedSpots,
            )
        )
    }

    private suspend fun enrollee(seats: Int = 1): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.parse("00000000-0000-0000-0000-0000000000d1"),
            reference = Reference.Series(seriesId),
            contactName = "Omluveny",
            contactEmail = "omluveny@example.com",
            seatCount = seats,
            totalPrice = 1000.0,
            paidAmount = 1000.0,
            status = Reservation.Status.CONFIRMED,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    /** Omluvenka tak, jak ji zapsal `cancelReservation`: záznam + kredit v peněžence. */
    private suspend fun optOut(reservation: Reservation, isLate: Boolean, refunded: Double) {
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = reservation.id,
                instanceId = lessonId,
                optedOutAt = Clock.System.now(),
                isLateCancellation = isLate,
                // Pozdní omluvenka kredit nedostane — co je v peněžence, patří jiné.
                refundedAmount = if (isLate) 0.0 else refunded,
            )
        )
        if (refunded > 0.0) {
            val wallet = walletService
                .resolveAnonymousWallet(code = null, contactEmail = reservation.contactEmail, force = true)
                .getOrNull()!!
            walletService.credit(
                wallet.id, refunded, WalletTransactionReason.LESSON_OPT_OUT_REFUND, reservation.id
            )
        }
    }

    private suspend fun balance(): Double =
        walletRepo.findAnonymousByEmail("omluveny@example.com")?.balance ?: 0.0

    @Test
    fun `returning to the lesson deletes the opt-out and deducts the credit`() = runBlocking {
        prepare()
        val reservation = enrollee()
        optOut(reservation, isLate = false, refunded = 150.0)

        val result = service().revokeLessonOptOut(reservation.id, lessonId)

        assertTrue(result.isRight(), "vrácení do lekce musí projít, dostal: $result")
        assertNull(
            optOutRepo.findByReservationAndInstance(reservation.id, lessonId),
            "omluvenka má zmizet, jinak účastník v lekci pořád chybí",
        )
        assertEquals(0.0, balance(), "kredit za omluvenku se vrací zpátky")
        assertEquals(
            0.0,
            walletService.refundedForLessonOptOuts(reservation.id),
            "strop na další omluvenky musí klesnout spolu s vráceným kreditem",
        )
    }

    @Test
    fun `multiple seats deduct the rate for each of them`() = runBlocking {
        prepare()
        val reservation = enrollee(seats = 2)
        optOut(reservation, isLate = false, refunded = 300.0)

        service().revokeLessonOptOut(reservation.id, lessonId)

        assertEquals(0.0, balance(), "vrací se sazba × počet míst, stejně jako se připisovala")
    }

    @Test
    fun `late opt-out received no credit, so nothing is deducted`() = runBlocking {
        prepare()
        val reservation = enrollee()
        // Pozdní omluvenka kredit nedostala; v peněžence je z jiné, včasné omluvenky.
        optOut(reservation, isLate = true, refunded = 150.0)

        service().revokeLessonOptOut(reservation.id, lessonId)

        assertEquals(150.0, balance(), "za pozdní omluvenku se nic nevracelo, není co brát zpět")
    }

    @Test
    fun `deducts at most what the reservation actually received`() = runBlocking {
        // Sazba kurzu se po omluvence zvedla — vrátit se smí jen vyplacených 100.
        prepare(lessonRefundAmount = 500.0)
        val reservation = enrollee()
        optOut(reservation, isLate = false, refunded = 100.0)

        service().revokeLessonOptOut(reservation.id, lessonId)

        assertEquals(0.0, balance(), "peněženka nesmí jít do minusu za cizí kredit")
        assertEquals(
            0.0,
            walletService.refundedForLessonOptOuts(reservation.id),
            "vrací se přesně to, co bylo vyplaceno",
        )
    }

    @Test
    fun `full lesson rejects the return`() = runBlocking {
        prepare(capacity = 3, occupiedSpots = 3)
        val reservation = enrollee()
        optOut(reservation, isLate = false, refunded = 150.0)

        val result = service().revokeLessonOptOut(reservation.id, lessonId)

        assertEquals(
            AdminError.RevokeOptOut.LessonFull,
            result.leftOrNull(),
            "místo mezitím zabral někdo jiný, přesadit se nedá",
        )
        assertNotNull(
            optOutRepo.findByReservationAndInstance(reservation.id, lessonId),
            "když se vrácení nepovede, omluvenka musí zůstat",
        )
        assertEquals(150.0, balance(), "a kredit s ní")
    }

    @Test
    fun `without an opt-out there is nothing to revoke`() = runBlocking {
        prepare()
        val reservation = enrollee()

        val result = service().revokeLessonOptOut(reservation.id, lessonId)

        assertEquals(AdminError.RevokeOptOut.OptOutNotFound, result.leftOrNull())
    }

    @Test
    fun `opted-out participants are visible in the lesson detail`() = runBlocking {
        prepare()
        val reservation = enrollee()
        optOut(reservation, isLate = false, refunded = 150.0)

        val detail = service().getEventDetail(lessonId, isSeries = false).getOrNull()!!

        assertTrue(detail.participants.isEmpty(), "omluvený místo nedrží")
        assertEquals(
            listOf("Omluveny"),
            detail.optedOut.map { it.contactName },
            "bez seznamu omluvených nejde omluvenku vzít zpět",
        )
    }
}
