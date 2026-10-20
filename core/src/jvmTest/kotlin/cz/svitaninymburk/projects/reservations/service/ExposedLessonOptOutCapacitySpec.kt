package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.StubQrCodeGenerator
import cz.svitaninymburk.projects.reservations.error.AdminError
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventOwnerEmailsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedSeriesAwareCapacityGuard
import cz.svitaninymburk.projects.reservations.repository.event.ExposedSeriesLessonLoad
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.SeriesAwareEventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.payment.InMemoryPaymentEventRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedSeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutsTable
import cz.svitaninymburk.projects.reservations.repository.user.InMemoryUserRepository
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.repository.wallet.InMemoryWalletRepository
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.settings.AppSettings
import cz.svitaninymburk.projects.reservations.settings.AppSettingsProvider
import cz.svitaninymburk.projects.reservations.testWaitlistPromoter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Řetěz omluvenka → posun čekatele → vzetí omluvenky zpět přes skutečnou SQLite:
 * dekorátor [SeriesAwareEventInstanceRepository] s [ExposedSeriesLessonLoad]
 * a SQL guardem [ExposedSeriesAwareCapacityGuard]. In-memory specy
 * (`LessonWaitlistOnEnrollmentCancelSpec`, `AdminLessonParticipantsSpec`) tuhle
 * cestu nepokrývají.
 *
 * Kapacita lekce je 1 záměrně: na větší kapacitě by čekatel postoupil, i kdyby
 * omluvenka žádné místo neuvolnila. Slouží i jako regresní síť pro zrušení
 * uložených čítačů (`docs/followups/varianta-c-zruseni-ulozene-obsazenosti.md`).
 */
class ExposedLessonOptOutCapacitySpec {

    private val seriesId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val lessonId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    private val seriesRepo = ExposedEventSeriesRepository()
    private val reservationRepo = ExposedReservationRepository()
    private val optOutRepo = ExposedSeriesLessonOptOutRepository()
    private val instanceRepo = SeriesAwareEventInstanceRepository(
        delegate = ExposedEventInstanceRepository(),
        load = ExposedSeriesLessonLoad(),
        guard = ExposedSeriesAwareCapacityGuard(),
    )

    private val appSettings = AppSettingsProvider.forTest(
        AppSettings(
            bankAccountNumber = "", fioToken = "", senderEmail = "",
            gmailAppPassword = "", senderDisplayName = "",
        )
    )

    private val reservationService = ReservationService(
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
        appSettingsProvider = appSettings,
    )

