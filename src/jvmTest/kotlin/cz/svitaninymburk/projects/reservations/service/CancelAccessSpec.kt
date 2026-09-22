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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Kdo smí zrušit celou rezervaci. Rezervaci bez účtu chrání znalost UUID,
 * registrovanou její majitel — stejná laťka, jakou drží omluvenka z lekce.
 *
 * Bez téhle brány by přivlastnění rezervace bylo aktivně škodlivé: kredit ze storna
 * jde registrované rezervaci do peněženky účtu a její kód se vrací volajícímu.
 */
class CancelAccessSpec {

    private val ownerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000d1")
    private val strangerId: Uuid = Uuid.parse("00000000-0000-0000-0000-0000000000d2")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()

    private inner class TestService(
        private val caller: Uuid?,
        private val admin: Boolean = false,
    ) : ReservationService(
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
    ) {
        override suspend fun currentCallerUserId(): Uuid? = caller
        override suspend fun isAdminCaller(): Boolean = admin
    }

    private suspend fun givenInstance(
        start: LocalDateTime = LocalDateTime(2099, 6, 1, 10, 0),
        end: LocalDateTime = LocalDateTime(2099, 6, 1, 11, 0),
    ): EventInstance = instanceRepo.create(
        EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Akce",
            description = "",
            startDateTime = start,
            endDateTime = end,
            price = 100.0,
            capacity = 10,
            occupiedSpots = 1,
        )
    )

    private suspend fun givenReservation(
        registeredUserId: Uuid?,
        instance: EventInstance? = null,
    ): Reservation =
        reservationRepo.save(
            Reservation(
                id = Uuid.random(),
                reference = Reference.Instance((instance ?: givenInstance()).id),
                registeredUserId = registeredUserId,
                contactName = "Jan Host",
                contactEmail = "host@test.cz",
                seatCount = 1,
                totalPrice = 100.0,
                status = Reservation.Status.CONFIRMED,
                createdAt = Clock.System.now(),
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )

    @Test
    fun `rezervaci bez uctu zrusi i neprihlaseny`() = runBlocking {
        val reservation = givenReservation(registeredUserId = null)

        val result = TestService(caller = null).cancelReservation(reservation.id)

        assertTrue(result.isRight(), "hosta bez účtu chrání jen UUID, dostal: $result")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    @Test
    fun `registrovanou rezervaci zrusi jeji majitel`() = runBlocking {
        val reservation = givenReservation(registeredUserId = ownerId)

        val result = TestService(caller = ownerId).cancelReservation(reservation.id)

        assertTrue(result.isRight(), "majitel musí projít, dostal: $result")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    @Test
    fun `registrovanou rezervaci neprihlaseny nezrusi`() = runBlocking {
        val reservation = givenReservation(registeredUserId = ownerId)

        val result = TestService(caller = null).cancelReservation(reservation.id)

        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    /**
     * Administrace ruší cizí rezervace touhle samou RPC cestou
     * (ui/admin/**/usecase volá ReservationServiceInterface.cancelReservation).
     */
    @Test
    fun `admin zrusi i cizi registrovanou rezervaci`() = runBlocking {
        val reservation = givenReservation(registeredUserId = ownerId)

        val result = TestService(caller = strangerId, admin = true).cancelReservation(reservation.id)

        assertTrue(result.isRight(), "admin musí projít, dostal: $result")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    /**
     * Kurz začal a účastník odpadl v půlce — admin ho musí umět odhlásit.
     * Zákazník má pořád zavřeno, kredit se dole stejně řídí uzávěrkou.
     */
    @Test
    fun `admin zrusi rezervaci na uz zapocatou akci`() = runBlocking {
        val past = givenInstance(
            start = LocalDateTime(2020, 6, 1, 10, 0),
            end = LocalDateTime(2020, 6, 1, 11, 0),
        )
        val reservation = givenReservation(registeredUserId = ownerId, instance = past)

        val result = TestService(caller = strangerId, admin = true).cancelReservation(reservation.id)

        assertTrue(result.isRight(), "admin musí projít i po začátku akce, dostal: $result")
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    @Test
    fun `zakaznik rezervaci na uz zapocatou akci nezrusi`() = runBlocking {
        val past = givenInstance(
            start = LocalDateTime(2020, 6, 1, 10, 0),
            end = LocalDateTime(2020, 6, 1, 11, 0),
        )
        val reservation = givenReservation(registeredUserId = ownerId, instance = past)

        val result = TestService(caller = ownerId).cancelReservation(reservation.id)

        assertEquals(ReservationError.EventAlreadyStarted, result.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }

    @Test
    fun `registrovanou rezervaci cizi ucet nezrusi`() = runBlocking {
        val reservation = givenReservation(registeredUserId = ownerId)

        val result = TestService(caller = strangerId).cancelReservation(reservation.id)

        assertEquals(ReservationError.ReservationNotFound, result.leftOrNull())
        assertEquals(Reservation.Status.CONFIRMED, reservationRepo.findById(reservation.id)?.status)
        Unit
    }
}
