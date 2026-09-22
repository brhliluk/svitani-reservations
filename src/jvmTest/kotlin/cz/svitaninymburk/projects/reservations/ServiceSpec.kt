package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.util.SettingsEncryption
import cz.svitaninymburk.projects.reservations.settings.maskSecret
import cz.svitaninymburk.projects.reservations.repository.settings.InMemoryAppSettingsRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.repository.event.*
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.service.AdminDashboardService
import cz.svitaninymburk.projects.reservations.service.SeriesScheduleRefresher
import cz.svitaninymburk.projects.reservations.service.RefundService
import cz.svitaninymburk.projects.reservations.service.WalletService
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonOptOut
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.service.QrCodeGeneratorService
import cz.svitaninymburk.projects.reservations.service.ConsoleEmailService
import cz.svitaninymburk.projects.reservations.service.ICalGenerator
import cz.svitaninymburk.projects.reservations.service.LectorEmailService
import cz.svitaninymburk.projects.reservations.service.PaymentTrigger
import cz.svitaninymburk.projects.reservations.service.ReservationService
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.payment.NewPaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.admin.PaymentEventsPage
import cz.svitaninymburk.projects.reservations.admin.ReservationsPage
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.admin.SeriesInstancesPage
import cz.svitaninymburk.projects.reservations.auth.BCryptHashingService
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.error.EmailError
import cz.svitaninymburk.projects.reservations.error.UserError
import cz.svitaninymburk.projects.reservations.service.UserService
import cz.svitaninymburk.projects.reservations.user.User
import arrow.core.Either
import arrow.core.right
import kotlinx.coroutines.runBlocking
import cz.svitaninymburk.projects.reservations.i18n.LectorTarget
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

private fun stubRefundService() = RefundService(
    walletService = WalletService(InMemoryWalletRepository()),
    walletEmailService = ConsoleEmailService(),
    appSettingsProvider = AppSettingsProvider.forTest(
        AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )
    ),
)

class ServiceSpec {
    @Test
    fun dummy() {
        assertTrue(true, "Dummy test")
    }
}

class AdminEditDeleteSpec {

    private fun makeService(
        defRepo: InMemoryEventDefinitionRepository = InMemoryEventDefinitionRepository(),
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        reservationRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
        walletRepo: InMemoryWalletRepository = InMemoryWalletRepository(),
        optOutRepo: InMemorySeriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
    ): AdminDashboardService {
        val walletService = WalletService(walletRepo)
        val refundService = RefundService(
            walletService = walletService,
            walletEmailService = ConsoleEmailService(),
            appSettingsProvider = AppSettingsProvider.forTest(
                AppSettings(
                    bankAccountNumber = "", fioToken = "", senderEmail = "",
                    gmailAppPassword = "", senderDisplayName = "",
                )
            ),
        )
        return AdminDashboardService(
            eventDefinitionRepository = defRepo,
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = reservationRepo,
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = InMemoryPaymentEventRepository(),
            walletService = walletService,
            refundService = refundService,
            seriesLessonOptOutRepository = optOutRepo,
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        )
    }

    private fun makeDefinition(id: Uuid = Uuid.random()) = EventDefinition(
        id = id,
        title = "Test Definition",
        description = "desc",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = 1.hours,
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        customFields = emptyList(),
    )

    private fun makeInstance(definitionId: Uuid, id: Uuid = Uuid.random(), occupiedSpots: Int = 0) = EventInstance(
        id = id,
        definitionId = definitionId,
        title = "Test Instance",
        description = "desc",
        startDateTime = LocalDateTime(2026, 6, 1, 10, 0),
        endDateTime = LocalDateTime(2026, 6, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        occupiedSpots = occupiedSpots,
    )

    private fun makeSeries(definitionId: Uuid, id: Uuid = Uuid.random(), occupiedSpots: Int = 0) = EventSeries(
        id = id,
        definitionId = definitionId,
        title = "Test Series",
        description = "desc",
        price = 500.0,
        capacity = 10,
        occupiedSpots = occupiedSpots,
        startDate = LocalDate(2026, 6, 1),
        endDate = LocalDate(2026, 8, 1),
        lessonCount = 8,
    )

    private fun makeReservation(reference: Reference, id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = reference,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        seatCount = 1,
        totalPrice = 100.0,
        status = Reservation.Status.CONFIRMED,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
    )

    // --- get-for-edit ---

    @Test
    fun `getEventDefinitionForEdit returns Left when definition not found`() = runBlocking {
        val result = makeService().getEventDefinitionForEdit(Uuid.random())
        assertTrue(result.isLeft())
    }

    @Test
    fun `getEventDefinitionForEdit returns definition when found`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val result = makeService(defRepo = defRepo).getEventDefinitionForEdit(def.id)
        assertTrue(result.isRight())
        result.onRight { assertEquals("Test Definition", it.title) }
        Unit
    }

    @Test
    fun `getEventInstanceForEdit returns Left when instance not found`() = runBlocking {
        val result = makeService().getEventInstanceForEdit(Uuid.random())
        assertTrue(result.isLeft())
    }

    @Test
    fun `getEventSeriesForEdit returns Left when series not found`() = runBlocking {
        val result = makeService().getEventSeriesForEdit(Uuid.random())
        assertTrue(result.isLeft())
    }

