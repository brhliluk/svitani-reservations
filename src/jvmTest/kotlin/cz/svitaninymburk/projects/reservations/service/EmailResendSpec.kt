package cz.svitaninymburk.projects.reservations.service

import arrow.core.left
import arrow.core.right
import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.audit.NewAuditEvent
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EmailResendSpec {

    /** Zapamatuje si, co a komu se poslalo — jinak by nešlo ověřit, že se posílá znovu totéž. */
    private class RecordingEmailService : EmailService by ConsoleEmailService() {
        val confirmations = mutableListOf<Pair<String, Reservation>>()
        var failNext = false

        override suspend fun sendReservationConfirmation(
            toEmail: String,
            reservation: Reservation,
            target: ReservationTarget,
            bankAccount: String,
            qrCodeImage: ByteArray?,
            icalBytes: ByteArray,
        ) = if (failNext) {
            EmailError.SendReservationConfirmationFailed("SMTP zase odmítl").left()
        } else {
            confirmations += toEmail to reservation
            Unit.right()
        }
    }

    private class Fixture {
        val auditRepository = InMemoryAuditRepository()
        val reservationRepository = InMemoryReservationRepository()
        val instanceRepository = InMemoryEventInstanceRepository()
        val seriesRepository = InMemoryEventSeriesRepository()
        val emailService = RecordingEmailService()

        val service = EmailResendService(
            auditRepository = auditRepository,
            reservationRepository = reservationRepository,
            eventInstanceRepository = instanceRepository,
            eventSeriesRepository = seriesRepository,
            emailService = emailService,
            qrCodeService = StubQrCodeGenerator(),
            appBaseUrl = "https://test.example.com",
        )

        suspend fun instance(): EventInstance = instanceRepository.create(
            EventInstance(
                id = Uuid.random(), definitionId = Uuid.random(), seriesId = null,
                title = "Podpůrná skupina v rodičovství", description = "",
                startDateTime = LocalDateTime(2099, 3, 1, 9, 0),
                endDateTime = LocalDateTime(2099, 3, 1, 11, 0),
                price = 200.0, capacity = 10, isPublished = true,
            )
        )

        suspend fun reservation(instanceId: Uuid, email: String = "anezka@example.com"): Reservation =
            reservationRepository.save(
                Reservation(
                    id = Uuid.random(),
                    reference = Reference.Instance(instanceId),
                    seatCount = 1,
                    contactName = "Anežka Brhlíková",
                    contactEmail = email,
                    contactPhone = null,
                    paymentType = PaymentType.BANK_TRANSFER,
                    customValues = emptyMap(),
                    totalPrice = 200.0,
                    status = Reservation.Status.PENDING_PAYMENT,
                    createdAt = Clock.System.now(),
                    variableSymbol = "2626487517",
                    locale = "cs",
                )
            )

        /** Vrátí id zapsaného záznamu — repository ho generuje sama. */
        suspend fun auditEntry(
            type: AuditEventType,
            reservationId: Uuid?,
            instanceId: Uuid?,
            recipient: String?,
        ): Uuid {
            auditRepository.record(
                NewAuditEvent(
                    type = type,
                    actorType = AuditActorType.ADMIN,
                    actorLabel = "admin@example.com",
                    subjectLabel = "Anežka Brhlíková",
                    instanceId = instanceId,
                    reservationId = reservationId,
                    outcome = AuditOutcome.FAILURE,
                    recipient = recipient,
                )
            )
            return auditRepository.recordedEvents().last().id
        }
    }

    @Test
    fun `neodeslane potvrzeni jde poslat znovu`() = runBlocking {
        val f = Fixture()
        val instance = f.instance()
        val reservation = f.reservation(instance.id)
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_RESERVATION_CONFIRMATION, reservation.id, instance.id, reservation.contactEmail,
        )

        val result = f.service.resend(entryId)

        assertEquals(reservation.contactEmail, result.getOrNull())
        assertEquals(listOf(reservation.contactEmail to reservation), f.emailService.confirmations)
    }

    /**
     * Adresa se bere z rezervace, ne ze starého záznamu: když admin mezitím opravil
     * překlep, opakované odeslání by jinak zase mířilo na mrtvou schránku.
     */
    @Test
    fun `posila se na aktualni kontaktni email, ne na ten z historie`() = runBlocking {
        val f = Fixture()
        val instance = f.instance()
        val reservation = f.reservation(instance.id, email = "spravna@example.com")
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_RESERVATION_CONFIRMATION, reservation.id, instance.id, "preklep@example.com",
        )

        val result = f.service.resend(entryId)

        assertEquals("spravna@example.com", result.getOrNull())
        assertEquals(listOf("spravna@example.com"), f.emailService.confirmations.map { it.first })
    }

    @Test
    fun `typ mimo seznam se preposlat neda`() = runBlocking {
        val f = Fixture()
        val instance = f.instance()
        val reservation = f.reservation(instance.id)
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_PASSWORD_RESET, reservation.id, instance.id, reservation.contactEmail,
        )

        assertIs<AdminError.ResendEmail.NotResendable>(f.service.resend(entryId).leftOrNull())
        assertTrue(f.emailService.confirmations.isEmpty())
    }

    @Test
    fun `zaznam bez rezervace se preposlat neda`() = runBlocking<Unit> {
        val f = Fixture()
        val instance = f.instance()
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_RESERVATION_CONFIRMATION, null, instance.id, "kdo@example.com",
        )

        assertIs<AdminError.ResendEmail.NotResendable>(f.service.resend(entryId).leftOrNull())
    }

    @Test
    fun `smazana rezervace vrati ReservationNotFound`() = runBlocking<Unit> {
        val f = Fixture()
        val instance = f.instance()
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_RESERVATION_CONFIRMATION, Uuid.random(), instance.id, "kdo@example.com",
        )

        assertIs<AdminError.ResendEmail.ReservationNotFound>(f.service.resend(entryId).leftOrNull())
    }

    @Test
    fun `neznamy zaznam vrati AuditEventNotFound`() = runBlocking<Unit> {
        val f = Fixture()

        assertIs<AdminError.ResendEmail.AuditEventNotFound>(f.service.resend(Uuid.random()).leftOrNull())
    }

    /** Opakované selhání se musí dostat až k adminovi — jinak by čekal, že mail došel. */
    @Test
    fun `selhani odeslani propadne jako SendFailed`() = runBlocking {
        val f = Fixture()
        val instance = f.instance()
        val reservation = f.reservation(instance.id)
        val entryId = f.auditEntry(
            AuditEventType.EMAIL_RESERVATION_CONFIRMATION, reservation.id, instance.id, reservation.contactEmail,
        )
        f.emailService.failNext = true

        val error = f.service.resend(entryId).leftOrNull()

        assertIs<AdminError.ResendEmail.SendFailed>(error)
        assertTrue(error.message.contains("SMTP zase odmítl"))
    }
}
