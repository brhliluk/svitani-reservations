package cz.svitaninymburk.projects.reservations.slot

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.error.DuplicateScope
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ConsoleEmailService
import cz.svitaninymburk.projects.reservations.service.PaymentTrigger
import cz.svitaninymburk.projects.reservations.service.QrCodeGeneratorService
import cz.svitaninymburk.projects.reservations.service.ReservationService
import cz.svitaninymburk.projects.reservations.service.ReservationTargetResolver
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.slot.repository.InMemoryAvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemoryAvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemorySlotServiceRepository
import cz.svitaninymburk.projects.reservations.slot.repository.InMemorySlotStore
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Rezervace úseku: každé validační pravidlo ze zadání (docs/hlidani/zadani.md, „Validace
 * v reserveSlot“) a souběh o poslední místa.
 *
 * „Teď“ je pondělí 19. 10. 2026 8:00; služba má okna v út a čt 8–12, krok 30 min,
 * 1–4 h, kapacitu 4 děti, max. 3 na rezervaci, uzávěrku 2 h a horizont 14 dní.
 */
class SlotBookingServiceSpec {

    private val monday8 = LocalDateTime(2026, 10, 19, 8, 0)
    private val tuesday = LocalDate(2026, 10, 20)
    private val wednesday = LocalDate(2026, 10, 21)

    private val store = InMemorySlotStore()
    private val serviceRepo = InMemorySlotServiceRepository(store)
    private val ruleRepo = InMemoryAvailabilityRuleRepository(store)
    private val exceptionRepo = InMemoryAvailabilityExceptionRepository(store)
    private val reservationRepo = InMemoryReservationRepository()

    private val service = SlotService(
        id = Uuid.random(),
        title = "Hlídání",
        description = "",
        step = 30.minutes,
        minDuration = 1.hours,
        maxDuration = 4.hours,
        pricePerHour = 150.0,
        capacity = 4,
        maxSeatsPerBooking = 3,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        reservationDeadline = 2.hours,
        bookingHorizon = 14.days,
        isPublished = true,
    )

