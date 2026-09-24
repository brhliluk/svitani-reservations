package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemorySeriesAwareCapacityGuard
import cz.svitaninymburk.projects.reservations.repository.event.InMemorySeriesLessonLoad
import cz.svitaninymburk.projects.reservations.repository.event.SeriesAwareEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Zápis na kurz drží místo na každé lekci. Jeho storno proto musí posunout
 * i pořadníky jednotlivých lekcí, ne jen pořadník kurzu — jinak náhradník na lekci
 * zůstane čekat, i když je na ní volno.
 */
class LessonWaitlistOnEnrollmentCancelSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val innerInstances = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()
    private val load = InMemorySeriesLessonLoad(reservationRepo, optOutRepo)
    private val instanceRepo = SeriesAwareEventInstanceRepository(
        delegate = innerInstances,
        load = load,
        guard = InMemorySeriesAwareCapacityGuard(innerInstances, load),
    )

    private val service = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = ConsoleEmailService(),
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = optOutRepo,
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private val createdAt = Clock.System.now()

    private suspend fun setup(seriesCapacity: Int, seriesWaitlist: Int = 0) {
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Kurz",
                description = "",
                price = 500.0,
                capacity = seriesCapacity,
                occupiedSpots = 1,
                waitlistCapacity = 3,
                occupiedWaitlist = seriesWaitlist,
                startDate = LocalDate(2099, 12, 1),
                endDate = LocalDate(2099, 12, 31),
                lessonCount = 1,
            )
        )
        // Kapacita 2: jedno místo drží drop-in, druhé zápis na kurz — lekce je plná.
        innerInstances.create(
            EventInstance(
                id = lessonId,
                definitionId = Uuid.random(),
                seriesId = seriesId,
                title = "Lekce",
                description = "",
                startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
                endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
                price = 100.0,
                capacity = 2,
                occupiedSpots = 1,
                waitlistCapacity = 3,
                occupiedWaitlist = 1,
                isDropIn = true,
            )
        )
    }

    private suspend fun reserve(id: String, reference: Reference, status: Reservation.Status, order: Int) =
        reservationRepo.save(
            Reservation(
                id = Uuid.parse(id),
                reference = reference,
                contactName = "Tester $id",
                contactEmail = "tester$order@example.com",
                seatCount = 1,
                totalPrice = 100.0,
                status = status,
                createdAt = createdAt + order.minutes,
                customValues = emptyMap(),
                paymentType = PaymentType.BANK_TRANSFER,
            )
        )

    @Test
    fun `storno zapisu na kurz posune naradnika na lekci`() = runBlocking {
        setup(seriesCapacity = 10)
        val zapis = reserve("00000000-0000-0000-0000-0000000000d1", Reference.Series(seriesId), Reservation.Status.CONFIRMED, 0)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Instance(lessonId), Reservation.Status.CONFIRMED, 1)
        val cekatel = reserve("00000000-0000-0000-0000-0000000000d3", Reference.Instance(lessonId), Reservation.Status.WAITLISTED, 2)
        assertTrue(instanceRepo.get(lessonId)!!.isFull, "předpoklad: lekce je plná")

        val result = service.cancelReservation(zapis.id)
        assertTrue(result.isRight(), "storno musí projít, dostal: $result")

        assertEquals(
            Reservation.Status.PENDING_PAYMENT,
            reservationRepo.findById(cekatel.id)?.status,
            "zápis na kurz uvolnil místo na lekci, náhradník na ni se má posunout",
        )
        val lesson = instanceRepo.get(lessonId)!!
        assertEquals(2, lesson.occupiedSpots, "drop-in + posunutý náhradník")
        assertEquals(0, lesson.occupiedWaitlist)
    }

    @Test
    fun `naradnik na cely kurz ma prednost pred naradnikem na lekci`() = runBlocking {
        setup(seriesCapacity = 1, seriesWaitlist = 1)
        val zapis = reserve("00000000-0000-0000-0000-0000000000d1", Reference.Series(seriesId), Reservation.Status.CONFIRMED, 0)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Instance(lessonId), Reservation.Status.CONFIRMED, 1)
        val cekatelNaLekci = reserve("00000000-0000-0000-0000-0000000000d3", Reference.Instance(lessonId), Reservation.Status.WAITLISTED, 2)
        val cekatelNaKurz = reserve("00000000-0000-0000-0000-0000000000d4", Reference.Series(seriesId), Reservation.Status.WAITLISTED, 3)

        val result = service.cancelReservation(zapis.id)
        assertTrue(result.isRight(), "storno musí projít, dostal: $result")

        assertEquals(Reservation.Status.PENDING_PAYMENT, reservationRepo.findById(cekatelNaKurz.id)?.status)
        assertEquals(
            Reservation.Status.WAITLISTED,
            reservationRepo.findById(cekatelNaLekci.id)?.status,
            "uvolněné místo na lekci zabral náhradník na kurz, pro lekci nic nezbylo",
        )
        assertEquals(2, instanceRepo.get(lessonId)!!.occupiedSpots, "lekce nesmí být přeplněná")
    }
}