    @Test
    fun `getEventSeriesForEdit returns refreshed lessonCount excluding cancelled`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id) // stored lessonCount = 8
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id))
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id))
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, isCancelled = true))
        SeriesScheduleRefresher(instanceRepo, seriesRepo).refresh(series.id)
        val result = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)
            .getEventSeriesForEdit(series.id)

        assertEquals(2, result.getOrNull()?.lessonCount)
    }

    // --- get event detail ---

    @Test
    fun `getEventDetail subtitle shows refreshed lessonCount, not the stale stored value`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id) // stored lessonCount = 8
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id))
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id))
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, isCancelled = true))
        SeriesScheduleRefresher(instanceRepo, seriesRepo).refresh(series.id)
        val result = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)
            .getEventDetail(series.id, isSeries = true)

        assertTrue(result.isRight())
        val subtitle = result.getOrNull()?.subtitle
        assertNotNull(subtitle)
        assertContains(subtitle, "2 lekcí")
        assertTrue(!subtitle.contains("8 lekcí"), "subtitle should not contain the stale stored count: $subtitle")
    }

    // --- updateEventInstance ---

    @Test
    fun `updateEventInstance returns Left when instance not found`() = runBlocking {
        val request = UpdateEventInstanceRequest(
            title = "New", description = "d",
            startDateTime = LocalDateTime(2026, 7, 1, 10, 0),
            endDateTime = LocalDateTime(2026, 7, 1, 11, 0),
            price = 200.0, capacity = 5,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
        )
        val result = makeService().updateEventInstance(Uuid.random(), request)
        assertTrue(result.isLeft())
    }

    @Test
    fun `updateEventInstance updates mutable fields and preserves occupiedSpots`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val instance = makeInstance(defId, occupiedSpots = 3)
        instanceRepo.create(instance)

        val request = UpdateEventInstanceRequest(
            title = "Updated Title", description = "new desc",
            startDateTime = LocalDateTime(2026, 7, 1, 10, 0),
            endDateTime = LocalDateTime(2026, 7, 1, 12, 0),
            price = 200.0, capacity = 5,
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            customFields = emptyList(),
        )
        val result = makeService(instanceRepo = instanceRepo).updateEventInstance(instance.id, request)
        assertTrue(result.isRight())

        val updated = instanceRepo.get(instance.id)
        assertNotNull(updated)
        assertEquals("Updated Title", updated.title)
        assertEquals(200.0, updated.price)
        assertEquals(3, updated.occupiedSpots)
        assertEquals(instance.definitionId, updated.definitionId)
    }

    // --- updateEventSeries ---

    @Test
    fun `updateEventSeries returns Left when series not found`() = runBlocking {
        val request = UpdateEventSeriesRequest(
            title = "X", description = "d", price = 100.0, capacity = 5,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
        )
        val result = makeService().updateEventSeries(Uuid.random(), request)
        assertTrue(result.isLeft())
    }

    @Test
    fun `updateEventSeries updates mutable fields and preserves occupiedSpots`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries(defId, occupiedSpots = 4)
        seriesRepo.create(series)

        val request = UpdateEventSeriesRequest(
            title = "New Title", description = "new", price = 600.0, capacity = 15,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
        )
        val result = makeService(seriesRepo = seriesRepo).updateEventSeries(series.id, request)
        assertTrue(result.isRight())

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals("New Title", updated.title)
        assertEquals(600.0, updated.price)
        assertEquals(4, updated.occupiedSpots)
    }

    @Test
    fun `updateEventSeries does not touch schedule fields`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries(defId).copy(
            lessonDayOfWeek = DayOfWeek.MONDAY,
            lessonStartTime = LocalTime(17, 0),
            lessonEndTime = LocalTime(18, 0),
        )
        seriesRepo.create(series)

        val request = UpdateEventSeriesRequest(
            title = "New Title", description = "new", price = 600.0, capacity = 15,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
        )
        val result = makeService(seriesRepo = seriesRepo).updateEventSeries(series.id, request)
        assertTrue(result.isRight())

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(series.startDate, updated.startDate)
        assertEquals(series.endDate, updated.endDate)
        assertEquals(series.lessonCount, updated.lessonCount)
        assertEquals(DayOfWeek.MONDAY, updated.lessonDayOfWeek)
        assertEquals(LocalTime(17, 0), updated.lessonStartTime)
        assertEquals(LocalTime(18, 0), updated.lessonEndTime)
    }

    // --- addSeriesLesson ---

    @Test
    fun `addSeriesLesson creates instance inheriting series fields`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(
            isPublished = true,
            waitlistCapacity = 3,
            reservationDeadline = 2.hours,
            reservationDeadlineMessage = "Uzávěrka rezervací 2 hodiny předem",
        )
        seriesRepo.create(series)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.addSeriesLesson(
            AddSeriesLessonRequest(
                seriesId = series.id,
                startDateTime = LocalDateTime(2026, 6, 15, 9, 0),
                endDateTime = LocalDateTime(2026, 6, 15, 10, 0),
                isDropIn = true,
            )
        )

        assertTrue(result.isRight())
        val lessons = instanceRepo.findBySeries(series.id)
        assertEquals(1, lessons.size)
        val lesson = lessons.single()
        assertEquals(series.id, lesson.seriesId)
        assertEquals(series.definitionId, lesson.definitionId)
        assertEquals(series.title, lesson.title)
        assertEquals(series.price, lesson.price)
        assertEquals(series.capacity, lesson.capacity)
        assertEquals(series.waitlistCapacity, lesson.waitlistCapacity)
        assertEquals(series.reservationDeadline, lesson.reservationDeadline)
        assertEquals(series.reservationDeadlineMessage, lesson.reservationDeadlineMessage)
        assertEquals(series.isPublished, lesson.isPublished)
        assertTrue(lesson.isDropIn)
        assertEquals(LocalDateTime(2026, 6, 15, 9, 0), lesson.startDateTime)
    }

    @Test
    fun `addSeriesLesson returns SeriesNotFoundForAddLesson when series missing`() = runBlocking {
        val service = makeService()
        val missingId = Uuid.random()

        val result = service.addSeriesLesson(
            AddSeriesLessonRequest(
                seriesId = missingId,
                startDateTime = LocalDateTime(2026, 6, 15, 9, 0),
                endDateTime = LocalDateTime(2026, 6, 15, 10, 0),
            )
        )

        assertEquals(AdminError.SeriesNotFoundForAddLesson(missingId), result.leftOrNull())
    }

    // --- series schedule cache refresh on lesson mutations ---

    @Test
    fun `addSeriesLesson refreshes series schedule cache`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id) // stored: 2026-06-01..2026-08-01, lessonCount 8
        seriesRepo.create(series)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        service.addSeriesLesson(
            AddSeriesLessonRequest(
                seriesId = series.id,
                startDateTime = LocalDateTime(2026, 9, 15, 9, 0),
                endDateTime = LocalDateTime(2026, 9, 15, 10, 0),
            )
        )

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(LocalDate(2026, 9, 15), updated.startDate)
        assertEquals(LocalDate(2026, 9, 15), updated.endDate)
        assertEquals(1, updated.lessonCount)
    }

    @Test
    fun `updateEventInstance on a series lesson refreshes series dates`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id)
        seriesRepo.create(series)
        val first = makeInstance(def.id).copy(seriesId = series.id) // 2026-06-01 10:00
        val second = makeInstance(def.id).copy(
            seriesId = series.id,
            startDateTime = LocalDateTime(2026, 6, 8, 10, 0),
            endDateTime = LocalDateTime(2026, 6, 8, 11, 0),
        )
        instanceRepo.create(first)
        instanceRepo.create(second)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.updateEventInstance(
            second.id,
            UpdateEventInstanceRequest(
                title = second.title, description = second.description,
                startDateTime = LocalDateTime(2026, 9, 1, 10, 0),
                endDateTime = LocalDateTime(2026, 9, 1, 11, 0),
                price = second.price, capacity = second.capacity,
                allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                customFields = emptyList(),
            )
        )
        assertTrue(result.isRight())

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(LocalDate(2026, 6, 1), updated.startDate)
        assertEquals(LocalDate(2026, 9, 1), updated.endDate)
    }

    @Test
    fun `deleteEventInstance on a series lesson refreshes series dates`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id)
        seriesRepo.create(series)
        val first = makeInstance(def.id).copy(seriesId = series.id) // 2026-06-01 10:00
        val last = makeInstance(def.id).copy(
            seriesId = series.id,
            startDateTime = LocalDateTime(2026, 6, 8, 10, 0),
            endDateTime = LocalDateTime(2026, 6, 8, 11, 0),
        )
        instanceRepo.create(first)
        instanceRepo.create(last)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.deleteEventInstance(last.id, refund = false)
        assertTrue(result.isRight())

        val updated = seriesRepo.get(series.id)
        assertNotNull(updated)
        assertEquals(LocalDate(2026, 6, 1), updated.startDate)
        assertEquals(LocalDate(2026, 6, 1), updated.endDate)
        assertEquals(1, updated.lessonCount)
    }

    @Test
    fun `createEventSeries stores schedule derived from generated lessons`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        // endDate is deliberately nonsense — generation runs weekly from startDate,
        // and the stored endDate must come out as the last generated lesson's date.
        val result = service.createEventSeries(
            CreateEventSeriesRequest(
                definitionId = def.id,
                title = "Course", description = "d",
                price = 500.0, capacity = 10,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 12, 31),
                lessonCount = 3,
                lessonDayOfWeek = DayOfWeek.MONDAY,
                lessonStartTime = LocalTime(17, 0),
                lessonEndTime = LocalTime(18, 0),
            )
        )
        assertTrue(result.isRight())
        val seriesId = result.getOrNull()
        assertNotNull(seriesId)

        val stored = seriesRepo.get(seriesId)
        assertNotNull(stored)
        assertEquals(LocalDate(2026, 6, 1), stored.startDate)   // first Monday
        assertEquals(LocalDate(2026, 6, 15), stored.endDate)    // third Monday
        assertEquals(3, stored.lessonCount)
        assertEquals(DayOfWeek.MONDAY, stored.lessonDayOfWeek)
    }

    // --- cena za lekci ---

    @Test
    fun `createEventSeries prices generated lessons with lessonPrice when set`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.createEventSeries(
            CreateEventSeriesRequest(
                definitionId = def.id,
                title = "Course", description = "d",
                price = 1500.0, capacity = 10,
                lessonPrice = 250.0,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 6, 15),
                lessonCount = 3,
                lessonDayOfWeek = DayOfWeek.MONDAY,
                lessonStartTime = LocalTime(17, 0),
                lessonEndTime = LocalTime(18, 0),
            )
        )
        val seriesId = result.getOrNull()
        assertNotNull(seriesId)

        assertEquals(250.0, seriesRepo.get(seriesId)?.lessonPrice)
        val lessons = instanceRepo.findBySeries(seriesId)
        assertEquals(3, lessons.size)
        assertTrue(lessons.all { it.price == 250.0 }, "lekce mají mít cenu za lekci, ne cenu kurzu")
    }

    @Test
    fun `createEventSeries prices custom lessons with lessonPrice when set`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.createEventSeries(
            CreateEventSeriesRequest(
                definitionId = def.id,
                title = "Course", description = "d",
                price = 1500.0, capacity = 10,
                lessonPrice = 250.0,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 6, 8),
                lessonCount = 2,
                customLessons = listOf(
                    LessonConfig(LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0), isDropIn = true),
                    LessonConfig(LocalDateTime(2026, 6, 8, 17, 0), LocalDateTime(2026, 6, 8, 18, 0)),
                ),
            )
        )
        val seriesId = result.getOrNull()
        assertNotNull(seriesId)

        val lessons = instanceRepo.findBySeries(seriesId)
        assertEquals(2, lessons.size)
        assertTrue(lessons.all { it.price == 250.0 }, "lekce mají mít cenu za lekci, ne cenu kurzu")
    }

    @Test
    fun `createEventSeries falls back to course price when lessonPrice is absent`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.createEventSeries(
            CreateEventSeriesRequest(
                definitionId = def.id,
                title = "Course", description = "d",
                price = 1500.0, capacity = 10,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 6, 8),
                lessonCount = 2,
                lessonDayOfWeek = DayOfWeek.MONDAY,
                lessonStartTime = LocalTime(17, 0),
                lessonEndTime = LocalTime(18, 0),
            )
        )
        val seriesId = result.getOrNull()
        assertNotNull(seriesId)

        assertNull(seriesRepo.get(seriesId)?.lessonPrice)
        assertTrue(instanceRepo.findBySeries(seriesId).all { it.price == 1500.0 })
    }

    @Test
    fun `createEventAndSeries prices lessons with lessonPrice when set`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.createEventAndSeries(
            CreateEventAndSeriesRequest(
                title = "Course", description = "d",
                defaultPrice = 1500.0, defaultCapacity = 10,
                defaultDuration = 1.hours,
                lessonPrice = 250.0,
                startDate = LocalDate(2026, 6, 1),
                endDate = LocalDate(2026, 6, 8),
                lessonCount = 2,
                customLessons = listOf(
                    LessonConfig(LocalDateTime(2026, 6, 1, 17, 0), LocalDateTime(2026, 6, 1, 18, 0), isDropIn = true),
                    LessonConfig(LocalDateTime(2026, 6, 8, 17, 0), LocalDateTime(2026, 6, 8, 18, 0)),
                ),
            )
        )
        assertTrue(result.isRight())

        val series = seriesRepo.getAll(null).single()
        assertEquals(250.0, series.lessonPrice)
        assertTrue(instanceRepo.findBySeries(series.id).all { it.price == 250.0 })
    }

    @Test
    fun `updateEventSeries reprices all lessons when lessonPrice changes`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(lessonPrice = 250.0)
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, price = 250.0))
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, price = 400.0)) // ručně upravená lekce
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.updateEventSeries(
            series.id,
            UpdateEventSeriesRequest(
                title = series.title, description = series.description,
                price = series.price, capacity = series.capacity,
                allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                customFields = emptyList(),
                lessonPrice = 300.0,
            )
        )
        assertTrue(result.isRight())

        assertEquals(300.0, seriesRepo.get(series.id)?.lessonPrice)
        assertTrue(
            instanceRepo.findBySeries(series.id).all { it.price == 300.0 },
            "změna ceny za lekci přepíše cenu všech lekcí kurzu",
        )
    }

    @Test
    fun `updateEventSeries reprices lessons back to the course price when lessonPrice is cleared`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(lessonPrice = 250.0) // price = 500.0
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, price = 250.0))
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        service.updateEventSeries(
            series.id,
            UpdateEventSeriesRequest(
                title = series.title, description = series.description,
                price = series.price, capacity = series.capacity,
                allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                customFields = emptyList(),
                lessonPrice = null,
            )
        )

        assertNull(seriesRepo.get(series.id)?.lessonPrice)
        assertEquals(500.0, instanceRepo.findBySeries(series.id).single().price)
    }

    @Test
    fun `updateEventSeries leaves lesson prices alone when lessonPrice is unchanged`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(lessonPrice = 250.0)
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(def.id).copy(seriesId = series.id, price = 400.0))
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        service.updateEventSeries(
            series.id,
            UpdateEventSeriesRequest(
                title = "Nový název", description = series.description,
                price = series.price, capacity = series.capacity,
                allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                customFields = emptyList(),
                lessonPrice = 250.0,
            )
        )

        assertEquals(400.0, instanceRepo.findBySeries(series.id).single().price)
    }

    @Test
    fun `addSeriesLesson prices the new lesson with the series lessonPrice`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(lessonPrice = 250.0)
        seriesRepo.create(series)
        val service = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)

        val result = service.addSeriesLesson(
            AddSeriesLessonRequest(
                seriesId = series.id,
                startDateTime = LocalDateTime(2026, 6, 22, 17, 0),
                endDateTime = LocalDateTime(2026, 6, 22, 18, 0),
                isDropIn = true,
            )
        )
        val lessonId = result.getOrNull()
        assertNotNull(lessonId)

        assertEquals(250.0, instanceRepo.get(lessonId)?.price)
    }

    @Test
    fun `updateEventDefinition propagation keeps the lesson price of series lessons`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val def = makeDefinition()
        defRepo.create(def)
        val series = makeSeries(def.id).copy(lessonPrice = 250.0)
        seriesRepo.create(series)
        val lesson = makeInstance(def.id).copy(seriesId = series.id, price = 250.0)
        instanceRepo.create(lesson)
        val standalone = makeInstance(def.id) // mimo kurz — propagace ho přecenit má
        instanceRepo.create(standalone)

        val result = makeService(defRepo = defRepo, seriesRepo = seriesRepo, instanceRepo = instanceRepo)
            .updateEventDefinition(
                def.id,
                UpdateEventDefinitionRequest(
                    title = "T", description = "d",
                    defaultPrice = 999.0, defaultCapacity = 10,
                    defaultDuration = 1.hours,
                    allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                    customFields = emptyList(),
                    propagateToChildren = true,
                ),
            )
        assertTrue(result.isRight())

        assertEquals(250.0, instanceRepo.get(lesson.id)?.price, "propagace šablony nesmí smazat cenu za lekci")
        assertEquals(999.0, instanceRepo.get(standalone.id)?.price)
    }

    // --- updateEventDefinition with propagation ---

    @Test
    fun `updateEventDefinition propagates title to child instances and series when requested`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()

        val def = makeDefinition()
        defRepo.create(def)
        val instance = makeInstance(def.id)
        instanceRepo.create(instance)
        val series = makeSeries(def.id)
        seriesRepo.create(series)

        val request = UpdateEventDefinitionRequest(
            title = "Propagated Title", description = "new desc",
            defaultPrice = 200.0, defaultCapacity = 20,
            defaultDuration = 2.hours,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
            propagateToChildren = true,
        )
        val result = makeService(defRepo = defRepo, instanceRepo = instanceRepo, seriesRepo = seriesRepo)
            .updateEventDefinition(def.id, request)
        assertTrue(result.isRight())

        assertEquals("Propagated Title", instanceRepo.get(instance.id)?.title)
        assertEquals("Propagated Title", seriesRepo.get(series.id)?.title)
    }

    @Test
    fun `updateEventDefinition does not touch children when propagate is false`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()

        val def = makeDefinition()
        defRepo.create(def)
        val instance = makeInstance(def.id)
        instanceRepo.create(instance)

        val request = UpdateEventDefinitionRequest(
            title = "New Template Title", description = "d",
            defaultPrice = 100.0, defaultCapacity = 10,
            defaultDuration = 1.hours,
            allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
            customFields = emptyList(),
            propagateToChildren = false,
        )
        makeService(defRepo = defRepo, instanceRepo = instanceRepo).updateEventDefinition(def.id, request)

        assertEquals("Test Instance", instanceRepo.get(instance.id)?.title)
    }

    // --- deleteEventInstance ---

    @Test
    fun `deleteEventInstance returns Left when instance not found`() = runBlocking {
        val result = makeService().deleteEventInstance(Uuid.random())
        assertTrue(result.isLeft())
    }

    @Test
    fun `deleteEventInstance deletes instance and cancels active reservations`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()

        val instance = makeInstance(defId)
        instanceRepo.create(instance)
        val res = makeReservation(Reference.Instance(instance.id))
        reservationRepo.save(res)

        val result = makeService(instanceRepo = instanceRepo, reservationRepo = reservationRepo)
            .deleteEventInstance(instance.id)
        assertTrue(result.isRight())

        assertNull(instanceRepo.get(instance.id))
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(res.id)?.status)
    }

    @Test
    fun `deleteEventInstance does not cancel already-cancelled reservations twice`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()

        val instance = makeInstance(defId)
        instanceRepo.create(instance)
        val cancelled = makeReservation(Reference.Instance(instance.id)).copy(status = Reservation.Status.CANCELLED)
        reservationRepo.save(cancelled)

        makeService(instanceRepo = instanceRepo, reservationRepo = reservationRepo).deleteEventInstance(instance.id)

        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(cancelled.id)?.status)
    }

    // --- deleteEventSeries ---

    @Test
    fun `deleteEventSeries deletes series and cancels its active reservations`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()

        val series = makeSeries(defId)
        seriesRepo.create(series)
        val res = makeReservation(Reference.Series(series.id))
        reservationRepo.save(res)

        val result = makeService(seriesRepo = seriesRepo, reservationRepo = reservationRepo)
            .deleteEventSeries(series.id)
        assertTrue(result.isRight())

        assertNull(seriesRepo.get(series.id))
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(res.id)?.status)
    }

    @Test
    fun `deleteEventSeries also deletes its lesson instances (no orphans)`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()

        val series = makeSeries(defId)
        seriesRepo.create(series)
        val lesson1 = makeInstance(defId).copy(seriesId = series.id)
        val lesson2 = makeInstance(defId).copy(seriesId = series.id)
        instanceRepo.create(lesson1)
        instanceRepo.create(lesson2)

        val result = makeService(seriesRepo = seriesRepo, instanceRepo = instanceRepo)
            .deleteEventSeries(series.id)
        assertTrue(result.isRight())

        assertNull(seriesRepo.get(series.id))
        // Regression: lessons must be deleted with the series, not left orphaned.
        assertNull(instanceRepo.get(lesson1.id))
        assertNull(instanceRepo.get(lesson2.id))
        assertTrue(instanceRepo.findBySeries(series.id).isEmpty())
    }

    @Test
    fun `deleteEventSeries cancels a per-lesson (drop-in) reservation`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()

        val series = makeSeries(defId)
        seriesRepo.create(series)
        val lesson = makeInstance(defId).copy(seriesId = series.id, isDropIn = true)
        instanceRepo.create(lesson)
        val lessonRes = makeReservation(Reference.Instance(lesson.id))
        reservationRepo.save(lessonRes)

        val result = makeService(seriesRepo = seriesRepo, instanceRepo = instanceRepo, reservationRepo = reservationRepo)
            .deleteEventSeries(series.id)
        assertTrue(result.isRight())

        assertNull(instanceRepo.get(lesson.id))
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(lessonRes.id)?.status)
    }

    @Test
    fun `getEventDetail subtitle shows refreshed startDate, not the pre-refresh stored value`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()

        // Stored startDate deliberately diverges from the actual lessons.
        val series = makeSeries(defId).copy(startDate = LocalDate(2026, 9, 14))
        seriesRepo.create(series)
        instanceRepo.create(makeInstance(defId).copy(
            seriesId = series.id,
            startDateTime = LocalDateTime(2026, 7, 6, 14, 30),
            endDateTime = LocalDateTime(2026, 7, 6, 15, 30),
        ))
        instanceRepo.create(makeInstance(defId).copy(
            seriesId = series.id,
            startDateTime = LocalDateTime(2026, 7, 13, 14, 30),
            endDateTime = LocalDateTime(2026, 7, 13, 15, 30),
        ))
        SeriesScheduleRefresher(instanceRepo, seriesRepo).refresh(series.id)

        val result = makeService(seriesRepo = seriesRepo, instanceRepo = instanceRepo)
            .getEventDetail(series.id, isSeries = true)
        assertTrue(result.isRight())
        val subtitle = result.getOrNull()!!.subtitle
        assertTrue(subtitle.contains("2026-07-06"), "expected first lesson date, was: $subtitle")
        assertTrue(!subtitle.contains("2026-09-14"), "must not show stale stored startDate, was: $subtitle")
    }

    // --- deleteEventDefinition ---

    @Test
    fun `deleteEventDefinition cascades to child instances and series with reservations`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()

        val def = makeDefinition()
        defRepo.create(def)
        val instance = makeInstance(def.id)
        instanceRepo.create(instance)
        val series = makeSeries(def.id)
        seriesRepo.create(series)
        val instanceRes = makeReservation(Reference.Instance(instance.id))
        reservationRepo.save(instanceRes)
        val seriesRes = makeReservation(Reference.Series(series.id))
        reservationRepo.save(seriesRes)

        val result = makeService(
            defRepo = defRepo,
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
        ).deleteEventDefinition(def.id)
        assertTrue(result.isRight())

        assertNull(defRepo.get(def.id))
        assertNull(instanceRepo.get(instance.id))
        assertNull(seriesRepo.get(series.id))
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(instanceRes.id)?.status)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(seriesRes.id)?.status)
    }

    // --- delete refunds ---

    @Test
    fun `deleteEventInstance refunds registered user's paid amount to wallet`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val instance = makeInstance(defId)
        instanceRepo.create(instance)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Instance(instance.id))
            .copy(registeredUserId = userId, paidAmount = 100.0)
        reservationRepo.save(res)

        val result = makeService(
            instanceRepo = instanceRepo, reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).deleteEventInstance(instance.id)
        assertTrue(result.isRight())

        val wallet = walletRepo.findByRegisteredUserId(userId)
        assertNotNull(wallet)
        assertEquals(100.0, wallet.balance)
    }

    @Test
    fun `deleteEventInstance refunds anonymous reservation into a new wallet`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val instance = makeInstance(defId)
        instanceRepo.create(instance)
        val res = makeReservation(Reference.Instance(instance.id)).copy(paidAmount = 100.0)
        reservationRepo.save(res)

        makeService(
            instanceRepo = instanceRepo, reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).deleteEventInstance(instance.id)

        val wallets = walletRepo.findAll(0, 10)
        assertEquals(1, wallets.size)
        assertEquals(100.0, wallets.first().balance)
        assertEquals("jan@test.com", wallets.first().ownerEmail)
    }

    @Test
    fun `deleteEventInstance does not create a wallet for unpaid reservation`() = runBlocking {
        val defId = Uuid.random()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val instance = makeInstance(defId)
        instanceRepo.create(instance)
        reservationRepo.save(makeReservation(Reference.Instance(instance.id)))

        makeService(
            instanceRepo = instanceRepo, reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).deleteEventInstance(instance.id)

        assertEquals(0, walletRepo.findAll(0, 10).size)
    }

    @Test
    fun `deleteEventSeries refunds paid reservations to wallets`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries(defId)
        seriesRepo.create(series)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Series(series.id))
            .copy(registeredUserId = userId, paidAmount = 100.0)
        reservationRepo.save(res)

        val result = makeService(
            seriesRepo = seriesRepo, reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).deleteEventSeries(series.id)
        assertTrue(result.isRight())

        val wallet = walletRepo.findByRegisteredUserId(userId)
        assertNotNull(wallet)
        assertEquals(100.0, wallet.balance)
    }

    private fun makeSeriesInstance(seriesId: Uuid, id: Uuid = Uuid.random()) = EventInstance(
        id = id,
        definitionId = Uuid.random(),
        title = "Lesson",
        description = "desc",
        startDateTime = LocalDateTime(2026, 6, 10, 10, 0),
        endDateTime = LocalDateTime(2026, 6, 10, 11, 0),
        price = 100.0,
        capacity = 10,
        seriesId = seriesId,
    )

    @Test
    fun `deleteEventDefinition refunds paid reservations across child instances and series`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val def = makeDefinition()
        defRepo.create(def)
        val instance = makeInstance(def.id)
        instanceRepo.create(instance)
        val series = makeSeries(def.id)
        seriesRepo.create(series)

        val userId1 = Uuid.random()
        val userId2 = Uuid.random()
        val instanceRes = makeReservation(Reference.Instance(instance.id))
            .copy(registeredUserId = userId1, paidAmount = 100.0)
        val seriesRes = makeReservation(Reference.Series(series.id))
            .copy(registeredUserId = userId2, paidAmount = 200.0)
        reservationRepo.save(instanceRes)
        reservationRepo.save(seriesRes)

        val result = makeService(
            defRepo = defRepo,
            instanceRepo = instanceRepo,
            seriesRepo = seriesRepo,
            reservationRepo = reservationRepo,
            walletRepo = walletRepo,
        ).deleteEventDefinition(def.id)
        assertTrue(result.isRight())

        assertEquals(100.0, walletRepo.findByRegisteredUserId(userId1)?.balance)
        assertEquals(200.0, walletRepo.findByRegisteredUserId(userId2)?.balance)
    }

    // --- cancelSeriesLesson refunds ---

    @Test
    fun `cancelSeriesLesson refunds lessonRefundAmount to active enrollees`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries(defId).copy(lessonRefundAmount = 60.0)
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id)
        instanceRepo.create(lesson)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Series(series.id))
            .copy(registeredUserId = userId, paidAmount = 500.0)
        reservationRepo.save(res)

        val result = makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).cancelSeriesLesson(lesson.id)
        assertTrue(result.isRight())

        assertEquals(60.0, walletRepo.findByRegisteredUserId(userId)?.balance)
    }

    @Test
    fun `cancelSeriesLesson does not refund enrollee who already opted out of that lesson`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()
        val optOutRepo = InMemorySeriesLessonOptOutRepository()

        val series = makeSeries(defId).copy(lessonRefundAmount = 60.0)
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id)
        instanceRepo.create(lesson)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Series(series.id))
            .copy(registeredUserId = userId, paidAmount = 500.0)
        reservationRepo.save(res)
        optOutRepo.save(
            SeriesLessonOptOut(
                id = Uuid.random(),
                reservationId = res.id,
                instanceId = lesson.id,
                optedOutAt = Clock.System.now(),
                isLateCancellation = false,
            )
        )

        makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo, optOutRepo = optOutRepo,
        ).cancelSeriesLesson(lesson.id)

        assertNull(walletRepo.findByRegisteredUserId(userId))
    }

    @Test
    fun `cancelSeriesLesson does not refund when series has no lessonRefundAmount`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries(defId) // lessonRefundAmount defaults to null
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id)
        instanceRepo.create(lesson)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Series(series.id))
            .copy(registeredUserId = userId, paidAmount = 500.0)
        reservationRepo.save(res)

        makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).cancelSeriesLesson(lesson.id)

        assertNull(walletRepo.findByRegisteredUserId(userId))
    }

    @Test
    fun `cancelSeriesLesson refunds the whole paid amount to a drop-in reservation on that lesson`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        // 60 Kč je kredit za odhlášení z kurzu — na koho si koupil jen tuhle
        // lekci, se nevztahuje, ten má dostat celých 100 Kč, co zaplatil.
        val series = makeSeries(defId).copy(lessonRefundAmount = 60.0)
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id).copy(isDropIn = true)
        instanceRepo.create(lesson)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Instance(lesson.id))
            .copy(registeredUserId = userId, paidAmount = 100.0)
        reservationRepo.save(res)

        val result = makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).cancelSeriesLesson(lesson.id)
        assertTrue(result.isRight())

        assertEquals(100.0, walletRepo.findByRegisteredUserId(userId)?.balance)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(res.id)?.status)
    }

    @Test
    fun `cancelSeriesLesson refunds a drop-in reservation even when the series has no lessonRefundAmount`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries(defId) // lessonRefundAmount defaults to null
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id).copy(isDropIn = true)
        instanceRepo.create(lesson)
        val userId = Uuid.random()
        val res = makeReservation(Reference.Instance(lesson.id))
            .copy(registeredUserId = userId, paidAmount = 100.0)
        reservationRepo.save(res)

        makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo,
        ).cancelSeriesLesson(lesson.id)

        assertEquals(100.0, walletRepo.findByRegisteredUserId(userId)?.balance)
        assertEquals(Reservation.Status.CANCELLED, reservationRepo.findById(res.id)?.status)
    }

    @Test
    fun `cancelSeriesLesson on an already cancelled lesson refunds nothing more`() = runBlocking {
        val defId = Uuid.random()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val walletRepo = InMemoryWalletRepository()

        val series = makeSeries(defId).copy(lessonRefundAmount = 60.0)
        seriesRepo.create(series)
        val lesson = makeSeriesInstance(series.id)
        instanceRepo.create(lesson)
        val enrolleeId = Uuid.random()
        reservationRepo.save(
            makeReservation(Reference.Series(series.id))
                .copy(registeredUserId = enrolleeId, paidAmount = 500.0)
        )
        val dropInId = Uuid.random()
        reservationRepo.save(
            makeReservation(Reference.Instance(lesson.id))
                .copy(registeredUserId = dropInId, paidAmount = 100.0)
        )

        val service = makeService(
            instanceRepo = instanceRepo, seriesRepo = seriesRepo,
            reservationRepo = reservationRepo, walletRepo = walletRepo,
        )
        service.cancelSeriesLesson(lesson.id)
        service.cancelSeriesLesson(lesson.id)

        assertEquals(60.0, walletRepo.findByRegisteredUserId(enrolleeId)?.balance)
        assertEquals(100.0, walletRepo.findByRegisteredUserId(dropInId)?.balance)
    }
}

