package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Admin ruší účast po uzávěrce storna (18:00 den předem) a vrací plný kredit —
 * provoz to dělá, když zákazník ráno před lekcí volá, že se stalo něco, za co nemůže.
 * Zákazník uzávěrku přepsat nesmí, ani když příznak pošle.
 */
class AdminLateCancellationSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val ownerId = Uuid.parse("00000000-0000-0000-0000-0000000000b2")
    private val adminId = Uuid.parse("00000000-0000-0000-0000-0000000000b3")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val walletService = WalletService(InMemoryWalletRepository())

    private inner class TestService(
        private val caller: Uuid?,
        private val admin: Boolean,
    ) : ReservationService(
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
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(bankAccountNumber = "", fioToken = "", senderEmail = "", gmailAppPassword = "", senderDisplayName = "")
        ),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = caller
        override suspend fun isAdminCaller(): Boolean = admin
    }

    private val asAdmin = TestService(caller = adminId, admin = true)
    private val asOwner = TestService(caller = ownerId, admin = false)

    /** Za hodinu: uzávěrka (18:00 den předem) je vždy pryč, lekce ale ještě nezačala. */
    private fun inOneHour(): LocalDateTime = (Clock.System.now() + 1.hours).toLocalDateTime(APP_TIMEZONE)

    private suspend fun lesson(start: LocalDateTime, seriesId: Uuid? = this.seriesId): EventInstance = instanceRepo.create(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            seriesId = seriesId,
            title = "Lekce",
            description = "",
            startDateTime = start,
            endDateTime = start,
            price = 100.0,
            capacity = 10,
            occupiedSpots = if (seriesId == null) 1 else 0,
            isPublished = true,
        )
    )

    /** Kurz za 600 Kč, sazba 150 Kč za lekci; první lekce za hodinu. */
    private suspend fun courseStartingSoon(): Pair<EventInstance, Reservation> {
        val first = lesson(inOneHour())
        (1..3).forEach { lesson(LocalDateTime(2099, 12, it, 10, 0)) }
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Kurz",
                description = "",
                price = 600.0,
                capacity = 10,
                startDate = first.startDateTime.date,
                endDate = LocalDate(2099, 12, 3),
                lessonCount = 4,
                isPublished = true,
                lessonRefundAmount = 150.0,
            )
        )
        val enrollment = reservationRepo.save(
            reservation(Reference.Series(seriesId), price = 600.0).copy(lessonShare = 150.0)
        )
        return first to enrollment
    }

    private fun reservation(reference: Reference, price: Double) = Reservation(
        id = Uuid.random(),
        reference = reference,
        registeredUserId = ownerId,
        contactName = "Jana Rodičová",
        contactEmail = "jana@example.com",
        seatCount = 1,
        totalPrice = price,
        paidAmount = price,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
    )

    @Test
    fun `admin opts a course participant out after the deadline with a full lesson credit`() = runBlocking {
        val (lesson, enrollment) = courseStartingSoon()

        val result = asAdmin.cancelReservation(enrollment.id, instanceId = lesson.id, ignoreDeadline = true)

        assertTrue(result.isRight(), "admin musí projít, dostal: $result")
        assertEquals(150.0, result.getOrNull()?.walletCreditAmount)
        val optOut = optOutRepo.findByReservationAndInstance(enrollment.id, lesson.id)
        assertEquals(false, optOut?.isLateCancellation, "pro kredit i maily je to včasná omluvenka")
        assertEquals(150.0, optOut?.refundedAmount, "vratka je svázaná s omluvenkou — vzetí zpět ji strhne")
        assertEquals(150.0, walletService.refundedForLessonOptOuts(enrollment.id), "počítá se do stropu vratek")
    }

    @Test
    fun `admin without the override keeps the deadline for a lesson opt-out`() = runBlocking {
        val (lesson, enrollment) = courseStartingSoon()

        val result = asAdmin.cancelReservation(enrollment.id, instanceId = lesson.id)

        assertTrue(result.isRight(), "omluvenka musí projít, dostal: $result")
        assertNull(result.getOrNull()?.walletCreditAmount)
        assertEquals(true, optOutRepo.findByReservationAndInstance(enrollment.id, lesson.id)?.isLateCancellation)
    }

    @Test
    fun `customer cannot override the deadline for a lesson opt-out`() = runBlocking {
        val (lesson, enrollment) = courseStartingSoon()

        val result = asOwner.cancelReservation(enrollment.id, instanceId = lesson.id, ignoreDeadline = true)

        assertTrue(result.isRight(), "omluvenka sama projít musí, dostal: $result")
        assertNull(result.getOrNull()?.walletCreditAmount, "zákazníkovi se příznak ignoruje")
        assertEquals(0.0, walletService.refundedForLessonOptOuts(enrollment.id))
        assertEquals(true, optOutRepo.findByReservationAndInstance(enrollment.id, lesson.id)?.isLateCancellation)
    }

    @Test
    fun `admin cancels a single event reservation after the deadline with a full refund`() = runBlocking {
        val event = lesson(inOneHour(), seriesId = null)
        val booking = reservationRepo.save(reservation(Reference.Instance(event.id), price = 100.0))

        val result = asAdmin.cancelReservation(booking.id, ignoreDeadline = true)

        assertTrue(result.isRight(), "storno musí projít, dostal: $result")
        assertEquals(100.0, result.getOrNull()?.walletCreditAmount)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(booking.id)?.status)
    }

    @Test
    fun `admin without the override refunds nothing after the deadline`() = runBlocking {
        val event = lesson(inOneHour(), seriesId = null)
        val booking = reservationRepo.save(reservation(Reference.Instance(event.id), price = 100.0))

        val result = asAdmin.cancelReservation(booking.id)

        assertTrue(result.isRight(), "storno musí projít, dostal: $result")
        assertNull(result.getOrNull()?.walletCreditAmount)
    }

    @Test
    fun `customer cannot override the deadline for a reservation`() = runBlocking {
        val event = lesson(inOneHour(), seriesId = null)
        val booking = reservationRepo.save(reservation(Reference.Instance(event.id), price = 100.0))

        val result = asOwner.cancelReservation(booking.id, ignoreDeadline = true)

        assertTrue(result.isRight(), "storno samo projít musí, dostal: $result")
        assertNull(result.getOrNull()?.walletCreditAmount, "zákazníkovi se příznak ignoruje")
    }
}
