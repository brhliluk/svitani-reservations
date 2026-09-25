package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Přivlastnění rezervace bez účtu. Laťkou je znalost UUID rezervace plus shoda
 * kontaktního e-mailu s e-mailem přihlášeného účtu — a musí sedět s tím, co pak
 * ukáže „Moje rezervace“, jinak by tlačítko připsalo neviditelnou rezervaci.
 */
class ClaimReservationSpec {

    private val callerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val otherUserId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000a2")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val userRepo = InMemoryUserRepository()

    private inner class TestService(private val caller: Uuid?) : AuthenticatedReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        reservationRepository = reservationRepo,
        userRepository = userRepo,
    ) {
        override suspend fun currentCallerUserId(): Uuid? = caller
    }

    private fun service(caller: Uuid? = callerId) = TestService(caller)

    private suspend fun givenUser(id: Uuid, email: String) {
        userRepo.create(
            User.Email(
                id = id, email = email, name = "Jan", surname = "Host",
                role = User.Role.USER, passwordHash = "x",
            )
        )
    }

    private suspend fun givenInstance(day: Int, year: Int = 2099, cancelled: Boolean = false): EventInstance =
        instanceRepo.create(
            EventInstance(
                id = Uuid.random(),
                definitionId = Uuid.random(),
                title = "Jednorázová akce",
                description = "",
                startDateTime = LocalDateTime(year, 12, day, 10, 0),
                endDateTime = LocalDateTime(year, 12, day, 11, 0),
                price = 100.0,
                capacity = 10,
                occupiedSpots = 1,
                isCancelled = cancelled,
            )
        )

    private suspend fun givenSeries(
        startDate: LocalDate,
        endDate: LocalDate,
        cancelled: Boolean = false,
    ): EventSeries = seriesRepo.create(
        EventSeries(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Kurz",
            description = "",
            price = 500.0,
            capacity = 10,
            occupiedSpots = 1,
            startDate = startDate,
            endDate = endDate,
            lessonCount = 10,
            isCancelled = cancelled,
        )
    )

    private suspend fun givenReservation(
        reference: Reference,
        email: String = "host@test.cz",
        registeredUserId: Uuid? = null,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.random(),
            reference = reference,
            registeredUserId = registeredUserId,
            contactName = "Jan Host",
            contactEmail = email,
            seatCount = 1,
            totalPrice = 500.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    @Test
    fun `e-mail match ignoring letter case goes through`() = runBlocking {
        givenUser(callerId, "Host@Test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id), email = "  host@test.cz ")

        val result = service().claimReservation(reservation.id)

        assertTrue(result.isRight(), "mělo projít, dostal: $result")
        assertEquals(callerId, reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `after claiming the reservation is in My reservations`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))

        service().claimReservation(reservation.id)

        val mine = service().getReservations(callerId).getOrNull()!!
        assertEquals(listOf(reservation.id), mine.map { it.id })
        Unit
    }

    @Test
    fun `different e-mail does not go through`() = runBlocking {
        givenUser(callerId, "nekdo.jiny@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.EmailDoesNotMatch, result.leftOrNull())
        assertNull(reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `reservation already claimed by another user does not go through`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id), registeredUserId = otherUserId)

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.AlreadyClaimed, result.leftOrNull())
        assertEquals(otherUserId, reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `claiming again for oneself is fine`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id), registeredUserId = callerId)

        val result = service().claimReservation(reservation.id)

        assertTrue(result.isRight(), "dvojklik nesmí skončit chybou, dostal: $result")
        Unit
    }

    @Test
    fun `cancelled reservation does not go through`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(
            Reference.Instance(instance.id),
            status = Reservation.Status.CANCELLED,
        )

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.NotClaimable, result.leftOrNull())
        Unit
    }

    @Test
    fun `waitlisted reservation can be claimed`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(
            Reference.Instance(instance.id),
            status = Reservation.Status.WAITLISTED,
        )

        val result = service().claimReservation(reservation.id)

        assertTrue(result.isRight(), "čekatel je na akci přihlášený, dostal: $result")
        Unit
    }

    @Test
    fun `past event does not go through`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1, year = 2020)
        val reservation = givenReservation(Reference.Instance(instance.id))

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.NotClaimable, result.leftOrNull())
        Unit
    }

    @Test
    fun `cancelled event does not go through`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1, cancelled = true)
        val reservation = givenReservation(Reference.Instance(instance.id))

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.NotClaimable, result.leftOrNull())
        Unit
    }

    /** Jádro zadání: kurz, kterému část lekcí už proběhla, pořád běží. */
    @Test
    fun `course with some lessons in the past can be claimed`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val series = givenSeries(startDate = LocalDate(2020, 1, 1), endDate = LocalDate(2099, 12, 31))
        val reservation = givenReservation(Reference.Series(series.id))

        val result = service().claimReservation(reservation.id)

        assertTrue(result.isRight(), "rozhoduje konec kurzu, ne jednotlivé lekce; dostal: $result")
        Unit
    }

    @Test
    fun `finished course does not go through`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val series = givenSeries(startDate = LocalDate(2020, 1, 1), endDate = LocalDate(2020, 12, 31))
        val reservation = givenReservation(Reference.Series(series.id))

        val result = service().claimReservation(reservation.id)

        assertEquals(ReservationError.NotClaimable, result.leftOrNull())
        Unit
    }

    @Test
    fun `anonymous caller gets nothing`() = runBlocking {
        givenUser(callerId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))

        val result = service(caller = null).claimReservation(reservation.id)

        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
        assertNull(reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `nonexistent reservation returns NotFound`() = runBlocking {
        givenUser(callerId, "host@test.cz")

        val result = service().claimReservation(Uuid.random())

        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
        Unit
    }
}
