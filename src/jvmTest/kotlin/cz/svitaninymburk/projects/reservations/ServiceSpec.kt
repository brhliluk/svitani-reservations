package cz.svitaninymburk.projects.reservations

import cz.svitaninymburk.projects.reservations.util.SettingsEncryption
import cz.svitaninymburk.projects.reservations.settings.maskSecret
import cz.svitaninymburk.projects.reservations.repository.settings.InMemoryAppSettingsRepository
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.repository.event.*
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.AdminDashboardService
import cz.svitaninymburk.projects.reservations.service.ConsoleEmailService
import cz.svitaninymburk.projects.reservations.service.ICalGenerator
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.payment.NewPaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.admin.PaymentEventsPage
import cz.svitaninymburk.projects.reservations.admin.ReservationsPage
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.admin.SeriesInstancesPage
import arrow.core.getOrNull
import kotlinx.coroutines.runBlocking
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
    ) = AdminDashboardService(
        eventDefinitionRepository = defRepo,
        eventSeriesRepository = seriesRepo,
        eventInstanceRepository = instanceRepo,
        reservationRepository = reservationRepo,
        userRepository = InMemoryUserRepository(),
        emailService = ConsoleEmailService(),
        paymentEventRepository = InMemoryPaymentEventRepository(),
    )

    private fun makeDefinition(id: Uuid = Uuid.random()) = EventDefinition(
        id = id,
        title = "Test Definition",
        description = "desc",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = 1.hours,
        allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
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

    // --- updateEventInstance ---

    @Test
    fun `updateEventInstance returns Left when instance not found`() = runBlocking {
        val request = UpdateEventInstanceRequest(
            title = "New", description = "d",
            startDateTime = LocalDateTime(2026, 7, 1, 10, 0),
            endDateTime = LocalDateTime(2026, 7, 1, 11, 0),
            price = 200.0, capacity = 5,
            allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
            allowedPaymentTypes = listOf(PaymentInfo.Type.ON_SITE),
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
            startDate = LocalDate(2026, 6, 1), endDate = LocalDate(2026, 8, 1),
            lessonCount = 8, allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
            startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2026, 9, 1),
            lessonCount = 10, allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
            allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
            allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
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
        paymentType = PaymentInfo.Type.ON_SITE,
    )

    @Test
    fun `markReservationAsPaid inserts a payment event`() = runBlocking {
        val reservation = makeReservation()
        reservationRepo.save(reservation)

        // service will be created in Task 6 — this test will fail to compile until then
        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = InMemoryEventSeriesRepository(),
            eventInstanceRepository = InMemoryEventInstanceRepository(),
            reservationRepository = reservationRepo,
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
        )

        val result = service.markReservationAsPaid(reservation.id)
        assertTrue(result.isRight(), "Expected Right but got: $result")

        val inserted = paymentRepo.insertedEvents()
        assertEquals(1, inserted.size, "Expected exactly one payment event")
        assertEquals(reservation.id.toString(), inserted[0].reservationId)
        assertEquals(300.0, inserted[0].amount)
        assertEquals(PaymentEvent.Source.MANUAL_ADMIN, inserted[0].source)
        assertEquals(PaymentInfo.Type.ON_SITE, inserted[0].type)
    }

    @Test
    fun `getPaymentEvents returns paginated results`() = runBlocking {
        val now = Clock.System.now()
        paymentRepo.seed(PaymentEvent("id1", Uuid.random().toString(), "Alice", 100.0, "CZK", PaymentInfo.Type.BANK_TRANSFER, PaymentEvent.Source.AUTO_FIO,     now))
        paymentRepo.seed(PaymentEvent("id2", Uuid.random().toString(), "Bob",   200.0, "CZK", PaymentInfo.Type.ON_SITE,       PaymentEvent.Source.MANUAL_ADMIN, now))

        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = InMemoryEventSeriesRepository(),
            eventInstanceRepository = InMemoryEventInstanceRepository(),
            reservationRepository = InMemoryReservationRepository(),
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
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
            paymentRepo.seed(PaymentEvent("id$i", Uuid.random().toString(), "User$i", 100.0 * i, "CZK", PaymentInfo.Type.BANK_TRANSFER, PaymentEvent.Source.AUTO_FIO, now))
        }

        val service = AdminDashboardService(
            eventDefinitionRepository = InMemoryEventDefinitionRepository(),
            eventSeriesRepository = InMemoryEventSeriesRepository(),
            eventInstanceRepository = InMemoryEventInstanceRepository(),
            reservationRepository = InMemoryReservationRepository(),
            userRepository = InMemoryUserRepository(),
            emailService = ConsoleEmailService(),
            paymentEventRepository = paymentRepo,
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
    )

    private fun makeDefinition(title: String = "Def", id: Uuid = Uuid.random()) = EventDefinition(
        id = id,
        title = title,
        description = "",
        defaultPrice = 100.0,
        defaultCapacity = 10,
        defaultDuration = kotlin.time.Duration.parse("1h"),
        allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
        customFields = emptyList(),
        lectorEmail = "",
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
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
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
                    allowedPaymentTypes = listOf(PaymentInfo.Type.BANK_TRANSFER),
                    customFields = emptyList(),
                    lectorEmail = "",
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