class ICalGeneratorSpec {

    private fun makeInstance() = EventInstance(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        definitionId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        title = "Hlídání dětí",
        description = "Popis",
        startDateTime = LocalDateTime(2026, 5, 8, 9, 0),
        endDateTime = LocalDateTime(2026, 5, 8, 10, 0),
        price = 120.0,
        capacity = 10,
    )

    private fun makeSeries() = EventSeries(
        id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
        definitionId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        title = "Kroužek angličtiny",
        description = "Kurz",
        price = 500.0,
        capacity = 8,
        startDate = LocalDate(2026, 9, 3),
        endDate = LocalDate(2026, 12, 17),
        lessonCount = 15,
        lessonDayOfWeek = DayOfWeek.WEDNESDAY,
        lessonStartTime = LocalTime(9, 0),
        lessonEndTime = LocalTime(10, 0),
    )

    @Test
    fun `instance ical contains VEVENT with correct summary`() {
        val ical = ICalGenerator.forInstance(makeInstance(), Uuid.parse("00000000-0000-0000-0000-000000000099"), "https://example.cz")
        assertContains(ical, "BEGIN:VEVENT")
        assertContains(ical, "SUMMARY:Hlídání dětí")
        assertContains(ical, "END:VEVENT")
        assertContains(ical, "METHOD:REQUEST")
    }

