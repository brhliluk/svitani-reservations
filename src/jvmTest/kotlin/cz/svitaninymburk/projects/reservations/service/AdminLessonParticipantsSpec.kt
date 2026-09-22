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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter

/**
 * Seznam účastníků lekce musí sedět s obsazeností nad ním: přihláška na kurz drží
 * místo na každé lekci (dopočítává ji SeriesAwareEventInstanceRepository), ale
 * rezervaci má na sérii — bez doplnění by hlavička hlásila víc lidí, než kolik
 * jich je v tabulce vidět.
 */
class AdminLessonParticipantsSpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val definitionId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val standaloneId = Uuid.parse("00000000-0000-0000-0000-0000000000c9")

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

    private fun lesson(id: Uuid, series: Uuid?) = EventInstance(
        id = id,
        definitionId = definitionId,
        seriesId = series,
        title = "Lekce",
        description = "",
        startDateTime = LocalDateTime(2030, 1, 1, 10, 0),
        endDateTime = LocalDateTime(2030, 1, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        isCancelled = false,
    )

    private suspend fun reserve(
        id: String,
        reference: Reference,
        name: String,
        seats: Int = 1,
        price: Double = 100.0,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ): Reservation = reservationRepo.save(
        Reservation(
            id = Uuid.parse(id),
            reference = reference,
            contactName = name,
            contactEmail = "${name.lowercase()}@example.com",
            seatCount = seats,
            totalPrice = price,
            status = status,
            createdAt = Clock.System.now(),
            customValues = emptyMap(),
            paymentType = PaymentType.BANK_TRANSFER,
        )
    )

    @Test
    fun `detail lekce ukazuje i ucastniky kurzu`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))
        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(lessonId), "Primy")
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), "Kurzista")

        val detail = service().getEventDetail(lessonId, isSeries = false).getOrNull()!!

        assertEquals(listOf("Primy", "Kurzista"), detail.participants.map { it.contactName })
        assertEquals(listOf(false, true), detail.participants.map { it.fromSeries }, "kurzista musí být označený")
        assertEquals(seriesId, detail.seriesId, "proklik na kurz potřebuje id série")
    }

    @Test
    fun `omluveny z lekce v jejim seznamu neni`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))
        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Series(seriesId), "Prijde")
        val omluveny = reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), "Omluveny")
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = omluveny.id,
                instanceId = lessonId,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        val detail = service().getEventDetail(lessonId, isSeries = false).getOrNull()!!

        assertEquals(listOf("Prijde"), detail.participants.map { it.contactName })
    }

    @Test
    fun `cekatel na kurz misto na lekci nedrzi`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))
        reserve(
            "00000000-0000-0000-0000-0000000000d1",
            Reference.Series(seriesId),
            "Cekatel",
            status = Reservation.Status.WAITLISTED,
        )
        reserve(
            "00000000-0000-0000-0000-0000000000d2",
            Reference.Series(seriesId),
            "Zruseny",
            status = Reservation.Status.CANCELLED,
        )

        val detail = service().getEventDetail(lessonId, isSeries = false).getOrNull()!!

        assertTrue(detail.participants.isEmpty(), "ani čekatel, ani zrušená přihláška na kurz místo nedrží")
        assertTrue(detail.waitlist.isEmpty(), "pořadník kurzu není pořadník lekce")
    }

    @Test
    fun `vyber za kurz se do trzby lekce nepocita`() = runBlocking {
        instanceRepo.create(lesson(lessonId, seriesId))
        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(lessonId), "Primy", price = 100.0)
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), "Kurzista", price = 900.0)

        val detail = service().getEventDetail(lessonId, isSeries = false).getOrNull()!!

        assertEquals(100.0, detail.totalCollected, "kurzovné patří kurzu, ne jedné lekci")
    }

    @Test
    fun `samostatna akce zustava beze zmeny`() = runBlocking {
        instanceRepo.create(lesson(standaloneId, series = null))
        reserve("00000000-0000-0000-0000-0000000000d1", Reference.Instance(standaloneId), "Primy")
        reserve("00000000-0000-0000-0000-0000000000d2", Reference.Series(seriesId), "Kurzista")

        val detail = service().getEventDetail(standaloneId, isSeries = false).getOrNull()!!

        assertEquals(listOf("Primy"), detail.participants.map { it.contactName })
        assertNull(detail.seriesId)
    }
}
