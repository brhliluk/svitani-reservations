package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.CreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.LessonConfig
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Obě cesty zakládání kurzu — nad existující šablonou (createEventSeries)
 * i se šablonou naráz (createEventAndSeries) — musí dopadnout stejně.
 */
class AdminCreateSeriesSpec {

    private val defRepo = InMemoryEventDefinitionRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val instanceRepo = InMemoryEventInstanceRepository()
    private val reservationRepo = InMemoryReservationRepository()

    private val service = AdminDashboardService(
        eventDefinitionRepository = defRepo,
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

    // 1. 6. 2026 je pondělí; kurz začíná ve středu, takže se první lekce musí posunout.
    private val start = LocalDate(2026, 5, 27)

    private fun andSeriesRequest(customLessons: List<LessonConfig>? = null) = CreateEventAndSeriesRequest(
        title = "Cvičení", description = "d",
        defaultPrice = 1500.0, defaultCapacity = 8, defaultWaitlistCapacity = 3,
        defaultDuration = 1.hours,
        startDate = start, endDate = LocalDate(2026, 6, 15), lessonCount = 3,
        lessonDayOfWeek = DayOfWeek.MONDAY,
        lessonStartTime = LocalTime(17, 0),
        lessonEndTime = LocalTime(18, 0),
        customLessons = customLessons,
        lessonRefundAmount = 90.0,
    )

    @Test
    fun `createEventAndSeries bez rozpisu vygeneruje týdenní lekce podle dne a času`() = runBlocking {
        val result = service.createEventAndSeries(andSeriesRequest())
        assertTrue(result.isRight())

        val series = seriesRepo.getAll(null).single()
        val lessons = instanceRepo.findBySeries(series.id).sortedBy { it.startDateTime }
        assertEquals(
            listOf(
                LocalDateTime(2026, 6, 1, 17, 0),
                LocalDateTime(2026, 6, 8, 17, 0),
                LocalDateTime(2026, 6, 15, 17, 0),
            ),
            lessons.map { it.startDateTime },
        )
        assertTrue(lessons.all { it.endDateTime.time == LocalTime(18, 0) })
    }

    @Test
    fun `createEventAndSeries uloží pořadník a kredit za omluvenku`() = runBlocking {
        service.createEventAndSeries(andSeriesRequest())

        val series = seriesRepo.getAll(null).single()
        assertEquals(3, series.waitlistCapacity)
        assertEquals(90.0, series.lessonRefundAmount)
        assertEquals(3, defRepo.getAll(null).single().defaultWaitlistCapacity)
        assertTrue(instanceRepo.findBySeries(series.id).all { it.waitlistCapacity == 3 })
    }

    @Test
    fun `obě cesty založí kurz se stejnými lekcemi`() = runBlocking {
        service.createEventAndSeries(andSeriesRequest())
        val viaAndSeries = seriesRepo.getAll(null).single()

        val definition = EventDefinition(
            id = Uuid.random(), title = "Cvičení", description = "d",
            defaultPrice = 1500.0, defaultCapacity = 8, defaultDuration = 1.hours,
        )
        defRepo.create(definition)
        val seriesId = service.createEventSeries(
            CreateEventSeriesRequest(
                definitionId = definition.id, title = "Cvičení", description = "d",
                price = 1500.0, capacity = 8, waitlistCapacity = 3,
                startDate = start, endDate = LocalDate(2026, 6, 15), lessonCount = 3,
                lessonDayOfWeek = DayOfWeek.MONDAY,
                lessonStartTime = LocalTime(17, 0),
                lessonEndTime = LocalTime(18, 0),
                lessonRefundAmount = 90.0,
            )
        ).getOrNull()
        assertNotNull(seriesId)
        val viaSeries = seriesRepo.get(seriesId)!!

        suspend fun lessonsOf(id: Uuid) = instanceRepo.findBySeries(id)
            .sortedBy { it.startDateTime }
            .map { listOf(it.startDateTime, it.endDateTime, it.price, it.capacity, it.waitlistCapacity, it.isDropIn) }

        assertEquals(lessonsOf(viaSeries.id), lessonsOf(viaAndSeries.id))
        assertEquals(viaSeries.lessonRefundAmount, viaAndSeries.lessonRefundAmount)
        assertEquals(viaSeries.waitlistCapacity, viaAndSeries.waitlistCapacity)
        assertEquals(viaSeries.lessonDayOfWeek, viaAndSeries.lessonDayOfWeek)
    }

    @Test
    fun `rozpis z formuláře má přednost před dnem a časem`() = runBlocking {
        service.createEventAndSeries(
            andSeriesRequest(
                customLessons = listOf(
                    LessonConfig(LocalDateTime(2026, 6, 3, 9, 0), LocalDateTime(2026, 6, 3, 10, 0), isDropIn = true),
                )
            )
        )

        val series = seriesRepo.getAll(null).single()
        val lesson = instanceRepo.findBySeries(series.id).single()
        assertEquals(LocalDateTime(2026, 6, 3, 9, 0), lesson.startDateTime)
        assertTrue(lesson.isDropIn)
    }
}
