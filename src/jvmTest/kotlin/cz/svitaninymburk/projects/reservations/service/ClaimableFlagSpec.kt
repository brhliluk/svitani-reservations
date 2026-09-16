package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Příznak, podle kterého se na detailu ukáže tlačítko „Přidat do mých rezervací“.
 * Počítá ho server — klient o shodě e-mailů nerozhoduje.
 */
class ClaimableFlagSpec {

    private val callerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val userRepo = InMemoryUserRepository()

    private inner class TestService(private val caller: Uuid?) : ReservationService(
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
        userRepository = userRepo,
    ) {
        override suspend fun currentCallerUserId(): Uuid? = caller
    }

    private suspend fun givenUser(email: String) {
        userRepo.create(
            User.Email(
                id = callerId, email = email, name = "Jan", surname = "Host",
                role = User.Role.USER, passwordHash = "x",
            )
        )
    }

    private suspend fun givenInstance(): EventInstance = instanceRepo.create(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Akce",
            description = "",
            startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
            price = 100.0,
            capacity = 10,
            occupiedSpots = 1,
        )
    )

    private suspend fun givenReservation(registeredUserId: Uuid? = null): Reservation =
        reservationRepo.save(
            Reservation(
                id = Uuid.random(),
                reference = Reference.Instance(givenInstance().id),
                registeredUserId = registeredUserId,
                contactName = "Jan Host",
                contactEmail = "host@test.cz",
                seatCount = 1,
                totalPrice = 100.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentInfo.Type.BANK_TRANSFER,
            )
        )

    private suspend fun claimableFor(caller: Uuid?, reservation: Reservation): Boolean =
        TestService(caller).getDetail(reservation.id).getOrNull()!!.claimable

    @Test
    fun `shodny e-mail prihlaseneho uzivatele tlacitko ukaze`() = runBlocking {
        givenUser("host@test.cz")
        assertTrue(claimableFor(callerId, givenReservation()))
        Unit
    }

    @Test
    fun `nepriblaseny navstevnik tlacitko nevidi`() = runBlocking {
        givenUser("host@test.cz")
        assertFalse(claimableFor(null, givenReservation()))
        Unit
    }

    @Test
    fun `jiny e-mail tlacitko nevidi`() = runBlocking {
        givenUser("nekdo.jiny@test.cz")
        assertFalse(claimableFor(callerId, givenReservation()))
        Unit
    }

    @Test
    fun `uz privlastnena rezervace tlacitko neukazuje`() = runBlocking {
        givenUser("host@test.cz")
        assertFalse(claimableFor(callerId, givenReservation(registeredUserId = callerId)))
        Unit
    }
}