    @Test
    fun `instance ical contains DTSTART with correct date`() {
        val ical = ICalGenerator.forInstance(makeInstance(), Uuid.parse("00000000-0000-0000-0000-000000000099"), "https://example.cz")
        assertContains(ical, "20260508T")
    }

    @Test
    fun `series ical contains RRULE with lesson count`() {
        val ical = ICalGenerator.forSeries(makeSeries(), Uuid.parse("00000000-0000-0000-0000-000000000099"), "https://example.cz")
        assertContains(ical, "RRULE:FREQ=WEEKLY;COUNT=15")
    }

    @Test
    fun `series without schedule falls back to all-day event`() {
        val series = makeSeries().copy(lessonDayOfWeek = null, lessonStartTime = null, lessonEndTime = null)
        val ical = ICalGenerator.forSeries(series, Uuid.parse("00000000-0000-0000-0000-000000000099"), "https://example.cz")
        assertContains(ical, "DTSTART;VALUE=DATE:20260903")
    }
}

class SettingsEncryptionSpec {
    private val testKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `encrypt then decrypt returns original`() {
        val plaintext = "supersecret-fio-token"
        val encrypted = SettingsEncryption.encrypt(plaintext, testKey)
        val decrypted = SettingsEncryption.decrypt(encrypted, testKey)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `two encryptions produce different ciphertexts`() {
        val plaintext = "same-value"
        val enc1 = SettingsEncryption.encrypt(plaintext, testKey)
        val enc2 = SettingsEncryption.encrypt(plaintext, testKey)
        assertTrue(enc1 != enc2, "IV must be random, producing distinct ciphertexts")
    }

    @Test
    fun `decrypt rejects malformed stored value`() {
        try {
            SettingsEncryption.decrypt("not-valid-format", testKey)
            assertTrue(false, "Expected exception")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}

class MaskSecretSpec {
    @Test fun `long secret shows first3 bullets last3`() {
        assertEquals("sup•••n23", maskSecret("supersecretn23"))
    }
    @Test fun `short 5-char secret pads bullets to 9 total`() {
        val result = maskSecret("hello")
        assertEquals(9, result.length)
        assertTrue(result.startsWith("hel"))
        assertTrue(result.endsWith("lo"))
    }
    @Test fun `very short 2-char secret pads bullets to 9 total`() {
        val result = maskSecret("ab")
        assertEquals(9, result.length) // "ab" + 7 bullets
        assertTrue(result.startsWith("ab"))
    }
    @Test fun `empty string returns 3 bullets`() {
        assertEquals("•••", maskSecret(""))
    }
}

class AppSettingsRepositorySpec {
    private val defaultSettings = AppSettings(
        bankAccountNumber = "2003487968/2010",
        fioToken = "test-fio-token",
        senderEmail = "test@example.com",
        gmailAppPassword = "test-app-password",
        senderDisplayName = "Test Sender",
    )

    @Test fun `load returns seeded settings`() {
        val repo = InMemoryAppSettingsRepository(defaultSettings)
        val loaded = repo.load()
        assertEquals(defaultSettings, loaded)
    }

    @Test fun `save then load returns updated settings`() {
        val repo = InMemoryAppSettingsRepository(defaultSettings)
        val updated = defaultSettings.copy(bankAccountNumber = "NEW/9999")
        runBlocking { repo.save(updated) }
        assertEquals("NEW/9999", repo.load().bankAccountNumber)
    }
}

class PaymentEventSpec {

    private val paymentRepo = InMemoryPaymentEventRepository()
    private val reservationRepo = InMemoryReservationRepository()

    private fun makeReservation(id: Uuid = Uuid.random()) = Reservation(
        id = id,
        reference = Reference.Instance(Uuid.random()),
        contactName = "Jana Testová",
        contactEmail = "jana@test.com",
        seatCount = 1,
        totalPrice = 300.0,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.ON_SITE,
    )

    @Test
    fun `markReservationAsPaid inserts a payment event`() = runBlocking {
        val reservation = makeReservation()
        reservationRepo.save(reservation)

        // service will be created in Task 6 — this test will fail to compile until then
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = reservationRepo,
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
            walletService = WalletService(InMemoryWalletRepository()),
            refundService = stubRefundService(),
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
        )

        val result = service.markReservationAsPaid(reservation.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        val inserted = paymentRepo.insertedEvents()
        assertEquals(1, inserted.size, "Expected exactly one payment event")
        assertEquals(reservation.id.toString(), inserted[0].reservationId)
        assertEquals(300.0, inserted[0].amount)
        assertEquals(PaymentEvent.Source.MANUAL_ADMIN, inserted[0].source)
        assertEquals(PaymentType.ON_SITE, inserted[0].type)
    }

    @Test
    fun `getPaymentEvents returns paginated results`() = runBlocking {
        val now = Clock.System.now()
        paymentRepo.seed(PaymentEvent("id1", Uuid.random().toString(), "Alice", 100.0, "CZK", PaymentType.BANK_TRANSFER, PaymentEvent.Source.AUTO_FIO,     now))
        paymentRepo.seed(PaymentEvent("id2", Uuid.random().toString(), "Bob",   200.0, "CZK", PaymentType.ON_SITE,       PaymentEvent.Source.MANUAL_ADMIN, now))

        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = InMemoryReservationRepository(),
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
            walletService = WalletService(InMemoryWalletRepository()),
            refundService = stubRefundService(),
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, InMemoryReservationRepository()),
        )

        val result = service.getPaymentEvents(page = 0, pageSize = 10)
        assertTrue(result.isRight())
        result.onRight { page ->
            assertEquals(2, page.totalCount)
            assertEquals(2, page.items.size)
            assertEquals(0, page.page)
            assertEquals(10, page.pageSize)
        }
        Unit
    }

    @Test
    fun `getPaymentEvents respects pagination`() = runBlocking {
        val now = Clock.System.now()
        repeat(5) { i ->
            paymentRepo.seed(PaymentEvent("id$i", Uuid.random().toString(), "User$i", 100.0 * i, "CZK", PaymentType.BANK_TRANSFER, PaymentEvent.Source.AUTO_FIO, now))
        }

        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = seriesRepo,
            eventInstanceRepository = instanceRepo,
            reservationRepository = InMemoryReservationRepository(),
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
            walletService = WalletService(InMemoryWalletRepository()),
            refundService = stubRefundService(),
            seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
            seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
            waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, InMemoryReservationRepository()),
        )

