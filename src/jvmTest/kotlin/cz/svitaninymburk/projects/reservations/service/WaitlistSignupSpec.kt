package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WaitlistSignupSpec {

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository,
        reservationRepo: InMemoryReservationRepository,
        auditRepo: InMemoryAuditRepository = InMemoryAuditRepository(),
    ) = auditingEmails(auditRepo).let { mails -> ReservationService(
        audit = AuditService(auditRepo),
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = InMemoryEventSeriesRepository(),
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = mails,
        lectorEmailService = mails,
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = mails,
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    ) }

    /** Maily obalené auditem stejně jako v DI — jinak by se záznamy o nich nikde nevzaly. */
    private fun auditingEmails(auditRepo: InMemoryAuditRepository): AuditingEmailService {
        val console = ConsoleEmailService()
        return AuditingEmailService(console, console, console, AuditService(auditRepo))
    }

    private fun fullInstance(waitlistCapacity: Int, occupiedWaitlist: Int = 0) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Full Event",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 2,
        occupiedSpots = 2,
        waitlistCapacity = waitlistCapacity,
        occupiedWaitlist = occupiedWaitlist,
        isPublished = true,
    )

    private fun request(instanceId: Uuid) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyMap(),
    )

    @Test
    fun `joining waitlist on full event creates WAITLISTED reservation with no payment`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val instance = fullInstance(waitlistCapacity = 3)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, reservationRepo)

        val result = service.joinWaitlistInstance(request(instance.id), userId = null)

        assertTrue(result.isRight(), "Expected Right but got $result")
        val res = result.getOrNull()!!
        assertEquals(Reservation.Status.WAITLISTED, res.status)
        assertEquals(1, res.seatCount)
        assertEquals(null, res.variableSymbol)
        assertEquals(0.0, res.paidAmount)
        assertEquals(1, instanceRepo.get(instance.id)!!.occupiedWaitlist)
        assertEquals(2, instanceRepo.get(instance.id)!!.occupiedSpots)
    }

    @Test
    fun `joining waitlist fails when event is not full`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = fullInstance(waitlistCapacity = 3).copy(occupiedSpots = 1)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, InMemoryReservationRepository())

        val result = service.joinWaitlistInstance(request(instance.id), userId = null)
        assertEquals(ReservationError.EventNotFull, result.leftOrNull())
    }

    @Test
    fun `joining waitlist fails when no waitlist configured`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = fullInstance(waitlistCapacity = 0)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, InMemoryReservationRepository())

        val result = service.joinWaitlistInstance(request(instance.id), userId = null)
        assertEquals(ReservationError.WaitlistNotAvailable, result.leftOrNull())
    }

    @Test
    fun `joining waitlist fails when waitlist is full`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = fullInstance(waitlistCapacity = 1, occupiedWaitlist = 1)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, InMemoryReservationRepository())

        val result = service.joinWaitlistInstance(request(instance.id), userId = null)
        assertEquals(ReservationError.WaitlistFull, result.leftOrNull())
    }

    // --- Historie: zápis do pořadníku se dřív nikam nezapisoval ---

    @Test
    fun `zapis do poradniku se zaznamena do historie`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val audit = InMemoryAuditRepository()
        val instance = fullInstance(waitlistCapacity = 3)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, InMemoryReservationRepository(), audit)

        val res = service.joinWaitlistInstance(request(instance.id), userId = null).getOrNull()!!

        val zapis = audit.recordedEvents().single { it.type == AuditEventType.RESERVATION_WAITLIST_JOINED }
        assertEquals("Jan Novak", zapis.subjectLabel)
        assertEquals(instance.id, zapis.instanceId)
        assertEquals(res.id, zapis.reservationId)
        assertEquals(100.0, zapis.amount)
        // Host bez přihlášení není „systém" — zapsat se přišel zákazník.
        assertEquals(AuditActorType.CUSTOMER, zapis.actorType)
        assertEquals("jan@test.com", zapis.actorLabel)
    }

    /** Potvrzení o zápisu musí v historii dosednout na tu samou akci. */
    @Test
    fun `mail o zapisu do poradniku ma vazbu na akci`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val audit = InMemoryAuditRepository()
        val instance = fullInstance(waitlistCapacity = 3)
        instanceRepo.create(instance)
        val service = makeService(instanceRepo, InMemoryReservationRepository(), audit)

        service.joinWaitlistInstance(request(instance.id), userId = null)

        val mail = audit.recordedEvents().single { it.type == AuditEventType.EMAIL_WAITLIST_CONFIRMATION }
        assertEquals(instance.id, mail.instanceId)
        assertEquals("jan@test.com", mail.recipient)
    }
}
