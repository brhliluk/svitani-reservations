package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Časy akcí jsou pražské `LocalDateTime`. Hranice „už začalo“ / „už proběhlo“
 * se proto musí počítat v Praze, ať JVM běží v jakékoli zóně — produkce nemá
 * připnuté TZ. Testy schválně přepínají výchozí zónu JVM mimo Prahu.
 */
class AppTimeZoneSpec {

    private val originalDefault: java.util.TimeZone = java.util.TimeZone.getDefault()

    @AfterTest
    fun restoreDefaultTimeZone() = java.util.TimeZone.setDefault(originalDefault)

    private fun runInSystemZone(zoneId: String) = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zoneId))

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()

    private val service = AdminDashboardService(
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
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    /** Akce, která v pražském čase začíná za [startsIn] (záporné = už začala). */
    private suspend fun instanceStartingIn(startsIn: Duration, length: Duration = 1.hours): EventInstance {
        val prague = TimeZone.of("Europe/Prague")
        val start = Clock.System.now() + startsIn
        val instance = EventInstance(
            id = Uuid.random(),
            definitionId = Uuid.random(),
            title = "Akce",
            description = "",
            startDateTime = start.toLocalDateTime(prague),
            endDateTime = (start + length).toLocalDateTime(prague),
            price = 0.0,
            capacity = 10,
            isPublished = true,
        )
        instanceRepo.create(instance)
        return instance
    }

    @Test
    fun `an event that has not yet started in Prague can be cancelled even on a JVM in Tokyo`() = runBlocking {
        runInSystemZone("Asia/Tokyo")
        val instance = instanceStartingIn(1.hours)

        val result = service.cancelEventInstance(instance.id)

        assertTrue(result.isRight(), "Tokio je o 7–8 h napřed, se systémovou zónou by akce vypadala proběhlá")
    }

    @Test
    fun `an event that has already started in Prague cannot be cancelled even on a JVM in UTC`() = runBlocking {
        runInSystemZone("UTC")
        val instance = instanceStartingIn((-30).minutes)

        val result = service.cancelEventInstance(instance.id)

        assertEquals(AdminError.EventAlreadyPassed(instance.id), result.leftOrNull())
    }

    @Test
    fun `schedule marks past events by Prague time`() = runBlocking {
        runInSystemZone("Asia/Tokyo")
        val running = instanceStartingIn((-30).minutes)
        val finished = instanceStartingIn((-3).hours)

        val items = service.getSchedule(page = 0, pageSize = 50, includePast = true).getOrNull()!!.items

        assertFalse(items.single { it.id == running.id }.isPast, "právě probíhající akce ještě neproběhla")
        assertTrue(items.single { it.id == finished.id }.isPast)
    }
}