        val page0 = service.getPaymentEvents(page = 0, pageSize = 3)
        val page1 = service.getPaymentEvents(page = 1, pageSize = 3)

        assertTrue(page0.isRight())
        assertTrue(page1.isRight())
        page0.onRight { assertEquals(3, it.items.size) }
        page1.onRight { assertEquals(2, it.items.size) }
        Unit
    }
}

class PaginationSpec {

    private fun makeService(
        defRepo: InMemoryEventDefinitionRepository = InMemoryEventDefinitionRepository(),
        instanceRepo: InMemoryEventInstanceRepository = InMemoryEventInstanceRepository(),
        seriesRepo: InMemoryEventSeriesRepository = InMemoryEventSeriesRepository(),
        reservationRepo: InMemoryReservationRepository = InMemoryReservationRepository(),
    ) = AdminDashboardService(
        eventDefinitionRepository = defRepo,
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        refundService = stubRefundService(),
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    private fun makeDefinition(title: String = "Def", id: Uuid = Uuid.random()) = EventDefinition(
        id = id,
        title = title,
        description = "",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = kotlin.time.Duration.parse("1h"),
        allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
        customFields = emptyList(),
        ownerEmails = emptyList(),
    )

    private fun makeReservation(
        id: Uuid = Uuid.random(),
        contactName: String = "Jan Novák",
        contactEmail: String = "jan@example.com",
        vs: String? = null,
    ) = Reservation(
        id = id,
        reference = Reference.Instance(Uuid.random()),
        registeredUserId = null,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = null,
        seatCount = 1,
        totalPrice = 100.0,
        paidAmount = 0.0,
        status = Reservation.Status.PENDING_PAYMENT,
        createdAt = kotlin.time.Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.BANK_TRANSFER,
        variableSymbol = vs,
        paymentPairingToken = null,
        locale = "cs",
    )

    @Test
    fun `getAllReservations returns page with totalCount`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        repeat(25) { i -> reservationRepo.save(makeReservation(contactName = "User $i")) }
        val service = makeService(reservationRepo = reservationRepo)

        val result = service.getAllReservations(null, 0, 20)
        assertTrue(result.isRight())
        val page = result.getOrNull()!!
        assertEquals(20, page.items.size)
        assertEquals(25L, page.totalCount)
        assertEquals(0, page.page)
        assertEquals(20, page.pageSize)
    }

