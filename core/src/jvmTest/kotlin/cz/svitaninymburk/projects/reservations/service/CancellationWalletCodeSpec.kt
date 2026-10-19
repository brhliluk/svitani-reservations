package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
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
import cz.svitaninymburk.projects.reservations.wallet.WalletTransactionReason
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Kód peněženky u storna se ověřuje dřív, než se rezervace zruší. Neexistující kód
 * nebo cizí e-mail je chyba k opakování — kdyby se vyhodila až po uložení storna,
 * druhý pokus by spadl na AlreadyCancelled a kredit by nevznikl nikdy.
 */
class CancellationWalletCodeSpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val walletRepo = InMemoryWalletRepository()

    private val service = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = InMemoryEventSeriesRepository(),
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(walletRepo),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private val instanceId = Uuid.parse("00000000-0000-0000-0000-0000000000f1")
    private val reservationId = Uuid.parse("00000000-0000-0000-0000-0000000000f2")

    private suspend fun prepare(): Reservation {
        instanceRepo.create(
            EventInstance(
                id = instanceId,
                definitionId = Uuid.random(),
                title = "Akce",
                description = "",
                startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
                endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
                price = 100.0,
                capacity = 10,
                occupiedSpots = 2,
                isPublished = true,
            )
        )
        return reservationRepo.save(
            Reservation(
                id = reservationId,
                reference = Reference.Instance(instanceId),
                contactName = "Tester",
                contactEmail = "tester@example.com",
                seatCount = 2,
                totalPrice = 200.0,
                paidAmount = 200.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )
    }

    private val walletService = WalletService(walletRepo)

    @Test
    fun `wallet with a foreign email keeps the reservation active and credits after confirmation`() = runBlocking {
        val reservation = prepare()
        val foreignWallet = walletService.findOrCreateForRegisteredUser(Uuid.random(), "owner@example.com")

        val first = service.cancelReservation(reservation.id, null, foreignWallet.code, false)

        assertEquals(ReservationError.WalletEmailMismatch, first.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)!!.status)
        assertEquals(2, instanceRepo.get(instanceId)!!.occupiedSpots, "místa se nesmí uvolnit")

        val confirmed = service.cancelReservation(reservation.id, null, foreignWallet.code, true)

        assertTrue(confirmed.isRight(), "potvrzené storno musí projít, dostal: $confirmed")
        assertEquals(200.0, confirmed.getOrNull()!!.walletCreditAmount)
        assertEquals(200.0, walletRepo.findByCode(foreignWallet.code)!!.balance)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)!!.status)
    }

    @Test
    fun `unknown wallet code keeps the reservation active`() = runBlocking {
        val reservation = prepare()

        val result = service.cancelReservation(reservation.id, null, "SVIT-XXXX-XXXX", false)

        assertEquals(ReservationError.WalletNotFound, result.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)!!.status)
        assertEquals(2, instanceRepo.get(instanceId)!!.occupiedSpots)

        val withoutCode = service.cancelReservation(reservation.id, null, null, false)
        assertNotNull(withoutCode.getOrNull()?.walletCode, "bez kódu vznikne nová peněženka")
        Unit
    }
}
