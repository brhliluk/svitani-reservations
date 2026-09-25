package cz.svitaninymburk.projects.reservations.service

import arrow.core.left
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.repository.audit.AuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.util.AuditSubject
import cz.svitaninymburk.projects.reservations.util.withAuditSubject
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AuditingEmailServiceSpec {

    /** Odesílání selže vždy — pro ověření, že se zapíše i neúspěch. */
    private class FailingEmailService : EmailService by ConsoleEmailService() {
        override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String) =
            EmailError.SendCancellationFailed("SMTP odmítl spojení").left()
    }

    /** Audit, který vždy spadne — nesmí to shodit odeslání mailu. */
    private class BrokenAuditRepository : AuditRepository {
        override suspend fun record(event: NewAuditEvent) = error("DB je pryč")
        override suspend fun recordAll(events: List<NewAuditEvent>) = error("DB je pryč")
        override suspend fun findById(id: Uuid): cz.svitaninymburk.projects.reservations.audit.AuditEvent? = null
        override suspend fun findForEvent(
            eventId: Uuid, isSeries: Boolean,
            category: cz.svitaninymburk.projects.reservations.audit.AuditCategory?,
            page: Int, pageSize: Int, parentSeriesId: Uuid?,
        ) = emptyList<cz.svitaninymburk.projects.reservations.audit.AuditEvent>()
        override suspend fun countForEvent(
            eventId: Uuid, isSeries: Boolean,
            category: cz.svitaninymburk.projects.reservations.audit.AuditCategory?,
            parentSeriesId: Uuid?,
        ) = 0L
        override suspend fun countAll() = 0L
        override suspend fun deleteOlderThan(cutoff: Instant) = 0
    }

    @Test
    fun `sent email is recorded as SUCCESS`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val service = AuditingEmailService(ConsoleEmailService(), ConsoleEmailService(), ConsoleEmailService(), AuditService(repo))

        service.sendCancellationNotice("kdo@example.com", "Cvičení rodičů s dětmi", Uuid.random(), "cs")

        val entry = repo.recordedEvents().single()
        assertEquals(AuditEventType.EMAIL_CANCELLATION_NOTICE, entry.type)
        assertEquals(AuditOutcome.SUCCESS, entry.outcome)
        assertEquals("kdo@example.com", entry.recipient)
    }

    @Test
    fun `unsent email is recorded as FAILURE including the error text`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val console = ConsoleEmailService()
        val service = AuditingEmailService(FailingEmailService(), console, console, AuditService(repo))

        val result = service.sendCancellationNotice("kdo@example.com", "Kurz", Uuid.random(), "cs")

        assertTrue(result.isLeft())
        val entry = repo.recordedEvents().single()
        assertEquals(AuditOutcome.FAILURE, entry.outcome)
        assertEquals("SMTP odmítl spojení", entry.detail)
    }

    /** Log nesmí být důvod, proč zákazníkovi nepřijde e-mail. */
    @Test
    fun `audit failure does not break sending`() = runBlocking {
        val service = AuditingEmailService(
            ConsoleEmailService(), ConsoleEmailService(), ConsoleEmailService(),
            AuditService(BrokenAuditRepository()),
        )

        val result = service.sendCancellationNotice("kdo@example.com", "Kurz", Uuid.random(), "cs")

        assertTrue(result.isRight())
    }

    /** Bez kódu by na řádek „připsán kredit" nešlo z historie kliknout. */
    @Test
    fun `wallet email carries the wallet code`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val console = ConsoleEmailService()
        val service = AuditingEmailService(console, console, console, AuditService(repo))

        service.sendWalletCredited(
            toEmail = "jana@example.com",
            walletCode = "SVIT-EEEE-EEEE",
            creditedAmount = 300.0,
            newBalance = 300.0,
            resetMonth = 6,
            resetDay = 30,
            locale = "cs",
        )

        assertEquals("SVIT-EEEE-EEEE", repo.recordedEvents().single().walletCode)
    }

    @Test
    fun `regular email has no wallet code`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val console = ConsoleEmailService()
        val service = AuditingEmailService(console, console, console, AuditService(repo))

        service.sendCancellationNotice("kdo@example.com", "Kurz", Uuid.random(), "cs")

        assertEquals(null, repo.recordedEvents().single().walletCode)
    }

    @Test
    fun `email takes the event link from the context`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val service = AuditingEmailService(ConsoleEmailService(), ConsoleEmailService(), ConsoleEmailService(), AuditService(repo))
        val seriesId = Uuid.random()
        val lessonId = Uuid.random()

        withAuditSubject(AuditSubject(seriesId = seriesId, instanceId = lessonId, label = "Kurz")) {
            service.sendLessonCancelledNotification(
                toEmail = "kdo@example.com",
                contactName = "Jana Nováková",
                seriesTitle = "Kurz",
                lessonDateTime = kotlinx.datetime.LocalDateTime(2026, 9, 15, 10, 0),
                locale = "cs",
            )
        }

        val entry = repo.recordedEvents().single()
        assertEquals(seriesId, entry.seriesId)
        assertEquals(lessonId, entry.instanceId)
    }
}

class ShortenSmtpErrorSpec {

    @Test
    fun `leaves a short message as is`() {
        assertEquals("SMTP odmítl spojení", shortenSmtpError("SMTP odmítl spojení"))
    }

    @Test
    fun `truncates a long message and joins it into a single line`() {
        val gmail = "EmailException: Sending the email to the following server failed : smtp.gmail.com:465\n" +
            "→ AuthenticationFailedException: 535-5.7.8 Username and Password not accepted. " +
            "For more information, go to https://support.google.com/mail/?p=BadCredentials " +
            "5b1f17b1804b1-49d26bdb789sm66069425e9.1 - gsmtp"

        val short = shortenSmtpError(gmail)

        assertTrue(short.length <= 241, "má se vejít do limitu, bylo ${short.length}")
        assertTrue(short.endsWith("…"))
        assertTrue("\n" !in short, "nemá zůstat víceřádkové")
        assertTrue(short.startsWith("EmailException: Sending the email"), "začátek nese informaci")
    }
}