    @Test
    fun `getAllReservations second page returns remaining items`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        repeat(25) { i -> reservationRepo.save(makeReservation(contactName = "User $i")) }
        val service = makeService(reservationRepo = reservationRepo)

        val result = service.getAllReservations(null, 1, 20)
        val page = result.getOrNull()!!
        assertEquals(5, page.items.size)
        assertEquals(25L, page.totalCount)
    }

    @Test
    fun `getAllReservations filters by searchQuery`() = runBlocking {
        val reservationRepo = InMemoryReservationRepository()
        reservationRepo.save(makeReservation(contactName = "Jana Nováková"))
        reservationRepo.save(makeReservation(contactName = "Petr Svoboda"))
        val service = makeService(reservationRepo = reservationRepo)

        val result = service.getAllReservations("jana", 0, 20)
        val page = result.getOrNull()!!
        assertEquals(1, page.items.size)
        assertEquals(1L, page.totalCount)
    }

    @Test
    fun `getAllEvents returns page with totalDefinitionCount`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        repeat(25) { i -> defRepo.create(makeDefinition(title = "Event $i")) }
        val service = makeService(defRepo = defRepo)

        val result = service.getAllEvents(0, 20)
        assertTrue(result.isRight())
        val page = result.getOrNull()!!
        assertEquals(25L, page.totalDefinitionCount)
        assertEquals(0, page.page)
        // items contains 20 definition rows (no children)
        assertEquals(20, page.items.filter { it.isDefinitionOnly }.size)
    }

    @Test
    fun `getAllEvents second page returns remaining definitions`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        repeat(25) { i -> defRepo.create(makeDefinition(title = "Event $i")) }
        val service = makeService(defRepo = defRepo)

        val result = service.getAllEvents(1, 20)
        val page = result.getOrNull()!!
        assertEquals(5, page.items.filter { it.isDefinitionOnly }.size)
    }

    @Test
    fun `getSeriesInstances returns page with totalCount`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesId = Uuid.random()
        val defId = Uuid.random()
        defRepo.create(makeDefinition(id = defId))
        repeat(15) { i ->
            instanceRepo.create(
                EventInstance(
                    id = Uuid.random(),
                    definitionId = defId,
                    seriesId = seriesId,
                    title = "Lekce $i",
                    description = "",
                    startDateTime = LocalDateTime(2026, 1, i + 1, 10, 0),
                    endDateTime = LocalDateTime(2026, 1, i + 1, 11, 0),
                    price = 100.0,
                    capacity = 10,
                    allowedPaymentTypes = listOf(PaymentType.BANK_TRANSFER),
                    customFields = emptyList(),
                    ownerEmails = emptyList(),
                )
            )
        }
        val service = makeService(defRepo = defRepo, instanceRepo = instanceRepo)

        val result = service.getSeriesInstances(seriesId, 0, 10)
        assertTrue(result.isRight())
        val page = result.getOrNull()!!
        assertEquals(10, page.items.size)
        assertEquals(15L, page.totalCount)
    }
}

