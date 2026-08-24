package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Čekatel v pořadníku žádné místo nedrží — drží jen řádek v pořadníku. Zrušení
 * jeho rezervace tedy nesmí sahat na `occupiedSpots` (o který se nikdy nepřihlásil)
 * a nesmí nikoho posouvat, protože se žádné místo neuvolnilo.
 *
 * Pořadník se počítá po přihláškách, ne po místech: `attemptToReserveWaitlistSpot`
 * zvedá čítač o 1 a strop se kontroluje jako `occupiedWaitlist + 1 <= waitlistCapacity`.
 * Odečítat se proto musí taky po jedné.
 */
class WaitlistCancellationSpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()

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
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private val instanceId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private suspend fun createInstance(
        capacity: Int,
        occupiedSpots: Int,
        waitlistCapacity: Int,
        occupiedWaitlist: Int,
    ) = instanceRepo.create(
        EventInstance(
            id = instanceId,
            definitionId = Uuid.random(),
            title = "Plná akce",
            description = "",
            startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
            price = 100.0,
            capacity = capacity,
            occupiedSpots = occupiedSpots,
            waitlistCapacity = waitlistCapacity,
            occupiedWaitlist = occupiedWaitlist,
            isPublished = true,
        )
    )

    private suspend fun reserve(
        id: String,
        seats: Int,
        status: Reservation.Status,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.parse(id),
            reference = Reference.Instance(instanceId),
            contactName = "Tester",
            contactEmail = "tester@example.com",
            seatCount = seats,
            totalPrice = 100.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentInfo.Type.BANK_TRANSFER,
        )
    )

    @Test
    fun `zruseni cekatele nesahne na obsazena mista`() = runBlocking {
        createInstance(capacity = 2, occupiedSpots = 2, waitlistCapacity = 3, occupiedWaitlist = 2)
        val cekatel = reserve("00000000-0000-0000-0000-0000000000d1", seats = 1, status = Reservation.Status.WAITLISTED)
        val druhyCekatel = reserve("00000000-0000-0000-0000-0000000000d2", seats = 1, status = Reservation.Status.WAITLISTED)

        val result = service.cancelReservation(cekatel.id)
        assertTrue(result.isRight(), "zrušení čekatele musí projít, dostal: $result")

        val after = instanceRepo.get(instanceId)!!
        assertEquals(2, after.occupiedSpots, "čekatel žádné místo nedržel, čítač míst se nesmí hnout")
        assertEquals(1, after.occupiedWaitlist, "z pořadníku ubyla jedna přihláška")
        assertEquals(
            Reservation.Status.WAITLISTED,
            reservationRepo.findById(druhyCekatel.id)?.status,
            "žádné místo se neuvolnilo, takže se nikdo posouvat nemá",
        )
    }

    @Test
    fun `posun z poradniku ubere jednu prihlasku, ne pocet mist`() = runBlocking {
        createInstance(capacity = 5, occupiedSpots = 5, waitlistCapacity = 3, occupiedWaitlist = 1)
        val potvrzena = reserve("00000000-0000-0000-0000-0000000000d1", seats = 2, status = Reservation.Status.CONFIRMED)
        val cekatel = reserve("00000000-0000-0000-0000-0000000000d2", seats = 2, status = Reservation.Status.WAITLISTED)

        val result = service.cancelReservation(potvrzena.id)
        assertTrue(result.isRight(), "zrušení potvrzené rezervace musí projít, dostal: $result")

        assertEquals(
            Reservation.Status.PENDING_PAYMENT,
            reservationRepo.findById(cekatel.id)?.status,
            "uvolnila se dvě místa, čekatel se dvěma místy se má posunout",
        )
        assertEquals(
            0,
            instanceRepo.get(instanceId)!!.occupiedWaitlist,
            "pořadník se počítá po přihláškách — dvoumístný čekatel z něj ubere jednu, ne dvě",
        )
    }
}
