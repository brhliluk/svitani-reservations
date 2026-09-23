package cz.svitaninymburk.projects.reservations.service

import arrow.core.left
import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.event.EventInstance
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Storno zákazníkem nesmí spadnout na tom, že neodešel potvrzovací mail. Rezervace
 * je v tu chvíli už zrušená a místo uvolněné — chyba by zákazníka jen poslala
 * zkoušet to znovu. Místo toho projde s příznakem, podle kterého UI ukáže poznámku.
 */
class CancellationEmailFailureSpec {

    private val failingEmail = object : EmailService by ConsoleEmailService() {
        override suspend fun sendCancellationNotice(toEmail: String, eventTitle: String, reservationId: Uuid, locale: String) =
            EmailError.SendCancellationFailed("451 4.3.0 Temporary System Problem").left()
    }

    private fun makeService(
        instanceRepo: InMemoryEventInstanceRepository,
        reservationRepo: InMemoryReservationRepository,
        walletRepo: InMemoryWalletRepository,
        emailService: EmailService,
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = InMemoryEventSeriesRepository(),
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = emailService,
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(walletRepo),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )),
    )

    private val event = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Beseda",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 150.0,
        capacity = 5,
        occupiedSpots = 1,
        isPublished = true,
    )

    private val paidReservation = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(event.id),
        contactName = "Jana Novakova",
        contactEmail = "jana@test.com",
        seatCount = 1,
        totalPrice = 150.0,
        paidAmount = 150.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
    )

    @Test
    fun `failed cancellation email still cancels, credits the wallet and reports a note`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository().apply { create(event) }
        val reservationRepo = InMemoryReservationRepository().apply { save(paidReservation) }
        val service = makeService(instanceRepo, reservationRepo, InMemoryWalletRepository(), failingEmail)

        val result = service.cancelReservation(paidReservation.id, instanceId = null)

        val cancellation = assertNotNull(result.getOrNull(), "Expected Right but got $result")
        assertTrue(cancellation.cancellationEmailFailed)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(paidReservation.id)!!.status)
        // Dřív se kvůli selhanému mailu vrácení na peněženku vůbec nespustilo.
        assertEquals(150.0, cancellation.walletCreditAmount)
    }

    @Test
    fun `sent cancellation email reports no note`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository().apply { create(event) }
        val reservationRepo = InMemoryReservationRepository().apply { save(paidReservation) }
        val service = makeService(instanceRepo, reservationRepo, InMemoryWalletRepository(), ConsoleEmailService())

        val result = service.cancelReservation(paidReservation.id, instanceId = null)

        assertFalse(assertNotNull(result.getOrNull()).cancellationEmailFailed)
    }
}
