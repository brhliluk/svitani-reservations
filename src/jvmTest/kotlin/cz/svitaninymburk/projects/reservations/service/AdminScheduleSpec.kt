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
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AdminScheduleSpec {

    private fun service(instanceRepo: InMemoryEventInstanceRepository): AdminDashboardService {
        val seriesRepo = InMemoryEventSeriesRepository()
        return AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = InMemoryReservationRepository(),
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
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, InMemoryReservationRepository()),
        )
    }

    private fun instance(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        isPublished: Boolean = true,
        isCancelled: Boolean = false,
        seriesId: Uuid? = null,
    ) = EventInstance(
        id = Uuid.random(), definitionId = Uuid.random(), seriesId = seriesId, title = title,
        description = "D", startDateTime = start, endDateTime = end, price = 100.0, capacity = 10,
        isPublished = isPublished, isCancelled = isCancelled,
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9) = LocalDateTime(year, month, day, hour, 0)

    @Test
    fun `upcoming only by default, ordered by start ascending`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        repo.create(instance("budouci-pozdeji", at(2099, 3, 1), at(2099, 3, 1, 10)))
        repo.create(instance("budouci-driv", at(2099, 1, 1), at(2099, 1, 1, 10)))
        repo.create(instance("minule", at(2020, 1, 1), at(2020, 1, 1, 10)))

        val page = service(repo).getSchedule(0, 20, includePast = false).getOrNull()!!

        assertEquals(listOf("budouci-driv", "budouci-pozdeji"), page.items.map { it.title })
        assertEquals(2L, page.totalCount)
        assertEquals(1L, page.pastCount, "pastCount se počítá i když se minulé nezobrazují")
        assertTrue(page.items.none { it.isPast })
    }

    @Test
    fun `includePast adds history and keeps ascending order`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        repo.create(instance("budouci", at(2099, 1, 1), at(2099, 1, 1, 10)))
        repo.create(instance("minule", at(2020, 1, 1), at(2020, 1, 1, 10)))

        val page = service(repo).getSchedule(0, 20, includePast = true).getOrNull()!!

        assertEquals(listOf("minule", "budouci"), page.items.map { it.title })
        assertEquals(2L, page.totalCount)
        assertEquals(1L, page.pastCount)
        assertEquals(listOf(true, false), page.items.map { it.isPast })
    }

    @Test
    fun `unpublished and cancelled terms never show up`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        repo.create(instance("verejna", at(2099, 1, 1), at(2099, 1, 1, 10)))
        repo.create(instance("skryta", at(2099, 1, 2), at(2099, 1, 2, 10), isPublished = false))
        repo.create(instance("zrusena", at(2099, 1, 3), at(2099, 1, 3, 10), isCancelled = true))

        val svc = service(repo)
        listOf(false, true).forEach { includePast ->
            val page = svc.getSchedule(0, 20, includePast = includePast).getOrNull()!!
            assertEquals(listOf("verejna"), page.items.map { it.title }, "includePast=$includePast")
            assertEquals(1L, page.totalCount)
        }
    }

    @Test
    fun `an event running right now still counts as upcoming`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        // Začalo v minulosti, končí v budoucnosti — okno se řídí koncem, ne začátkem.
        repo.create(instance("probiha", at(2020, 1, 1), at(2099, 1, 1, 10)))

        val page = service(repo).getSchedule(0, 20, includePast = false).getOrNull()!!

        assertEquals(listOf("probiha"), page.items.map { it.title })
        assertEquals(0L, page.pastCount)
        assertFalse(page.items.first().isPast)
    }

    @Test
    fun `lessons of a course are part of the schedule and carry their seriesId`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        val seriesId = Uuid.random()
        repo.create(instance("lekce", at(2099, 1, 1), at(2099, 1, 1, 10), seriesId = seriesId))
        repo.create(instance("akce", at(2099, 1, 2), at(2099, 1, 2, 10)))

        val page = service(repo).getSchedule(0, 20, includePast = false).getOrNull()!!

        assertEquals(seriesId, page.items.first { it.title == "lekce" }.seriesId)
        assertEquals(null, page.items.first { it.title == "akce" }.seriesId)
    }

    @Test
    fun `paging slices the ordered list`() = runBlocking {
        val repo = InMemoryEventInstanceRepository()
        (1..5).forEach { day ->
            repo.create(instance("d$day", at(2099, 1, day), at(2099, 1, day, 10)))
        }
        val svc = service(repo)

        val first = svc.getSchedule(0, 2, includePast = false).getOrNull()!!
        val second = svc.getSchedule(1, 2, includePast = false).getOrNull()!!
        val last = svc.getSchedule(2, 2, includePast = false).getOrNull()!!

        assertEquals(listOf("d1", "d2"), first.items.map { it.title })
        assertEquals(listOf("d3", "d4"), second.items.map { it.title })
        assertEquals(listOf("d5"), last.items.map { it.title })
        assertEquals(5L, first.totalCount)
    }

    @Test
    fun `invalid paging arguments are rejected`() = runBlocking {
        val svc = service(InMemoryEventInstanceRepository())

        assertTrue(svc.getSchedule(-1, 20, includePast = false).isLeft())
        assertTrue(svc.getSchedule(0, 0, includePast = false).isLeft())
        assertTrue(svc.getSchedule(0, 201, includePast = false).isLeft())
    }
}