    init {
        runBlocking {
            serviceRepo.create(service)
            ruleRepo.replaceForService(
                service.id,
                listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY).map {
                    AvailabilityRule(Uuid.random(), service.id, it, LocalTime(8, 0), LocalTime(12, 0), LocalDate(2026, 1, 1))
                },
            )
        }
    }

    private fun clockAt(now: LocalDateTime) = object : Clock {
        override fun now(): Instant = now.toInstant(APP_TIMEZONE)
    }

    private inner class TestService(
        now: LocalDateTime,
        private val admin: Boolean,
        reservations: ReservationRepository,
    ) : SlotBookingService(
        services = serviceRepo,
        availability = SlotAvailability(serviceRepo, ruleRepo, exceptionRepo, reservations),
        reservationService = coreReservationService(reservations),
        clock = clockAt(now),
    ) {
        override suspend fun isAdminCaller(): Boolean = admin
    }

    private fun booking(now: LocalDateTime = monday8, admin: Boolean = false, reservations: ReservationRepository = reservationRepo) =
        TestService(now, admin, reservations)

    private fun coreReservationService(reservations: ReservationRepository): ReservationService {
        val instances = InMemoryEventInstanceRepository()
        val series = InMemoryEventSeriesRepository()
        val walletService = WalletService(InMemoryWalletRepository())
        return ReservationService(
            eventInstanceRepository = instances,
            eventSeriesRepository = series,
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            reservationRepository = reservations,
            emailService = ConsoleEmailService(),
            lectorEmailService = ConsoleEmailService(),
            qrCodeService = StubQrCode,
            paymentTrigger = PaymentTrigger(),
            appBaseUrl = "https://test.example.com",
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            walletService = walletService,
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(bankAccountNumber = "", fioToken = "", senderEmail = "", gmailAppPassword = "", senderDisplayName = "")
            ),
            targetResolver = ReservationTargetResolver(
                instances, series, SlotAvailability(serviceRepo, ruleRepo, exceptionRepo, reservations),
            ),
        )
    }

    private object StubQrCode : QrCodeGeneratorService {
        override val accountNumber = "123456789/0100"
        override fun generateQrPng(reservation: Reservation, target: ReservationTarget?) = ByteArray(0)
    }

    private fun request(
        start: LocalDateTime = LocalDateTime(2026, 10, 20, 9, 0),
        end: LocalDateTime = LocalDateTime(2026, 10, 20, 11, 30),
        seats: Int = 2,
        email: String = "jana@test.cz",
        serviceId: Uuid = service.id,
        paymentType: PaymentType = PaymentType.BANK_TRANSFER,
        acknowledgedDuplicate: Boolean = false,
    ) = CreateSlotReservationRequest(
        serviceId = serviceId,
        start = start,
        end = end,
        seatCount = seats,
        contactName = "Jana Nováková",
        contactEmail = email,
        contactPhone = "+420777123456",
        paymentType = paymentType,
        customValues = emptyMap(),
        acknowledgedDuplicate = acknowledgedDuplicate,
    )

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(date, LocalTime(hour, minute))

    private fun reserve(
        request: CreateSlotReservationRequest,
        service: SlotBookingService = booking(),
    ): Either<ReservationError.CreateReservation, Reservation> = runBlocking { service.reserveSlot(request, userId = null) }

    private fun failure(request: CreateSlotReservationRequest, service: SlotBookingService = booking()): ReservationError.CreateReservation? =
        reserve(request, service).leftOrNull()

    // --- úspěch ---

    @Test
    fun `valid interval is reserved through the shared flow with price per hour and child`() {
        val reservation = reserve(request()).getOrNull()!!

        assertEquals(Reference.Slot(service.id, at(tuesday, 9), at(tuesday, 11, 30)), reservation.reference)
        // 150 Kč × 2 děti × 2,5 h
        assertEquals(750.0, reservation.totalPrice)
        assertEquals(Reservation.Status.PENDING_PAYMENT, reservation.status)
        assertTrue(reservation.variableSymbol != null)
        assertEquals(reservation, runBlocking { reservationRepo.findById(reservation.id) })
    }

    @Test
    fun `reserved seats lower availability of every covered step`() = runBlocking {
        reserve(request())

        val segments = booking().getAvailability(service.id, tuesday, tuesday).single().segments
        val freeByStart = segments.associate { it.start.time to it.free }
        assertEquals(4, freeByStart[LocalTime(8, 30)])
        assertEquals(2, freeByStart[LocalTime(9, 0)])
        assertEquals(2, freeByStart[LocalTime(11, 0)])
        assertEquals(4, freeByStart[LocalTime(11, 30)])
    }

    // --- 1. služba existuje a je publikovaná ---

    @Test
    fun `unknown service is not found`() {
        assertEquals(ReservationError.ReservationNotFound, failure(request(serviceId = Uuid.random())))
    }

    @Test
    fun `unpublished service is bookable only by admin`() {
        runBlocking { serviceRepo.update(service.copy(isPublished = false)) }

        assertEquals(ReservationError.ReservationNotFound, failure(request()))
        assertTrue(reserve(request(), booking(admin = true)).isRight())
    }

    // --- 2. zarovnání a délka ---

    @Test
    fun `start or end off the step grid is rejected`() {
        assertEquals(ReservationError.SlotNotAligned, failure(request(start = at(tuesday, 9, 15), end = at(tuesday, 10, 30))))
        assertEquals(ReservationError.SlotNotAligned, failure(request(start = at(tuesday, 9), end = at(tuesday, 10, 5))))
    }

    @Test
    fun `end not after start or on another day is rejected`() {
        assertEquals(ReservationError.SlotNotAligned, failure(request(start = at(tuesday, 10), end = at(tuesday, 10))))
        assertEquals(ReservationError.SlotNotAligned, failure(request(start = at(tuesday, 11), end = at(tuesday, 9))))
        assertEquals(ReservationError.SlotNotAligned, failure(request(start = at(tuesday, 11), end = at(wednesday, 9))))
    }

    @Test
    fun `duration outside min and max is rejected`() {
        assertEquals(ReservationError.SlotDurationOutOfRange, failure(request(start = at(tuesday, 9), end = at(tuesday, 9, 30))))
        runBlocking { serviceRepo.update(service.copy(maxDuration = 2.hours)) }
        assertEquals(ReservationError.SlotDurationOutOfRange, failure(request(start = at(tuesday, 9), end = at(tuesday, 11, 30))))
        assertTrue(reserve(request(start = at(tuesday, 9), end = at(tuesday, 11))).isRight())
    }

    // --- 3. celý úsek v jednom okně ---

    @Test
    fun `day without a window is rejected`() {
        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(wednesday, 9), end = at(wednesday, 10))))
    }

    @Test
    fun `interval reaching past the window is rejected`() {
        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(tuesday, 11), end = at(tuesday, 12, 30))))
        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(tuesday, 7, 30), end = at(tuesday, 9))))
    }

    @Test
    fun `interval spanning the gap between two windows is rejected`() = runBlocking {
        ruleRepo.replaceForService(
            service.id,
            ruleRepo.findByService(service.id) +
                AvailabilityRule(Uuid.random(), service.id, DayOfWeek.TUESDAY, LocalTime(13, 0), LocalTime(16, 0), LocalDate(2026, 1, 1)),
        )

        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(tuesday, 11), end = at(tuesday, 14))))
        assertTrue(reserve(request(start = at(tuesday, 13), end = at(tuesday, 14))).isRight())
    }

    @Test
    fun `closed exception and replacement hours are respected`() = runBlocking {
        exceptionRepo.save(AvailabilityException(Uuid.random(), service.id, tuesday, closed = false, from = LocalTime(14, 0), to = LocalTime(16, 0)))

        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(tuesday, 9), end = at(tuesday, 10))))
        assertTrue(reserve(request(start = at(tuesday, 14), end = at(tuesday, 15))).isRight())

        val thursday = LocalDate(2026, 10, 22)
        exceptionRepo.save(AvailabilityException(Uuid.random(), service.id, thursday, closed = true))
        assertEquals(ReservationError.SlotOutsideOpeningHours, failure(request(start = at(thursday, 9), end = at(thursday, 10))))
    }

    // --- 4. uzávěrka a horizont ---

    @Test
    fun `interval that has already started is rejected`() {
        val duringTheSlot = booking(now = at(tuesday, 9, 10))

        assertEquals(ReservationError.EventAlreadyStarted, failure(request(), duringTheSlot))
    }

    @Test
    fun `interval starting before the deadline is rejected`() {
        // Uzávěrka 2 h: v 7:30 jde rezervovat nejdřív od 9:30.
        val earlyMorning = booking(now = at(tuesday, 7, 30))

        assertEquals(ReservationError.ReservationDeadlinePassed, failure(request(start = at(tuesday, 9), end = at(tuesday, 10)), earlyMorning))
        assertTrue(reserve(request(start = at(tuesday, 9, 30), end = at(tuesday, 10, 30)), earlyMorning).isRight())
    }

    @Test
    fun `interval beyond the booking horizon is rejected`() {
        // Horizont 14 dní od pondělí 19. 10. 8:00 končí 2. 11. v 8:00.
        val thursdayInRange = LocalDate(2026, 10, 29)
        val tuesdayOutOfRange = LocalDate(2026, 11, 3)

        assertTrue(reserve(request(start = at(thursdayInRange, 9), end = at(thursdayInRange, 10))).isRight())
        assertEquals(
            ReservationError.SlotBeyondBookingHorizon,
            failure(request(start = at(tuesdayOutOfRange, 9), end = at(tuesdayOutOfRange, 10))),
        )
    }

    // --- 5. počet míst a kapacita na každém kroku ---

    @Test
    fun `seat count must be between one and the per-booking maximum`() {
        assertEquals(ReservationError.InvalidSeatCount, failure(request(seats = 0)))
        assertEquals(ReservationError.InvalidSeatCount, failure(request(seats = 4)))
        assertTrue(reserve(request(seats = 3)).isRight())
    }

    @Test
    fun `capacity is checked on every step of the interval`() {
        // Jiná rodina má 3 místa jen 10:00–11:00; volné je tam už jen 1.
        assertTrue(reserve(request(start = at(tuesday, 10), end = at(tuesday, 11), seats = 3, email = "jina@test.cz")).isRight())

        assertEquals(ReservationError.SlotCapacityExceeded, failure(request(start = at(tuesday, 9), end = at(tuesday, 11), seats = 2)))
        // Mimo plnou část jsou 2 místa volná.
        assertTrue(reserve(request(start = at(tuesday, 8), end = at(tuesday, 10), seats = 2)).isRight())
        assertTrue(reserve(request(start = at(tuesday, 10), end = at(tuesday, 11), seats = 1, email = "treti@test.cz")).isRight())
    }

    @Test
    fun `cancelled reservations do not hold seats`() {
        val other = reserve(request(seats = 3, email = "jina@test.cz")).getOrNull()!!
        runBlocking { reservationRepo.save(other.copy(status = Reservation.Status.CANCELLED)) }

        assertTrue(reserve(request(seats = 3)).isRight())
    }

    // --- sdílený tok: platba a duplicita ---

    @Test
    fun `payment type not offered by the service is rejected`() {
        assertEquals(ReservationError.PaymentTypeNotAllowed, failure(request(paymentType = PaymentType.ON_SITE)))
    }

    @Test
    fun `overlapping reservation with the same email asks for confirmation`() {
        assertTrue(reserve(request(start = at(tuesday, 9), end = at(tuesday, 10), seats = 1)).isRight())

        assertEquals(
            ReservationError.AlreadyReserved(DuplicateScope.SLOT_OVERLAP),
            failure(request(start = at(tuesday, 9, 30), end = at(tuesday, 10, 30), seats = 1, email = " Jana@Test.cz ")),
        )
        assertTrue(reserve(request(start = at(tuesday, 9, 30), end = at(tuesday, 10, 30), seats = 1, acknowledgedDuplicate = true)).isRight())
        // Navazující úsek se nepřekrývá.
        assertTrue(reserve(request(start = at(tuesday, 10, 30), end = at(tuesday, 11, 30), seats = 1)).isRight())
    }

    // --- souběh ---

    @Test
    fun `parallel requests for the last seats - exactly the capacity succeeds`() = runBlocking {
        // Čtení obsazenosti před uložením chvíli trvá: bez zámku by si ji všechny
        // požadavky přečetly dřív, než kterýkoli uloží, a prošly by všechny.
        val slowReservations = object : ReservationRepository by reservationRepo {
            override suspend fun findActiveSlotReservations(serviceId: Uuid, from: LocalDateTime, to: LocalDateTime): List<Reservation> {
                delay(20)
                return reservationRepo.findActiveSlotReservations(serviceId, from, to)
            }
        }
        val shared = booking(reservations = slowReservations)

        val results = (1..6).map { i ->
            async(Dispatchers.Default) { shared.reserveSlot(request(seats = 1, email = "rodic$i@test.cz"), userId = null) }
        }.awaitAll()

        assertEquals(4, results.count { it.isRight() })
        assertTrue(results.mapNotNull { it.leftOrNull() }.all { it == ReservationError.SlotCapacityExceeded })
        assertEquals(4, reservationRepo.findActiveSlotReservations(service.id, at(tuesday, 0), at(wednesday, 0)).sumOf { it.seatCount })
    }

    @Test
    fun `two parallel requests for the last seat - exactly one succeeds`() = runBlocking {
        reserve(request(seats = 3, email = "prvni@test.cz"))
        val slowReservations = object : ReservationRepository by reservationRepo {
            override suspend fun findActiveSlotReservations(serviceId: Uuid, from: LocalDateTime, to: LocalDateTime): List<Reservation> {
                delay(20)
                return reservationRepo.findActiveSlotReservations(serviceId, from, to)
            }
        }
        val shared = booking(reservations = slowReservations)

        val results = listOf("a@test.cz", "b@test.cz").map { email ->
            async(Dispatchers.Default) { shared.reserveSlot(request(seats = 1, email = email), userId = null) }
        }.awaitAll()

        assertEquals(1, results.count { it.isRight() })
        assertEquals(ReservationError.SlotCapacityExceeded, results.single { it.isLeft() }.leftOrNull())
    }

    // --- dostupnost pro UI ---

    @Test
    fun `availability is limited to bookable steps within the horizon`() = runBlocking {
        val days = booking(now = at(tuesday, 8, 15)).getAvailability(service.id, LocalDate(2026, 10, 1), LocalDate(2026, 12, 31))

        // Dnes jen kroky po uzávěrce (10:15 → od 10:30), poslední den horizontu 3. 11. 8:15 už nic.
        assertEquals(LocalTime(10, 30), days.first().segments.first().start.time)
        assertEquals(tuesday, days.first().date)
        assertEquals(LocalDate(2026, 10, 29), days.last().date)
    }

    @Test
    fun `hidden service has no availability and is not listed`() = runBlocking {
        serviceRepo.update(service.copy(isPublished = false))

        assertTrue(booking().getAvailability(service.id, tuesday, tuesday).isEmpty())
        assertTrue(booking().listServices().isEmpty())
        assertEquals(listOf(service.id), booking(admin = true).listServices().map { it.id })
    }

    @Test
    fun `target of an existing reservation reports free seats over its interval`() = runBlocking {
        val reservation = reserve(request(seats = 2)).getOrNull()!!

        val target = SlotAvailability(serviceRepo, ruleRepo, exceptionRepo, reservationRepo)
            .targetFor(reservation.reference as Reference.Slot)

        assertIs<ReservationTarget.Slot>(target)
        assertEquals(2, target.freeSeats)
        assertEquals(4, target.capacity)
        assertEquals(at(tuesday, 11, 30), target.endDateTime)
    }
}
