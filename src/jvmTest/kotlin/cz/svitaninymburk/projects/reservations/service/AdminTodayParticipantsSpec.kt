package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter

/**
 * Počítadlo "dnešních účastníků" na nástěnce musí sedět s prezenčkou lekce:
 * přihláška na kurz je jedna rezervace na sérii, takže dřív se do počtu vůbec
 * nepropsala, i když stejné lidi prezenčka té lekce ukazuje.
 */
class AdminTodayParticipantsSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()
    private val optOutRepo = InMemorySeriesLessonOptOutRepository()

    private fun service() = AdminDashboardService(
        eventDefinitionRepository = InMemoryEventDefinitionRepository(),
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        refundService = RefundService(
            walletService = WalletService(InMemoryWalletRepository()),
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(
                    bankAccountNumber = "", fioToken = "", senderEmail = "",
                    gmailAppPassword = "", senderDisplayName = "",
                )
            ),
        ),
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    private fun todayAt(hour: Int): LocalDateTime {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return LocalDateTime(today.year, today.month, today.day, hour, 0)
    }

    private fun lesson(id: Uuid, series: Uuid?, isCancelled: Boolean = false) = EventInstance(
        id = id,
        definitionId = definitionId,
        seriesId = series,
        title = "Lekce",
        description = "",
        startDateTime = todayAt(10),
        endDateTime = todayAt(11),
        price = 100.0,
        capacity = 20,
        isCancelled = isCancelled,
    )

    private suspend fun reserve(
        id: String,
        reference: Reference,
        seats: Int,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.parse(id),
            reference = reference,
            contactName = "Tester",
            contactEmail = "tester@example.com",
            seatCount = seats,
            totalPrice = 100.0,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    @Test
    fun `dnesni ucastnici zahrnuji kurz minus omluvene`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))

        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(lessonId), seats = 1)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), seats = 2)
        val omluveny = reserve("00000000-0000-0000-0000-0000000000d3", Reference.Series(seriesId), seats = 3)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = omluveny.id,
                instanceId = lessonId,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        val summary = service().getDashboardSummary().getOrNull()!!

        assertEquals(3, summary.todayParticipantsCount, "1 přímá rezervace + 2 místa z kurzu; omluvený se nepočítá")
    }

    @Test
    fun `odmitnuta rezervace se do dnesnich ucastniku nepocita`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))

        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(lessonId), seats = 1)
        reserve(
            "00000000-0000-0000-0000-0000000000d2",
            Reference.Instance(lessonId),
            seats = 5,
            status = Reservation.Status.REJECTED,
        )
        reserve(
            "00000000-0000-0000-0000-0000000000d3",
            Reference.Series(seriesId),
            seats = 4,
            status = Reservation.Status.REJECTED,
        )

        val summary = service().getDashboardSummary().getOrNull()!!

        assertEquals(1, summary.todayParticipantsCount, "odmítnuté rezervace místo nedrží")
    }

    @Test
    fun `zrusena lekce dnes zadne ucastniky nema`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId, isCancelled = true))

        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(lessonId), seats = 1)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), seats = 2)

        val summary = service().getDashboardSummary().getOrNull()!!

        assertEquals(0, summary.todayParticipantsCount, "na zrušenou lekci dnes nikdo nepřijde")
    }

    @Test
    fun `samostatna akce bez serie se pocita jako driv`() = runBlocking {
        val samostatna = Uuid.parse("00000000-0000-0000-0000-0000000000c9")
        instanceRepo.create(lesson(samostatna, series = null))

        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(samostatna), seats = 2)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), seats = 5)

        val summary = service().getDashboardSummary().getOrNull()!!

        assertEquals(2, summary.todayParticipantsCount, "kurz s dnešní lekcí nemá co do počtu přidat")
    }
}