    private val adminService = AdminDashboardService(
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
            appSettingsProvider = appSettings,
        ),
        seriesLessonOptOutRepository = optOutRepo,
        seriesScheduleRefresher = SeriesScheduleRefresher(instanceRepo, seriesRepo),
        waitlistPromoter = testWaitlistPromoter(instanceRepo, seriesRepo, reservationRepo),
    )

    private val createdAt = Clock.System.now()

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("lesson-opt-out-capacity", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                UsersTable,
                ReservationsTable,
                SeriesLessonOptOutsTable,
                EventDefinitionsTable,
                EventSeriesTable,
                EventInstancesTable,
                EventOwnerEmailsTable,
            )
        }
    }

    /**
     * Kurz i lekce s kapacitou 1. Místo na lekci drží účastník kurzu, na lekci čeká
     * jeden náhradník. Čítače se zakládají tak, jak by je nechaly zápisové cesty:
     * kurz 1 místo, lekce 0 přímých míst (zátěž kurzu se dopočítává) a 1 čekatel.
     */
    private suspend fun fullLessonWithWaitlist(): Pair<Reservation, Reservation> {
        seriesRepo.create(
            EventSeries(
                id = seriesId,
                definitionId = Uuid.random(),
                title = "Kurz",
                description = "",
                price = 0.0,
                capacity = 1,
                occupiedSpots = 1,
                startDate = LocalDate(2099, 12, 1),
                endDate = LocalDate(2099, 12, 31),
                lessonCount = 1,
            )
        )
        instanceRepo.create(
            EventInstance(
                id = lessonId,
                definitionId = Uuid.random(),
                seriesId = seriesId,
                title = "Lekce",
                description = "",
                startDateTime = LocalDateTime(2099, 12, 1, 10, 0),
                endDateTime = LocalDateTime(2099, 12, 1, 11, 0),
                price = 0.0,
                capacity = 1,
                waitlistCapacity = 3,
                occupiedWaitlist = 1,
                isDropIn = true,
            )
        )
        val enrollment = reserve(Reference.Series(seriesId), Reservation.Status.CONFIRMED, order = 0)
        val waitlisted = reserve(Reference.Instance(lessonId), Reservation.Status.WAITLISTED, order = 1)
        return enrollment to waitlisted
    }

    // Zdarma a bez účtu: omluvenka pak nic nevrací do peněženky a projde i bez přihlášeného volajícího.
    private suspend fun reserve(reference: Reference, status: Reservation.Status, order: Int) =
        reservationRepo.save(
            Reservation(
                id = Uuid.random(),
                reference = reference,
                contactName = "Tester $order",
                contactEmail = "tester$order@example.com",
                seatCount = 1,
                totalPrice = 0.0,
                status = status,
                createdAt = createdAt + order.minutes,
                customValues = emptyMap(),
                paymentType = PaymentType.FREE,
            )
        )

    private suspend fun optOut(enrollment: Reservation) {
        val result = reservationService.cancelReservation(
            reservationId = enrollment.id,
            instanceId = lessonId,
            walletCode = null,
            force = false,
            refundAmount = null,
        )
        assertTrue(result.isRight(), "omluvenka musí projít, dostal: $result")
    }

    @Test
    fun `opt-out frees the only seat and the waitlisted person takes it`() = runBlocking {
        val (enrollment, waitlisted) = fullLessonWithWaitlist()
        assertTrue(instanceRepo.get(lessonId)!!.isFull, "předpoklad: místo drží účastník kurzu")

        optOut(enrollment)

        assertEquals(
            Reservation.Status.CONFIRMED,
            reservationRepo.findById(waitlisted.id)?.status,
            "omluvenka uvolnila jediné místo, náhradník ho má dostat",
        )
        val lesson = instanceRepo.get(lessonId)!!
        assertEquals(1, lesson.occupiedSpots, "účastník kurzu se omluvil, místo drží posunutý náhradník")
        assertEquals(0, lesson.occupiedWaitlist)
    }

    @Test
    fun `revoking the opt-out is refused once the seat was taken`() = runBlocking {
        val (enrollment, _) = fullLessonWithWaitlist()
        optOut(enrollment)

        val result = adminService.revokeLessonOptOut(enrollment.id, lessonId)

        assertEquals(AdminError.RevokeOptOut.LessonFull, result.leftOrNull())
        assertNotNull(optOutRepo.findByReservationAndInstance(enrollment.id, lessonId), "omluvenka musí zůstat")
        assertEquals(1, instanceRepo.get(lessonId)!!.occupiedSpots, "lekce nesmí být přeplněná")
    }

    @Test
    fun `revoking the opt-out takes the seat back when nobody claimed it`() = runBlocking {
        val (enrollment, waitlisted) = fullLessonWithWaitlist()
        // Bez čekatele: místo po omluvence zůstane volné.
        reservationRepo.save(waitlisted.copy(status = Reservation.Status.CANCELLED))
        optOut(enrollment)
        assertEquals(0, instanceRepo.get(lessonId)!!.occupiedSpots, "předpoklad: po omluvence je lekce volná")

        val result = adminService.revokeLessonOptOut(enrollment.id, lessonId)

        assertTrue(result.isRight(), "místo je volné, vzetí zpět musí projít, dostal: $result")
        assertEquals(1, instanceRepo.get(lessonId)!!.occupiedSpots, "účastník kurzu drží místo znovu")
        assertTrue(
            !instanceRepo.attemptToReserveSpots(lessonId, 1),
            "SQL guard musí po vzetí zpět s účastníkem kurzu počítat",
        )
    }
}