// ---------------------------------------------------------------------------
// Test doubles
// ---------------------------------------------------------------------------

class CapturingLectorEmailService : LectorEmailService {
    val sentEmails = mutableListOf<String>()

    override suspend fun sendLectorReservationNotification(
        lectorEmail: String, contactName: String, contactEmail: String, contactPhone: String?,
        seatCount: Int, eventTitle: String, target: LectorTarget, occupiedSpots: Int, capacity: Int, locale: String,
    ): Either<EmailError.SendLectorReservation, Unit> {
        sentEmails.add(lectorEmail)
        return Unit.right()
    }

    override suspend fun sendLectorCancellationNotification(
        lectorEmail: String, contactName: String, eventTitle: String, target: LectorTarget,
        seatCount: Int, occupiedSpots: Int, capacity: Int, locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> {
        sentEmails.add(lectorEmail)
        return Unit.right()
    }

    override suspend fun sendLectorLessonOptOutNotification(
        lectorEmail: String, contactName: String, eventTitle: String,
        lessonDate: LocalDate, isLateCancellation: Boolean, locale: String,
    ): Either<EmailError.SendLectorCancellation, Unit> {
        sentEmails.add(lectorEmail)
        return Unit.right()
    }
}

/** Simple stub implementing QrCodeGeneratorService — no AppSettingsProvider required. */
class StubQrCodeGenerator : QrCodeGeneratorService {
    override val accountNumber: String = "2003487968/2010"
    override fun generateQrPng(reservation: Reservation, target: ReservationTarget?): ByteArray = ByteArray(0)
}

// ---------------------------------------------------------------------------
// ResolveOwnerEmailsTest
// ---------------------------------------------------------------------------

class ResolveOwnerEmailsTest {

