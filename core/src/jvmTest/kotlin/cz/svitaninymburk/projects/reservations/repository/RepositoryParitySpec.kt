package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventOwnerEmailsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventDefinitionRepository
import cz.svitaninymburk.projects.reservations.repository.event.ExposedEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.event.InMemoryEventSeriesRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.InMemoryReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationKind
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * Stejné scénáře proti SQLite i InMemory. Služby se testují nad InMemory, takže
 * každý rozdíl tady znamená testy, které procházejí na něčem jiném než produkce.
 */
class RepositoryParitySpec {

    private fun sqlite() {
        val dbFile = File.createTempFile("repository-parity", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                UsersTable, ReservationsTable,
                EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable,
            )
        }
    }

    private fun reservationRepos(): List<ReservationRepository> {
        sqlite()
        return listOf(ExposedReservationRepository(), InMemoryReservationRepository())
    }

    private fun reservation(
        referenceId: Uuid,
        email: String = "jana@test.cz",
        status: Reservation.Status = Reservation.Status.CONFIRMED,
        seats: Int = 1,
    ) = Reservation(
        id = Uuid.random(),
        reference = Reference.Instance(referenceId),
        contactName = "Jana",
        contactEmail = email,
        seatCount = seats,
        totalPrice = 100.0,
        status = status,
        createdAt = Clock.System.now(),
        customValues = emptyMap(),
        paymentType = PaymentType.ON_SITE,
    )

    @Test
    fun `active signups are found despite whitespace around email`() = runBlocking {
        for (repo in reservationRepos()) {
            val eventId = Uuid.random()
            repo.save(reservation(eventId, email = " Jana@Test.cz "))

            val found = repo.findActiveByReferenceIdsAndEmail(listOf(eventId), "jana@test.cz")

            assertEquals(1, found.size, repo::class.simpleName)
        }
    }

    @Test
    fun `only active reservations hold seats`() = runBlocking {
        for (repo in reservationRepos()) {
            val eventId = Uuid.random()
            repo.save(reservation(eventId, seats = 2))
            repo.save(reservation(eventId, status = Reservation.Status.PENDING_PAYMENT))
            listOf(Reservation.Status.CANCELLED, Reservation.Status.REJECTED, Reservation.Status.WAITLISTED)
                .forEach { repo.save(reservation(eventId, status = it, seats = 5)) }

            assertEquals(3, repo.countSeats(eventId), repo::class.simpleName)
        }
    }

    @Test
    fun `series update keeps cancellation flag`() = runBlocking {
        sqlite()
        val definition = ExposedEventDefinitionRepository().create(
            EventDefinition(
                id = Uuid.random(), title = "T", description = "", defaultPrice = 100.0,
                defaultCapacity = 10, defaultDuration = 1.hours,
            )
        )
        val repos: List<EventSeriesRepository> = listOf(ExposedEventSeriesRepository(), InMemoryEventSeriesRepository())
        for (repo in repos) {
            val series = EventSeries(
                id = Uuid.random(), definitionId = definition.id, title = "Kurz", description = "",
                price = 1000.0, capacity = 10,
                startDate = LocalDate(2099, 9, 1), endDate = LocalDate(2099, 12, 1), lessonCount = 10,
            )
            repo.create(series)
            repo.setCancelled(series.id)

            repo.update(series.copy(title = "Přejmenovaný kurz"))

            val stored = repo.get(series.id)!!
            assertEquals("Přejmenovaný kurz", stored.title, repo::class.simpleName)
            assertEquals(true, stored.isCancelled, repo::class.simpleName)
        }
    }

    private fun slotReservation(
        serviceId: Uuid,
        start: LocalDateTime,
        end: LocalDateTime,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
    ) = reservation(serviceId, status = status, seats = 2).copy(
        reference = Reference.Slot(serviceId, start, end),
        // Celé sekundy: SQLite čas ukládá s menší přesností, než má Clock.System.
        createdAt = Instant.fromEpochSeconds(1_790_000_000),
    )

    @Test
    fun `slot reservation round trips with its interval`() = runBlocking {
        for (repo in reservationRepos()) {
            val saved = repo.save(slotReservation(Uuid.random(), LocalDateTime(2026, 10, 20, 9, 0), LocalDateTime(2026, 10, 20, 11, 30)))

            assertEquals(saved, repo.findById(saved.id), repo::class.simpleName)
        }
    }

    @Test
    fun `slot reference matches only the same interval`() = runBlocking {
        for (repo in reservationRepos()) {
            val serviceId = Uuid.random()
            val morning = repo.save(slotReservation(serviceId, LocalDateTime(2026, 10, 20, 9, 0), LocalDateTime(2026, 10, 20, 10, 0)))
            repo.save(slotReservation(serviceId, LocalDateTime(2026, 10, 20, 10, 0), LocalDateTime(2026, 10, 20, 11, 0)))

            assertEquals(listOf(morning.id), repo.findByReference(morning.reference).map { it.id }, repo::class.simpleName)
        }
    }

    @Test
    fun `active slot reservations of a service overlapping a range`() = runBlocking {
        for (repo in reservationRepos()) {
            val serviceId = Uuid.random()
            val day = LocalDate(2026, 10, 20)
            fun at(hour: Int, minute: Int = 0) = LocalDateTime(day.year, day.month, day.day, hour, minute)
            val overlapping = repo.save(slotReservation(serviceId, at(8), at(9, 30)))
            val inside = repo.save(slotReservation(serviceId, at(9, 30), at(10)))
            repo.save(slotReservation(serviceId, at(10), at(11)))            // začíná na konci rozsahu
            repo.save(slotReservation(serviceId, at(7), at(9)))              // končí na začátku rozsahu
            repo.save(slotReservation(serviceId, at(9), at(10), Reservation.Status.CANCELLED))
            repo.save(slotReservation(Uuid.random(), at(9), at(10)))         // jiná služba
            repo.save(reservation(serviceId))                                // akce se stejným id

            val found = repo.findActiveSlotReservations(serviceId, at(9), at(10)).map { it.id }.toSet()

            assertEquals(setOf(overlapping.id, inside.id), found, repo::class.simpleName)
        }
    }

    @Test
    fun `admin list filters by reservation kind together with search`() = runBlocking {
        for (repo in reservationRepos()) {
            repo.save(reservation(Uuid.random(), email = "akce@test.cz"))
            repo.save(reservation(Uuid.random()).copy(reference = Reference.Series(Uuid.random()), contactEmail = "kurz@test.cz"))
            val start = LocalDateTime(2026, 10, 20, 9, 0)
            repo.save(reservation(Uuid.random()).copy(reference = Reference.Slot(Uuid.random(), start, LocalDateTime(2026, 10, 20, 10, 0)), contactEmail = "hlidani@test.cz"))
            repo.save(reservation(Uuid.random(), status = Reservation.Status.CANCELLED).copy(reference = Reference.Slot(Uuid.random(), start, start), contactEmail = "storno@test.cz"))

            val slots = repo.findAllPaged(null, 0, 20, kind = ReservationKind.SLOT)
            assertEquals(listOf("hlidani@test.cz"), slots.map { it.contactEmail }, repo::class.simpleName)
            assertEquals(1L, repo.countAll(null, kind = ReservationKind.SLOT))
            assertEquals(2L, repo.countAll(null, includeCancelled = true, kind = ReservationKind.SLOT))
            assertEquals(0L, repo.countAll("akce", kind = ReservationKind.SLOT))
            assertEquals(3L, repo.countAll(null))
        }
    }
}
