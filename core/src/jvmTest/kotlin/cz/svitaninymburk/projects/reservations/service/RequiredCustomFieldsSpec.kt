package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Povinná vlastní pole hlídá i server, ne jen webový formulář. Rezervace poslaná mimo
 * formulář (API, starý klient v mezipaměti) by jinak prošla bez údajů, které si lektor
 * vyžádal. Odmítnutý pokus nesmí zabrat místo ani nic uložit.
 */
class RequiredCustomFieldsSpec {

    private val instanceRepo = InMemoryEventInstanceRepository()
    private val seriesRepo = InMemoryEventSeriesRepository()
    private val reservationRepo = InMemoryReservationRepository()

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
        seriesLessonOptOutRepository = InMemorySeriesLessonOptOutRepository(),
        walletService = WalletService(InMemoryWalletRepository()),
        walletEmailService = ConsoleEmailService(),
        appSettingsProvider = AppSettingsProvider.forTest(
            AppSettings(
                bankAccountNumber = "", fioToken = "", senderEmail = "",
                gmailAppPassword = "", senderDisplayName = "",
            )
        ),
    )

    private val childrenField = TextFieldDefinition(key = "children", label = "Jména a věk dětí", isRequired = true)
    private val consentField = BooleanFieldDefinition(key = "consent", label = "Souhlas s podmínkami", isRequired = true)
    private val noteField = TextFieldDefinition(key = "note", label = "Poznámka")
    private val fields = listOf(childrenField, consentField, noteField)

    private val filled: Map<String, CustomFieldValue> = mapOf(
        "children" to TextValue("children", "Anička, 3 roky"),
        "consent" to BooleanValue("consent", true),
    )

    private fun instance(customFields: List<CustomFieldDefinition> = fields, occupiedSpots: Int = 0) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Herna",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        occupiedSpots = occupiedSpots,
        isPublished = true,
        customFields = customFields,
    )

    private fun series(occupiedSpots: Int = 0) = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 100.0,
        capacity = 10,
        occupiedSpots = occupiedSpots,
        isPublished = true,
        startDate = LocalDate(2099, 12, 1),
        endDate = LocalDate(2099, 12, 22),
        lessonCount = 4,
        customFields = fields,
    )

    private fun instanceRequest(instanceId: Uuid, values: Map<String, CustomFieldValue>) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentType.ON_SITE,
        customValues = values,
    )

    private fun seriesRequest(seriesId: Uuid, values: Map<String, CustomFieldValue>) = CreateSeriesReservationRequest(
        eventSeriesId = seriesId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = "jan@test.com",
        contactPhone = "+420777111222",
        paymentType = PaymentType.ON_SITE,
        customValues = values,
    )

    @Test
    fun `instance reservation without a required field is rejected without taking a seat`() = runBlocking<Unit> {
        val event = instance()
        instanceRepo.create(event)

        val result = service.reserveInstance(instanceRequest(event.id, mapOf("consent" to BooleanValue("consent", true))), userId = null)

        assertEquals(ReservationError.RequiredFieldMissing(key = "children", label = "Jména a věk dětí"), result.leftOrNull())
        assertEquals(0, instanceRepo.get(event.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
        assertTrue(reservationRepo.findAll().isEmpty(), "Nic se nesmí uložit")
    }

    @Test
    fun `blank text and unchecked consent count as missing`() = runBlocking<Unit> {
        val event = instance()
        instanceRepo.create(event)

        val blankText = service.reserveInstance(
            instanceRequest(event.id, filled + ("children" to TextValue("children", "   "))),
            userId = null,
        )
        val uncheckedConsent = service.reserveInstance(
            instanceRequest(event.id, filled + ("consent" to BooleanValue("consent", false))),
            userId = null,
        )

        assertEquals("children", (blankText.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
        assertEquals("consent", (uncheckedConsent.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
    }

    @Test
    fun `instance reservation with required fields filled goes through`() = runBlocking<Unit> {
        val event = instance()
        instanceRepo.create(event)

        val result = service.reserveInstance(instanceRequest(event.id, filled), userId = null)

        assertNotNull(result.getOrNull())
        assertEquals(1, instanceRepo.get(event.id)!!.occupiedSpots)
    }

    @Test
    fun `optional fields alone never block a reservation`() = runBlocking<Unit> {
        val event = instance(customFields = listOf(noteField))
        instanceRepo.create(event)

        val result = service.reserveInstance(instanceRequest(event.id, emptyMap()), userId = null)

        assertNotNull(result.getOrNull())
    }

    @Test
    fun `course reservation without a required field is rejected without taking a seat`() = runBlocking<Unit> {
        val course = series()
        seriesRepo.create(course)

        val result = service.reserveSeries(seriesRequest(course.id, emptyMap()), userId = null)

        assertEquals("children", (result.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
        assertEquals(0, seriesRepo.get(course.id)!!.occupiedSpots, "Kapacita se nesmí zabrat")
    }

    @Test
    fun `waitlist signup without a required field is rejected without taking a waitlist spot`() = runBlocking<Unit> {
        val event = instance(occupiedSpots = 10)
        instanceRepo.create(event)

        val result = service.joinWaitlistInstance(instanceRequest(event.id, emptyMap()), userId = null)

        assertEquals("children", (result.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
        assertEquals(0, instanceRepo.get(event.id)!!.occupiedWaitlist, "Místo v pořadníku se nesmí zabrat")
    }

    @Test
    fun `course waitlist signup without a required field is rejected`() = runBlocking<Unit> {
        val course = series(occupiedSpots = 10)
        seriesRepo.create(course)

        val result = service.joinWaitlistSeries(seriesRequest(course.id, emptyMap()), userId = null)

        assertEquals("children", (result.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
        assertEquals(0, seriesRepo.get(course.id)!!.occupiedWaitlist, "Místo v pořadníku se nesmí zabrat")
    }

    @Test
    fun `slot reservation without a required field is rejected`() = runBlocking<Unit> {
        val target = ReservationTarget.Slot(
            serviceId = Uuid.random(),
            title = "Hlídání",
            startDateTime = LocalDateTime(2099, 6, 2, 9, 0),
            endDateTime = LocalDateTime(2099, 6, 2, 11, 0),
            pricePerHour = 150.0,
            freeSeats = 5,
            capacity = 8,
            maxSeatsPerBooking = 3,
            allowedPaymentTypes = listOf(PaymentType.ON_SITE),
            customFields = fields,
        )

        val missing = service.createSlotReservation(
            target = target,
            requestData = instanceRequest(Uuid.random(), emptyMap()),
            walletCode = null,
            acknowledgedDuplicate = false,
            userId = null,
        )
        val complete = service.createSlotReservation(
            target = target,
            requestData = instanceRequest(Uuid.random(), filled),
            walletCode = null,
            acknowledgedDuplicate = false,
            userId = null,
        )

        assertEquals("children", (missing.leftOrNull() as? ReservationError.RequiredFieldMissing)?.key)
        assertNotNull(complete.getOrNull())
    }
}
