package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.right
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.claim.InMemoryReservationClaimTokenRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Hromadné přidání rezervací k účtu podle shody e-mailu.
 *
 * Laťkou tu není znalost UUID rezervace jako u [ClaimReservationSpec], ale odkaz
 * doručený na adresu účtu — proto se tady testuje hlavně to, co všechno ten odkaz
 * neudělá: expirovaný, už použitý, nebo vydaný na jinou adresu, než má účet dnes.
 */
class ClaimByEmailSpec {

    private val userId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val otherUserId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000b2")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val userRepo = InMemoryUserRepository()
    private val tokenRepo = InMemoryReservationClaimTokenRepository()
    private val auditRepo = InMemoryAuditRepository()
    private val emails = RecordingClaimEmailService()

    private val now: Instant = Clock.System.now()

    private val service = ReservationClaimService(
        reservationRepository = reservationRepo,
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        userRepository = userRepo,
        claimTokenRepository = tokenRepo,
        emailService = emails,
        appBaseUrl = "https://test.local",
        audit = AuditService(auditRepo),
    )

    /** Zachytává odeslané maily; `fail` nechá odeslání selhat. */
    private class RecordingClaimEmailService(var fail: Boolean = false) : EmailService by ConsoleEmailService() {
        val sent = mutableListOf<Triple<String, List<MyReservationListItem>, String>>()

