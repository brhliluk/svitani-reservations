package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
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
import cz.svitaninymburk.projects.reservations.reservation.ReservationKind
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Rezervace časového úseku (hlídání) v toku jádra. Jádro službu úseků nezná, cíl mu
 * dodá [SlotBookingProvider] z modulu; bez něj se rezervace chová jako na smazanou akci.
 * Storno platí stejně jako u jednorázové akce.
 */
class SlotReservationSpec {

    private val ownerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000e1")
    private val serviceId: Uuid = Uuid.random()

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val walletService = WalletService(InMemoryWalletRepository())

    /** Služba úseků tak, jak ji vidí jádro: jen data cíle. */
    private val provider = object : SlotBookingProvider {
        override suspend fun targetFor(reference: Reference.Slot): ReservationTarget.Slot? =
            if (reference.id != serviceId) null
            else ReservationTarget.Slot(
                serviceId = serviceId,
                title = "Hlídání",
                startDateTime = reference.start,
                endDateTime = reference.end,
                pricePerHour = 150.0,
                freeSeats = 5,
                capacity = 8,
                maxSeatsPerBooking = 3,
                allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                customFields = emptyList(),
            )
    }

    private fun resolver(withProvider: Boolean = true) =
        ReservationTargetResolver(instanceRepo, seriesRepo, if (withProvider) provider else null)

    private inner class TestService(
        private val admin: Boolean = false,
        withProvider: Boolean = true,
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
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = walletService,
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
        targetResolver = resolver(withProvider),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = null
        override suspend fun isAdminCaller(): Boolean = admin
    }

    private inner class TestAuthenticatedService : AuthenticatedReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        reservationRepository = reservationRepo,
        targetResolver = resolver(),
    ) {
        override suspend fun currentCallerUserId(): Uuid? = ownerId
    }

    private val futureStart = LocalDateTime(2099, 6, 2, 9, 0)
    private val futureEnd = LocalDateTime(2099, 6, 2, 11, 30)

    private suspend fun givenSlotReservation(
        start: LocalDateTime = futureStart,
        end: LocalDateTime = futureEnd,
        registeredUserId: Uuid? = null,
        paidAmount: Double = 750.0,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = Reference.Slot(serviceId, start, end),
            registeredUserId = registeredUserId,
            contactName = "Jana Nováková",
            contactEmail = "jana@test.cz",
            seatCount = 2,
            totalPrice = 750.0,
            paidAmount = paidAmount,
            status = if (paidAmount > 0) Reservation.Status.CONFIRMED else Reservation.Status.PENDING_PAYMENT,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    @Test
    fun `detail carries slot target from provider`() = runBlocking {
        val reservation = givenSlotReservation()

        val detail = TestService().getDetail(reservation.id).getOrNull()!!

        val target = assertIs<ReservationTarget.Slot>(detail.target)
        assertEquals(futureStart, target.startDateTime)
        assertEquals(futureEnd, target.endDateTime)
        assertEquals(serviceId, target.id)
        assertNull(detail.waitlistPosition)
    }

    @Test
    fun `without the module slot reservation looks like one for a deleted event`() = runBlocking {
        val reservation = givenSlotReservation()

        assertNull(TestService(withProvider = false).getDetail(reservation.id).getOrNull()!!.target)

        val result = TestService(withProvider = false).cancelReservation(reservation.id)
        assertTrue(result.isRight(), "storno má projít i bez modulu, dostal: $result")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    @Test
    fun `customer cancels before start and gets credit like for a one-off event`() = runBlocking {
        val reservation = givenSlotReservation()

        val result = TestService().cancelReservation(reservation.id).getOrNull()!!

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        assertEquals(750.0, result.walletCreditAmount)
    }

    @Test
    fun `customer cannot cancel a slot that has already started but admin can`() = runBlocking {
        val started = givenSlotReservation(
            start = LocalDateTime(2000, 1, 4, 9, 0),
            end = LocalDateTime(2099, 1, 4, 11, 0),
        )

        assertEquals(ReservationError.EventAlreadyStarted, TestService().cancelReservation(started.id).leftOrNull())
        assertTrue(TestService(admin = true).cancelReservation(started.id).isRight())
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(started.id)?.status)
        Unit
    }

    @Test
    fun `my reservations list slot with its kind and keep series flag`() = runBlocking {
        val slot = givenSlotReservation(registeredUserId = ownerId)
        val series = seriesRepo.create(
            EventSeries(
                id = Uuid.random(),
                definitionId = Uuid.random(),
                title = "Kurz",
                description = "",
                price = 1000.0,
                capacity = 10,
                startDate = LocalDate(2099, 1, 1),
                endDate = LocalDate(2099, 12, 31),
                lessonCount = 10,
            )
        )
        reservationRepo.save(slot.copy(id = Uuid.random(), reference = Reference.Series(series.id)))
        // Proběhlý úsek se v seznamu neukazuje, stejně jako proběhlá akce.
        givenSlotReservation(start = LocalDateTime(2000, 1, 4, 9, 0), end = LocalDateTime(2000, 1, 4, 10, 0), registeredUserId = ownerId)

        val items = TestAuthenticatedService().getReservations(ownerId).getOrNull()!!

        val kinds = items.associate { it.kind to it }
        assertEquals(setOf(ReservationKind.SLOT, ReservationKind.SERIES), kinds.keys)
        val slotItem = kinds.getValue(ReservationKind.SLOT)
        assertEquals("Hlídání", slotItem.eventTitle)
        assertEquals(futureStart, slotItem.startDateTime)
        assertEquals(false, slotItem.isSeries)
        assertEquals(true, kinds.getValue(ReservationKind.SERIES).isSeries)
    }

    @Test
    fun `resolver resolves a mix of targets at once`() = runBlocking {
        val series = seriesRepo.create(
            EventSeries(
                id = Uuid.random(), definitionId = Uuid.random(), title = "Kurz", description = "",
                price = 1000.0, capacity = 10, startDate = LocalDate(2099, 1, 1), endDate = LocalDate(2099, 12, 31),
                lessonCount = 10,
            )
        )
        val slot = Reference.Slot(serviceId, futureStart, futureEnd)
        val unknownSlot = Reference.Slot(Uuid.random(), futureStart, futureEnd)

        val targets = resolver().resolveAll(listOf(slot, Reference.Series(series.id), unknownSlot, Reference.Instance(Uuid.random())))

        assertEquals(setOf(slot, Reference.Series(series.id)), targets.keys)
        assertIs<ReservationTarget.Slot>(targets[slot])
        assertNull(resolver(withProvider = false).resolve(slot))
    }
}
