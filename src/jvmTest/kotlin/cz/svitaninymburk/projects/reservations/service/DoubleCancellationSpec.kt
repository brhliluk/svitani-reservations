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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Zrušit rezervaci jde jen jednou. Tlačítko po stornu zmizí, ale RPC je veřejné —
 * druhé volání by jinak proběhlo celou cestou znovu a připsalo zaplacenou částku
 * do peněženky podruhé.
 */
class DoubleCancellationSpec {

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

    private val instanceId = Uuid.parse("00000000-0000-0000-0000-0000000000e1")
    private val reservationId = Uuid.parse("00000000-0000-0000-0000-0000000000e2")

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

    @Test
    fun `druhe storne uz neprojde a nevrati penize podruhe`() = runBlocking {
        val reservation = prepare()

        val first = service.cancelReservation(reservation.id)
        assertTrue(first.isRight(), "první storno musí projít, dostal: $first")

        val second = service.cancelReservation(reservation.id)
        assertEquals(
            ReservationError.AlreadyCancelled,
            second.leftOrNull(),
            "druhé storno té samé rezervace musí skončit chybou, dostal: $second",
        )

        assertEquals(
            200.0,
            walletRepo.sumCreditedForReservation(reservation.id, WalletTransactionReason.CANCELLATION_REFUND),
            "zaplacená částka se smí vrátit jen jednou",
        )
        assertEquals(
            0,
            instanceRepo.get(instanceId)!!.occupiedSpots,
            "místa se smí uvolnit jen jednou, jinak čítač spadne do záporu",
        )
    }
}