        override suspend fun sendReservationClaimEmail(
            toEmail: String,
            reservations: List<MyReservationListItem>,
            claimToken: String,
            locale: String,
        ): Either<EmailError.SendReservationClaim, Unit> {
            if (fail) return Either.Left(EmailError.SendReservationClaimFailed("SMTP down"))
            sent += Triple(toEmail, reservations, claimToken)
            return Unit.right()
        }
    }

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
                title = "Akce $day",
                description = "",
                startDateTime = LocalDateTime(year, 12, day, 10, 0),
                endDateTime = LocalDateTime(year, 12, day, 11, 0),
                price = 100.0,
                capacity = 10,
                occupiedSpots = 1,
                isCancelled = cancelled,
            )
        )

    private suspend fun givenSeries(startDate: LocalDate, endDate: LocalDate, cancelled: Boolean = false): EventSeries =
        seriesRepo.create(
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

    /** Projde celý tok až k uplatnění a vrátí token z odeslaného mailu. */
    private suspend fun requestAndGetToken(): String {
        service.requestClaim(userId, now)
        return emails.sent.last().third
    }

    // --- počítání nabídky ---

    @Test
    fun `count includes only guest reservations for the account e-mail`() = runBlocking {
        givenUser(userId, "Host@Test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id), email = "host@test.cz")
        givenReservation(Reference.Instance(instance.id), email = "nekdo.jiny@test.cz")
        givenReservation(Reference.Instance(instance.id), email = "host@test.cz", registeredUserId = otherUserId)

        assertEquals(1, service.countClaimable(userId, now))
    }

    @Test
    fun `whitespace around the address does not break the count`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id), email = "  Host@Test.cz ")

        assertEquals(1, service.countClaimable(userId, now))
    }

    @Test
    fun `finished event, cancelled course and inactive statuses are not counted`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val past = givenInstance(day = 1, year = 2000)
        val cancelledSeries = givenSeries(LocalDate(2099, 1, 1), LocalDate(2099, 12, 31), cancelled = true)
        val running = givenInstance(day = 2)
        givenReservation(Reference.Instance(past.id))
        givenReservation(Reference.Series(cancelledSeries.id))
        givenReservation(Reference.Instance(running.id), status = Reservation.Status.CANCELLED)

        assertEquals(0, service.countClaimable(userId, now))
    }

    // --- vyžádání odkazu ---

    @Test
    fun `request stores the token and sends an email with the link`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id))

        val result = service.requestClaim(userId, now)

        assertEquals(1, result.getOrNull())
        assertEquals(1, emails.sent.size)
        val (to, items, token) = emails.sent.single()
        assertEquals("host@test.cz", to)
        assertEquals(1, items.size)
        val saved = tokenRepo.findByToken(token)!!
        assertEquals(userId, saved.userId)
        assertEquals("host@test.cz", saved.email)
        assertEquals(now + 24.hours, saved.expiresAt)
        Unit
    }

    @Test
    fun `no email is sent without reservations`() = runBlocking {
        givenUser(userId, "host@test.cz")

        val result = service.requestClaim(userId, now)

        assertEquals(ReservationError.NothingToClaim, result.leftOrNull())
        assertTrue(emails.sent.isEmpty())
        Unit
    }

    @Test
    fun `repeated request within five minutes does not send a second email`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id))

        service.requestClaim(userId, now)
        val second = service.requestClaim(userId, now + 1.minutes)

        assertTrue(second.isRight())
        assertEquals(1, emails.sent.size)
        Unit
    }

    @Test
    fun `request after the throttle window invalidates the previous link`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id))

        service.requestClaim(userId, now)
        val firstToken = emails.sent.single().third
        service.requestClaim(userId, now + 10.minutes)

        assertEquals(2, emails.sent.size)
        assertNull(tokenRepo.findByToken(firstToken))
        Unit
    }

    @Test
    fun `email failure leaves no token behind`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id))
        emails.fail = true

        val result = service.requestClaim(userId, now)

        assertTrue(result.leftOrNull() is ReservationError.ClaimEmailSendFailed)
        assertNull(tokenRepo.findLatestUnusedFor(userId))
        Unit
    }

    // --- uplatnění odkazu ---

    @Test
    fun `confirmation claims all reservations and records an audit entry`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val first = givenInstance(day = 1)
        val second = givenInstance(day = 2)
        val a = givenReservation(Reference.Instance(first.id))
        val b = givenReservation(Reference.Instance(second.id))
        val token = requestAndGetToken()

        val result = service.confirmClaim(token, now).getOrNull()!!

        assertEquals(2, result.claimed)
        assertEquals(0, result.skipped)
        assertEquals(userId, reservationRepo.findById(a.id)?.registeredUserId)
        assertEquals(userId, reservationRepo.findById(b.id)?.registeredUserId)
        // Audit píše jen tenhle tok, takže celkový počet je počet připsaných rezervací.
        assertEquals(2L, auditRepo.countAll())
        Unit
    }

    @Test
    fun `unknown token is an invalid link`() = runBlocking {
        val result = service.confirmClaim("nic-takoveho", now)

        assertEquals(ReservationError.ClaimLinkInvalid, result.leftOrNull())
        Unit
    }

    @Test
    fun `expired link claims nothing`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))
        val token = requestAndGetToken()

        val result = service.confirmClaim(token, now + 2.days)

        assertEquals(ReservationError.ClaimLinkExpired, result.leftOrNull())
        assertNull(reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `opening the link again returns the original count and claims nothing`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        givenReservation(Reference.Instance(instance.id))
        val token = requestAndGetToken()
        service.confirmClaim(token, now)

        val again = service.confirmClaim(token, now).getOrNull()!!

        assertTrue(again.alreadyDone)
        assertEquals(1, again.claimed)
        Unit
    }

    @Test
    fun `changing the account e-mail invalidates the link`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))
        val token = requestAndGetToken()

        val user = userRepo.findById(userId) as User.Email
        userRepo.update(userId, user.copy(email = "jiny@test.cz"))

        val result = service.confirmClaim(token, now)

        assertEquals(ReservationError.ClaimLinkInvalid, result.leftOrNull())
        assertNull(reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `reservation taken in the meantime is skipped and the rest goes through`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val first = givenInstance(day = 1)
        val second = givenInstance(day = 2)
        val taken = givenReservation(Reference.Instance(first.id))
        val free = givenReservation(Reference.Instance(second.id))
        val token = requestAndGetToken()

        // Mezi odesláním odkazu a klikem si rezervaci připsal někdo jiný.
        reservationRepo.linkToUser(taken.id, otherUserId)

        val result = service.confirmClaim(token, now).getOrNull()!!

        assertEquals(1, result.claimed)
        assertEquals(otherUserId, reservationRepo.findById(taken.id)?.registeredUserId)
        assertEquals(userId, reservationRepo.findById(free.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `event that finished in the meantime is not claimed`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))
        val token = requestAndGetToken()

        // Klik přijde až po konci akce.
        val result = service.confirmClaim(token, Instant.parse("2100-01-01T00:00:00Z")).getOrNull()

        assertNull(result?.claimed?.takeIf { it > 0 })
        assertNull(reservationRepo.findById(reservation.id)?.registeredUserId)
        Unit
    }

    @Test
    fun `claimed reservation appears in My reservations`() = runBlocking {
        givenUser(userId, "host@test.cz")
        val instance = givenInstance(day = 1)
        val reservation = givenReservation(Reference.Instance(instance.id))
        val token = requestAndGetToken()

        service.confirmClaim(token, now)

        val mine = AuthenticatedReservationService(instanceRepo, seriesRepo, reservationRepo, userRepo)
            .getReservations(userId).getOrNull()!!
        assertEquals(listOf(reservation.id), mine.map { it.id })
        assertFalse(mine.isEmpty())
        Unit
    }
}
