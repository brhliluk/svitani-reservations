package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.DuplicateScope
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemorySeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Rezervace nejsou vázané na účet, takže se totožnost pozná jen podle e-mailu.
 * Druhý pokus na tutéž akci je varování, ne zákaz — po potvrzení musí projít.
 */
class DuplicateReservationSpec {

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

    private fun instance(seriesId: Uuid? = null) = EventInstance(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Jednorázová akce",
        description = "",
        startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
        endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
        price = 100.0,
        capacity = 10,
        isPublished = true,
        allowMultipleSeats = true,
        seriesId = seriesId,
    )

    private fun series() = EventSeries(
        id = Uuid.random(),
        definitionId = Uuid.random(),
        title = "Kurz",
        description = "",
        price = 100.0,
        capacity = 10,
        isPublished = true,
        startDate = LocalDate(2099, 12, 1),
        endDate = LocalDate(2099, 12, 22),
        lessonCount = 4,
        allowMultipleSeats = true,
    )

    private fun instanceRequest(
        instanceId: Uuid,
        email: String = "jan@test.com",
        acknowledgedDuplicate: Boolean = false,
    ) = CreateInstanceReservationRequest(
        eventInstanceId = instanceId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = email,
        contactPhone = "+420777111222",
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyMap(),
        acknowledgedDuplicate = acknowledgedDuplicate,
    )

    private fun seriesRequest(
        seriesId: Uuid,
        email: String = "jan@test.com",
        acknowledgedDuplicate: Boolean = false,
    ) = CreateSeriesReservationRequest(
        eventSeriesId = seriesId,
        seatCount = 1,
        contactName = "Jan Novak",
        contactEmail = email,
        contactPhone = "+420777111222",
        paymentType = PaymentInfo.Type.BANK_TRANSFER,
        customValues = emptyMap(),
        acknowledgedDuplicate = acknowledgedDuplicate,
    )

    // --- Stejná akce ---

    @Test
    fun `second reservation for the same instance warns about a duplicate`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        assertTrue(service.reserveInstance(instanceRequest(event.id), userId = null).isRight())

        val result = service.reserveInstance(instanceRequest(event.id), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.SAME_EVENT), result.leftOrNull())
    }

    @Test
    fun `second reservation for the same series warns about a duplicate`() = runBlocking {
        val course = series()
        seriesRepo.create(course)
        assertTrue(service.reserveSeries(seriesRequest(course.id), userId = null).isRight())

        val result = service.reserveSeries(seriesRequest(course.id), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.SAME_EVENT), result.leftOrNull())
    }

    @Test
    fun `rejected attempt must not consume capacity`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        service.reserveInstance(instanceRequest(event.id), userId = null)
        val occupiedAfterFirst = instanceRepo.get(event.id)!!.occupiedSpots

        service.reserveInstance(instanceRequest(event.id), userId = null)

        assertEquals(
            occupiedAfterFirst,
            instanceRepo.get(event.id)!!.occupiedSpots,
            "Odmítnutý pokus nesmí ukousnout místo — kontrola musí být před attemptToReserveSpots",
        )
    }

    // --- Kurz ↔ jeho lekce ---

    @Test
    fun `reserving a lesson warns when signed up for the whole course`() = runBlocking {
        val course = series()
        seriesRepo.create(course)
        val lesson = instance(seriesId = course.id)
        instanceRepo.create(lesson)
        assertTrue(service.reserveSeries(seriesRequest(course.id), userId = null).isRight())

        val result = service.reserveInstance(instanceRequest(lesson.id), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.PARENT_SERIES), result.leftOrNull())
    }

    @Test
    fun `reserving a course warns when signed up for one of its lessons`() = runBlocking {
        val course = series()
        seriesRepo.create(course)
        val lesson = instance(seriesId = course.id)
        instanceRepo.create(lesson)
        assertTrue(service.reserveInstance(instanceRequest(lesson.id), userId = null).isRight())

        val result = service.reserveSeries(seriesRequest(course.id), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.SERIES_LESSON), result.leftOrNull())
    }

    @Test
    fun `lesson of an unrelated course does not warn`() = runBlocking {
        val course = series()
        seriesRepo.create(course)
        val unrelatedLesson = instance(seriesId = Uuid.random())
        instanceRepo.create(unrelatedLesson)
        assertTrue(service.reserveInstance(instanceRequest(unrelatedLesson.id), userId = null).isRight())

        val result = service.reserveSeries(seriesRequest(course.id), userId = null)

        assertTrue(result.isRight(), "Cizí kurz nemá co varovat, dostal jsem $result")
    }

    // --- Které stavy se počítají ---

    @Test
    fun `cancelled reservation is ignored`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        val first = service.reserveInstance(instanceRequest(event.id), userId = null).getOrNull()!!
        reservationRepo.updateStatus(first.id, Reservation.Status.CANCELLED)

        val result = service.reserveInstance(instanceRequest(event.id), userId = null)

        assertTrue(result.isRight(), "Po stornu se musí dát rezervovat znovu bez ptaní, dostal jsem $result")
    }

    @Test
    fun `rejected reservation is ignored`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        val first = service.reserveInstance(instanceRequest(event.id), userId = null).getOrNull()!!
        reservationRepo.updateStatus(first.id, Reservation.Status.REJECTED)

        val result = service.reserveInstance(instanceRequest(event.id), userId = null)

        assertTrue(result.isRight(), "Odmítnutá rezervace se nepočítá, dostal jsem $result")
    }

    @Test
    fun `waitlisted reservation counts as already signed up`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        val first = service.reserveInstance(instanceRequest(event.id), userId = null).getOrNull()!!
        reservationRepo.updateStatus(first.id, Reservation.Status.WAITLISTED)

        val result = service.reserveInstance(instanceRequest(event.id), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.SAME_EVENT), result.leftOrNull())
    }

    // --- Podle čeho se pozná totožnost ---

    @Test
    fun `a different email does not warn`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        assertTrue(service.reserveInstance(instanceRequest(event.id), userId = null).isRight())

        val result = service.reserveInstance(instanceRequest(event.id, email = "petra@test.com"), userId = null)

        assertTrue(result.isRight(), "Jiný e-mail je jiný člověk, dostal jsem $result")
    }

    @Test
    fun `email case and surrounding whitespace do not matter`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        assertTrue(service.reserveInstance(instanceRequest(event.id, email = "jan@test.com"), userId = null).isRight())

        val result = service.reserveInstance(instanceRequest(event.id, email = "  Jan@TEST.com "), userId = null)

        assertEquals(ReservationError.AlreadyReserved(DuplicateScope.SAME_EVENT), result.leftOrNull())
    }

    // --- Potvrzení uživatelem ---

    @Test
    fun `acknowledged duplicate goes through`() = runBlocking {
        val event = instance()
        instanceRepo.create(event)
        assertTrue(service.reserveInstance(instanceRequest(event.id), userId = null).isRight())

        val result = service.reserveInstance(
            instanceRequest(event.id, acknowledgedDuplicate = true),
            userId = null,
        )

        assertNull(result.leftOrNull(), "Potvrzenou duplicitu musí server pustit, dostal jsem $result")
        assertEquals(2, reservationRepo.findAll().size)
    }
}
