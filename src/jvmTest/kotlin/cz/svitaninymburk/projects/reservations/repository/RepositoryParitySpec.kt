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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
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
    fun `aktivni prihlasky se najdou i s mezerou kolem e-mailu`() = runBlocking {
        for (repo in reservationRepos()) {
            val eventId = Uuid.random()
            repo.save(reservation(eventId, email = " Jana@Test.cz "))

            val found = repo.findActiveByReferenceIdsAndEmail(listOf(eventId), "jana@test.cz")

            assertEquals(1, found.size, repo::class.simpleName)
        }
    }

    @Test
    fun `mista drzi jen aktivni rezervace`() = runBlocking {
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
    fun `uprava kurzu nezrusi ani neobnovi zruseni`() = runBlocking {
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
}
