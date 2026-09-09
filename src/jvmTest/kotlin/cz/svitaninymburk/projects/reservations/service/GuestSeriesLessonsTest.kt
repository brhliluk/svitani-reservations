package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
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
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * getSeriesLessons je guest-callable — visí na rozhraní pod `optional = true`,
 * takže si autorizaci musí ohlídat sama a přesně stejně jako zápisová cesta
 * v cancelReservation.
 */
class GuestSeriesLessonsTest {

    private val testCallerId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private inner class TestReservationService(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        reservationRepo: InMemoryReservationRepository,
        optOutRepo: InMemorySeriesLessonOptOutRepository,
        private val callerId: Uuid?,
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
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = callerId
    }

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()

    private fun service(callerId: Uuid?) = TestReservationService(
        instanceRepo, seriesRepo, reservationRepo, optOutRepo, callerId,
    )

    private fun makeSeries(lessonRefundAmount: Double? = null) = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Test Series",
        description = "",
        price = 500.0,
        capacity = 10,
        occupiedSpots = 1,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 12, 31),
        lessonCount = 10,
        lessonRefundAmount = lessonRefundAmount,
    )

    private fun makeInstance(seriesId: Uuid, day: Int) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        seriesId = seriesId,
        title = "Test Lesson",
        description = "",
        startDateTime = LocalDateTime(2099, 12, day, 10, 0),
        endDateTime = LocalDateTime(2099, 12, day, 11, 0),
        price = 100.0,
        capacity = 10,
        occupiedSpots = 1,
    )

    private fun makeReservation(
        reference: Reference,
        registeredUserId: Uuid?,
        paidAmount: Double = 0.0,
        seatCount: Int = 1,
    ) = Reservation(
        id = Uuid.random(),
        reference = reference,
        registeredUserId = registeredUserId,
        contactName = "Host Testovaci",
        contactEmail = "host@test.com",
        seatCount = seatCount,
        totalPrice = 500.0,
        paidAmount = paidAmount,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
    )

    @Test
    fun `host dostane termíny kurzu i bez prihlaseni`() = runBlocking {
        val series = makeSeries(lessonRefundAmount = 120.0)
        seriesRepo.create(series)
        listOf(1, 8, 15).forEach { instanceRepo.create(makeInstance(series.id, it)) }
        val reservation = makeReservation(Reference.Series(series.id), null, paidAmount = 600.0, seatCount = 2)
        reservationRepo.save(reservation)

        val result = service(callerId = null).getSeriesLessons(reservation.id)

        assertTrue(result.isRight(), "host musí seznam dostat, dostal: $result")
        val view = result.getOrNull()!!
        assertEquals(3, view.lessons.size)
        assertTrue(view.isAnonymousReservation, "rezervace bez účtu se má označit")
        assertEquals(120.0, view.lessonRefundAmount)
        assertEquals(2, view.seatCount)
        assertEquals(600.0, view.paidAmount)
        assertEquals(0.0, view.alreadyRefunded, "zatím žádná omluvenka")
        Unit
    }

    @Test
    fun `anonymni volajici nedostane registrovanou rezervaci`() = runBlocking {
        val series = makeSeries()
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(series.id, 1))
        val reservation = makeReservation(Reference.Series(series.id), testCallerId)
        reservationRepo.save(reservation)

        val result = service(callerId = null).getSeriesLessons(reservation.id)

        assertTrue(result.isLeft(), "anonym nesmí vidět cizí registrovanou rezervaci, dostal: $result")
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        Unit
    }

    @Test
    fun `majitel svou registrovanou rezervaci vidi`() = runBlocking {
        val series = makeSeries()
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(series.id, 1))
        val reservation = makeReservation(Reference.Series(series.id), testCallerId)
        reservationRepo.save(reservation)

        val result = service(callerId = testCallerId).getSeriesLessons(reservation.id)

        assertTrue(result.isRight(), "majitel musí projít, dostal: $result")
        assertFalse(result.getOrNull()!!.isAnonymousReservation)
        Unit
    }

    @Test
    fun `uz odhlasene lekce jsou oznacene`() = runBlocking {
        val series = makeSeries(lessonRefundAmount = 100.0)
        seriesRepo.create(series)
        val first = makeInstance(series.id, 1)
        val second = makeInstance(series.id, 8)
        instanceRepo.create(first)
        instanceRepo.create(second)
        val reservation = makeReservation(Reference.Series(series.id), null, paidAmount = 500.0)
        reservationRepo.save(reservation)

        optOutRepo.save(
            SeriesLessonOptOut(Uuid.random(), reservation.id, first.id, Clock.System.now(), isLateCancellation = false)
        )
        // Pozdní omluvenka kredit nevrací, takže se do alreadyRefunded počítat nesmí.
        optOutRepo.save(
            SeriesLessonOptOut(Uuid.random(), reservation.id, second.id, Clock.System.now(), isLateCancellation = true)
        )

        val view = service(callerId = null).getSeriesLessons(reservation.id).getOrNull()!!

        val firstItem = view.lessons.single { it.instanceId == first.id }
        val secondItem = view.lessons.single { it.instanceId == second.id }
        assertTrue(firstItem.isOptedOut)
        assertFalse(firstItem.isLateCancellation)
        assertTrue(secondItem.isOptedOut)
        assertTrue(secondItem.isLateCancellation)
        // alreadyRefunded čte skutečné pohyby v peněžence; tady se jen předsadily
        // řádky omluvenek, žádný kredit nikdo nevyplatil.
        assertEquals(0.0, view.alreadyRefunded)
        Unit
    }

    @Test
    fun `rezervace na jednorazovou akci zadne lekce nema`() = runBlocking {
        val reservation = makeReservation(Reference.Instance(Uuid.random()), null)
        reservationRepo.save(reservation)

        val result = service(callerId = null).getSeriesLessons(reservation.id)

        assertTrue(result.isLeft(), "jednorázová akce nemá termíny kurzu, dostal: $result")
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        Unit
    }

    @Test
    fun `neexistujici rezervace vrati NotFound`() = runBlocking {
        val result = service(callerId = null).getSeriesLessons(Uuid.random())

        assertTrue(result.isLeft())
        result.onLeft { assertEquals(ReservationError.ReservationNotFound, it) }
        assertNotNull(result.leftOrNull())
        Unit
    }
}
