package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
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
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Zvednutá kapacita uvolní místa stejně jako zrušená rezervace — náhradník se na ně
 * musí posunout sám. Bez toho svítí v pořadníku i u lekce, kde je volno (reálný případ
 * z produkce: kapacita 10 → 11 při 10 obsazených a náhradnice zůstala čekat).
 */
class WaitlistPromotionOnCapacityIncreaseSpec {

    /** Počítá promotion e-maily; zbytek rozhraní obslouží [ConsoleEmailService]. */
    private class CountingEmailService(
        private val delegate: EmailService = ConsoleEmailService(),
    ) : EmailService by delegate {
        val promotions = mutableListOf<String>()

        override suspend fun sendWaitlistPromotion(
            toEmail: String,
            reservation: Reservation,
            target: ReservationTarget,
            bankAccount: String,
            qrCodeImage: ByteArray?,
            icalBytes: ByteArray,
        ): Either<EmailError.SendWaitlistPromotion, Unit> {
            promotions += toEmail
            return delegate.sendWaitlistPromotion(toEmail, reservation, target, bankAccount, qrCodeImage, icalBytes)
        }
    }

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val emailService = CountingEmailService()

    private fun service() = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        refundService = RefundService(
            walletService = WalletService(InMemoryWalletRepository()),
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(
                    bankAccountNumber = "", fioToken = "", senderEmail = "",
                    gmailAppPassword = "", senderDisplayName = "",
                )
            ),
        ),
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo, emailService),
    )

    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")

    private fun lesson(
        capacity: Int = 10,
        occupiedSpots: Int = 10,
        occupiedWaitlist: Int = 1,
        isCancelled: Boolean = false,
        startDateTime: LocalDateTime = LocalDateTime(2099, 12, 1, 10, 0),
    ) = EventInstance(
        id = lessonId,
        definitionId = Uuid.random(),
        title = "Odborné cvičení",
        description = "",
        startDateTime = startDateTime,
        endDateTime = startDateTime,
        price = 100.0,
        capacity = capacity,
        occupiedSpots = occupiedSpots,
        waitlistCapacity = 10,
        occupiedWaitlist = occupiedWaitlist,
        isCancelled = isCancelled,
        isPublished = true,
    )

    private fun series(
        capacity: Int = 10,
        occupiedSpots: Int = 10,
        occupiedWaitlist: Int = 1,
    ) = EventSeries(
        id = seriesId,
        definitionId = Uuid.random(),
        title = "Kurz plavání",
        description = "",
        price = 1400.0,
        capacity = capacity,
        occupiedSpots = occupiedSpots,
        waitlistCapacity = 10,
        occupiedWaitlist = occupiedWaitlist,
        isPublished = true,
        startDate = LocalDate(2099, 9, 1),
        endDate = LocalDate(2099, 12, 1),
        lessonCount = 10,
    )

    private fun waitlisted(
        name: String = "Jana Smolejová",
        email: String = "janca@test.com",
        reference: Reference = Reference.Instance(lessonId),
        totalPrice: Double = 100.0,
        seatCount: Int = 1,
        createdAt: kotlin.time.Instant = Clock.System.now(),
    ) = Reservation(
        id = Uuid.random(),
        reference = reference,
        contactName = name,
        contactEmail = email,
        seatCount = seatCount,
        totalPrice = totalPrice,
        status = Reservation.Status.WAITLISTED,
        createdAt = createdAt,
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
    )

    private fun updateLesson(instance: EventInstance, capacity: Int) = UpdateEventInstanceRequest(
        title = instance.title,
        description = instance.description,
        startDateTime = instance.startDateTime,
        endDateTime = instance.endDateTime,
        price = instance.price,
        capacity = capacity,
        waitlistCapacity = instance.waitlistCapacity,
        allowedPaymentTypes = instance.allowedPaymentTypes,
        customFields = instance.customFields,
    )

    private fun updateSeries(existing: EventSeries, capacity: Int) = UpdateEventSeriesRequest(
        title = existing.title,
        description = existing.description,
        price = existing.price,
        capacity = capacity,
        waitlistCapacity = existing.waitlistCapacity,
        allowedPaymentTypes = existing.allowedPaymentTypes,
        customFields = existing.customFields,
        lessonPrice = existing.lessonPrice,
    )

    @Test
    fun `increased lesson capacity promotes the waitlisted person into reservations`() = runBlocking {
        val instance = lesson()
        instanceRepo.create(instance)
        val substitute = waitlisted()
        reservationRepo.save(substitute)

        val result = service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))
        assertTrue(result.isRight(), "Expected Right but got $result")

        val promoted = reservationRepo.findById(substitute.id)!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, promoted.status)
        assertNotNull(promoted.variableSymbol)

        val updated = instanceRepo.get(lessonId)!!
        assertEquals(11, updated.occupiedSpots)
        assertEquals(0, updated.occupiedWaitlist)
        assertEquals(listOf(substitute.contactEmail), emailService.promotions)
    }

    @Test
    fun `waitlisted person for a free event is promoted directly as confirmed`() = runBlocking {
        val instance = lesson()
        instanceRepo.create(instance)
        val substitute = waitlisted(totalPrice = 0.0)
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        val promoted = reservationRepo.findById(substitute.id)!!
        assertEquals(Reservation.Status.CONFIRMED, promoted.status)
        assertEquals(PaymentType.FREE, promoted.paymentType)
    }

    @Test
    fun `increased course capacity promotes the waitlisted person from the course waitlist`() = runBlocking {
        val existing = series()
        seriesRepo.create(existing)
        val substitute = waitlisted(reference = Reference.Series(seriesId), totalPrice = 1400.0)
        reservationRepo.save(substitute)

        val result = service().updateEventSeries(seriesId, updateSeries(existing, capacity = 11))
        assertTrue(result.isRight(), "Expected Right but got $result")

        val promoted = reservationRepo.findById(substitute.id)!!
        assertEquals(Reservation.Status.PENDING_PAYMENT, promoted.status)

        val updated = seriesRepo.get(seriesId)!!
        assertEquals(11, updated.occupiedSpots)
        assertEquals(0, updated.occupiedWaitlist)
        assertEquals(listOf(substitute.contactEmail), emailService.promotions)
    }

    @Test
    fun `one free seat promotes only the first on the waitlist`() = runBlocking {
        val instance = lesson(occupiedWaitlist = 2)
        instanceRepo.create(instance)
        val now = Clock.System.now()
        val first = waitlisted(name = "První", email = "prvni@test.com", createdAt = now - 2.days)
        val second = waitlisted(name = "Druhý", email = "druhy@test.com", createdAt = now)
        reservationRepo.save(second)
        reservationRepo.save(first)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        assertEquals(Reservation.Status.PENDING_PAYMENT, reservationRepo.findById(first.id)!!.status)
        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(second.id)!!.status)

        val updated = instanceRepo.get(lessonId)!!
        assertEquals(11, updated.occupiedSpots)
        assertEquals(1, updated.occupiedWaitlist)
        assertEquals(listOf(first.contactEmail), emailService.promotions)
    }

    @Test
    fun `unchanged capacity promotes nobody`() = runBlocking {
        val instance = lesson()
        instanceRepo.create(instance)
        val substitute = waitlisted()
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 10))

        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(substitute.id)!!.status)
        assertNull(reservationRepo.findById(substitute.id)!!.variableSymbol)
        val updated = instanceRepo.get(lessonId)!!
        assertEquals(10, updated.occupiedSpots)
        assertEquals(1, updated.occupiedWaitlist)
        assertTrue(emailService.promotions.isEmpty())
    }

    @Test
    fun `reduced capacity promotes nobody`() = runBlocking {
        val instance = lesson(capacity = 12, occupiedSpots = 10)
        instanceRepo.create(instance)
        val substitute = waitlisted()
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(substitute.id)!!.status)
        assertTrue(emailService.promotions.isEmpty())
    }

    @Test
    fun `cancelled lesson promotes nobody`() = runBlocking {
        val instance = lesson(isCancelled = true)
        instanceRepo.create(instance)
        val substitute = waitlisted()
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(substitute.id)!!.status)
        assertTrue(emailService.promotions.isEmpty())
    }

    @Test
    fun `past lesson promotes nobody`() = runBlocking {
        val yesterday = (Clock.System.now() - 1.days).toLocalDateTime(TimeZone.of("Europe/Prague"))
        val instance = lesson(startDateTime = yesterday)
        instanceRepo.create(instance)
        val substitute = waitlisted()
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(substitute.id)!!.status)
        assertTrue(emailService.promotions.isEmpty())
    }

    @Test
    fun `waitlisted person for more seats than are free stays on the waitlist`() = runBlocking {
        val instance = lesson()
        instanceRepo.create(instance)
        val substitute = waitlisted(seatCount = 2)
        reservationRepo.save(substitute)

        service().updateEventInstance(lessonId, updateLesson(instance, capacity = 11))

        assertEquals(Reservation.Status.WAITLISTED, reservationRepo.findById(substitute.id)!!.status)
        val updated = instanceRepo.get(lessonId)!!
        assertEquals(10, updated.occupiedSpots)
        assertEquals(1, updated.occupiedWaitlist)
        assertTrue(emailService.promotions.isEmpty())
    }
}