    private fun makeReservationService(
        instanceRepo: InMemoryEventInstanceRepository,
        seriesRepo: InMemoryEventSeriesRepository,
        defRepo: InMemoryEventDefinitionRepository,
        reservationRepo: InMemoryReservationRepository,
        capturingService: CapturingLectorEmailService,
    ) = ReservationService(
        eventInstanceRepository = instanceRepo,
        eventSeriesRepository = seriesRepo,
        eventDefinitionRepository = defRepo,
        reservationRepository = reservationRepo,
        emailService = ConsoleEmailService(),
        lectorEmailService = capturingService,
        qrCodeService = StubQrCodeGenerator(),
        paymentTrigger = PaymentTrigger(),
        appBaseUrl = "https://test.example.com",
        seriesLessonOptOutRepository = cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository(),
        walletService = cz.svitaninymburk.projects.reservations.service.WalletService(
            cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository()
        ),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider.forTest(
            cz.svitaninymburk.projects.reservations.settings.AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    @Test
    fun `resolveOwnerEmails sends to all owners across instance, series, and definition`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val capturingService = CapturingLectorEmailService()

        val def = EventDefinition(
            id = Uuid.random(),
            title = "Test Event",
            description = "",
            defaultPrice = 100.0,
            defaultCapacity = 10,
            defaultDuration = 1.hours,
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            customFields = emptyList(),
            ownerEmails = listOf("definition@example.com"),
        )
        defRepo.create(def)

        val series = EventSeries(
            id = Uuid.random(),
            definitionId = def.id,
            title = "Test Series",
            description = "",
            price = 500.0,
            capacity = 10,
            startDate = LocalDate(2026, 9, 1),
            endDate = LocalDate(2026, 12, 1),
            lessonCount = 10,
            ownerEmails = listOf("series@example.com"),
        )
        seriesRepo.create(series)

        val instance = EventInstance(
            id = Uuid.random(),
            definitionId = def.id,
            seriesId = series.id,
            title = "Test Instance",
            description = "",
            startDateTime = LocalDateTime(2099, 6, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 6, 1, 11, 0),
            price = 100.0,
            capacity = 10,
            ownerEmails = listOf("instance@example.com"),
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            isPublished = true,
        )
        instanceRepo.create(instance)

        val service = makeReservationService(instanceRepo, seriesRepo, defRepo, reservationRepo, capturingService)

        val request = CreateInstanceReservationRequest(
            eventInstanceId = instance.id,
            seatCount = 1,
            contactName = "Jan Novak",
            contactEmail = "jan@test.com",
            contactPhone = "",
            paymentType = PaymentType.ON_SITE,
            customValues = emptyMap(),
            locale = "cs",
        )

        val result = service.reserveInstance(request, userId = null)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        assertEquals(3, capturingService.sentEmails.size, "Expected 3 owner notification emails")
        assertTrue(capturingService.sentEmails.contains("instance@example.com"))
        assertTrue(capturingService.sentEmails.contains("series@example.com"))
        assertTrue(capturingService.sentEmails.contains("definition@example.com"))
        Unit
    }

    @Test
    fun `resolveOwnerEmails deduplicates the same email at multiple levels`() = runBlocking {
        val defRepo = InMemoryEventDefinitionRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val instanceRepo = InMemoryEventInstanceRepository()
        val reservationRepo = InMemoryReservationRepository()
        val capturingService = CapturingLectorEmailService()

        val def = EventDefinition(
            id = Uuid.random(),
            title = "Test Event",
            description = "",
            defaultPrice = 100.0,
            defaultCapacity = 10,
            defaultDuration = 1.hours,
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            customFields = emptyList(),
            ownerEmails = listOf("shared@example.com"),
        )
        defRepo.create(def)

        val instance = EventInstance(
            id = Uuid.random(),
            definitionId = def.id,
            seriesId = null,
            title = "Test Instance",
            description = "",
            startDateTime = LocalDateTime(2099, 7, 1, 10, 0),
            endDateTime = LocalDateTime(2099, 7, 1, 11, 0),
            price = 100.0,
            capacity = 10,
            ownerEmails = listOf("shared@example.com"),
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            isPublished = true,
        )
        instanceRepo.create(instance)

        val service = makeReservationService(instanceRepo, seriesRepo, defRepo, reservationRepo, capturingService)

        val request = CreateInstanceReservationRequest(
            eventInstanceId = instance.id,
            seatCount = 1,
            contactName = "Jana Novakova",
            contactEmail = "jana@test.com",
            contactPhone = "",
            paymentType = PaymentType.ON_SITE,
            customValues = emptyMap(),
            locale = "cs",
        )

        val result = service.reserveInstance(request, userId = null)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        assertEquals(1, capturingService.sentEmails.count { it == "shared@example.com" },
            "shared@example.com should be notified exactly once, but got: ${capturingService.sentEmails}")
        Unit
    }
}

class ChangePasswordSpec {

    private val hashing = BCryptHashingService()
    private val userRepo = InMemoryUserRepository()

    private fun makeUser(id: Uuid, passwordHash: String) = User.Email(
        id = id,
        email = "test@example.com",
        name = "Test",
        surname = "User",
        role = User.Role.USER,
        passwordHash = passwordHash,
        passwordResetToken = null,
        passwordResetTokenExpiresAt = null,
    )

    private fun makeService(userId: Uuid? = null) = object : UserService(userRepo, hashing) {
        override suspend fun currentUserId(): Uuid? = userId
    }

    @Test
    fun `returns WeakPassword when new password is shorter than 6 chars`() = runBlocking {
        val id = Uuid.random()
        userRepo.create(makeUser(id, hashing.generateSaltedHash("oldpass")))

        val result = makeService(userId = id).changePassword("oldpass", "abc")

        assertTrue(result.isLeft())
        assertEquals(UserError.WeakPassword, (result as arrow.core.Either.Left).value)
    }

    @Test
    fun `returns UserNotFound when no call context`() = runBlocking {
        val result = makeService(userId = null).changePassword("oldpass", "newpass123")

        assertTrue(result.isLeft())
        assertTrue((result as arrow.core.Either.Left).value is UserError.UserNotFound)
    }

    @Test
    fun `returns NotEmailUser for Google account`() = runBlocking {
        val id = Uuid.random()
        userRepo.create(User.Google(id = id, email = "g@example.com", name = "G", surname = "User", role = User.Role.USER, googleSub = "sub123"))

        val result = makeService(userId = id).changePassword("any", "newpass123")

        assertTrue(result.isLeft())
        assertEquals(UserError.NotEmailUser, (result as arrow.core.Either.Left).value)
    }

    @Test
    fun `returns WrongOldPassword when old password does not match`() = runBlocking {
        val id = Uuid.random()
        userRepo.create(makeUser(id, hashing.generateSaltedHash("correctpass")))

        val result = makeService(userId = id).changePassword("wrongpass", "newpass123")

        assertTrue(result.isLeft())
        assertEquals(UserError.WrongOldPassword, (result as arrow.core.Either.Left).value)
    }

    @Test
    fun `returns Unit and updates password hash on success`() = runBlocking {
        val id = Uuid.random()
        userRepo.create(makeUser(id, hashing.generateSaltedHash("oldpass")))

        val result = makeService(userId = id).changePassword("oldpass", "newpass123")

        assertTrue(result.isRight())
        val updatedUser = userRepo.findById(id) as User.Email
        assertTrue(hashing.verify("newpass123", updatedUser.passwordHash))
    }
}

class AuthenticatedEventServiceScheduleSpec {

    private val definitionId = Uuid.random()

    private fun makeSeries(id: Uuid = Uuid.random()) = EventSeries(
        id = id,
        definitionId = definitionId,
        title = "Series",
        description = "desc",
        price = 500.0,
        capacity = 10,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 1, 2),
        lessonCount = 99,
    )

    private fun makeLesson(seriesId: Uuid, day: Int) = EventInstance(
        id = Uuid.random(),
        definitionId = definitionId,
        seriesId = seriesId,
        title = "Lesson",
        description = "desc",
        startDateTime = LocalDateTime(2026, 6, day, 10, 0),
        endDateTime = LocalDateTime(2026, 6, day, 11, 0),
        price = 500.0,
        capacity = 10,
    )

    @Test
    fun `updateEventInstance refreshes series schedule cache`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries()
        seriesRepo.create(series)
        val lesson = makeLesson(series.id, day = 1)
        instanceRepo.create(lesson)
        val service = cz.svitaninymburk.projects.reservations.service.AuthenticatedEventService(
            InMemoryEventDefinitionRepository(), instanceRepo, SeriesScheduleRefresher(instanceRepo, seriesRepo),
        )

        val result = service.updateEventInstance(
            lesson.copy(
                startDateTime = LocalDateTime(2026, 6, 22, 10, 0),
                endDateTime = LocalDateTime(2026, 6, 22, 11, 0),
            )
        )
        assertTrue(result.isRight())

        assertEquals(LocalDate(2026, 6, 22), seriesRepo.get(series.id)?.startDate)
        assertEquals(LocalDate(2026, 6, 22), seriesRepo.get(series.id)?.endDate)
    }

    @Test
    fun `deleteEventInstance refreshes series schedule cache`() = runBlocking {
        val instanceRepo = InMemoryEventInstanceRepository()
        val seriesRepo = InMemoryEventSeriesRepository()
        val series = makeSeries()
        seriesRepo.create(series)
        val first = makeLesson(series.id, day = 1)
        val last = makeLesson(series.id, day = 8)
        instanceRepo.create(first)
        instanceRepo.create(last)
        val service = cz.svitaninymburk.projects.reservations.service.AuthenticatedEventService(
            InMemoryEventDefinitionRepository(), instanceRepo, SeriesScheduleRefresher(instanceRepo, seriesRepo),
        )

        val result = service.deleteEventInstance(last.id)
        assertTrue(result.isRight())

        assertEquals(LocalDate(2026, 6, 1), seriesRepo.get(series.id)?.endDate)
        assertEquals(1, seriesRepo.get(series.id)?.lessonCount)
    }
}
