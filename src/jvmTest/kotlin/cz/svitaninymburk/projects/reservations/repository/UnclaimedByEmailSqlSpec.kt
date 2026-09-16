package cz.svitaninymburk.projects.reservations.repository

import cz.svitaninymburk.projects.reservations.repository.reservation.ExposedReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import cz.svitaninymburk.projects.reservations.repository.user.UsersTable
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.user.User
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * `findUnclaimedByEmail` proti skutečné SQLite, ne proti in-memory repozitáři.
 *
 * Má to důvod: in-memory varianta adresu v Kotlinu trimuje, takže by prošla i verze
 * dotazu bez `TRIM`. Adresy se přitom ukládají tak, jak je člověk napsal — s mezerou
 * na kraji — a takový řádek by v SQL propadl. Filtr v paměti ho pak nemá z čeho
 * zachránit, protože se k němu vůbec nedostane.
 */
class UnclaimedByEmailSqlSpec {

    companion object {
        private val dbFile: File = File.createTempFile("unclaimed-email-test", ".db").also { it.deleteOnExit() }

        val db: Database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
        )

        init {
            transaction(db) { SchemaUtils.create(UsersTable, ReservationsTable) }
        }
    }

    private val repository = ExposedReservationRepository()

    private fun insertUser(id: Uuid) {
        transaction(db) {
            UsersTable.insert { row ->
                row[UsersTable.id] = id
                row[UsersTable.email] = "owner-$id@test.cz"
                row[UsersTable.name] = "Vlastník"
                row[UsersTable.surname] = "Účtu"
                row[UsersTable.role] = User.Role.USER
                row[UsersTable.passwordHash] = "x"
            }
        }
    }

    private fun insertReservation(
        email: String,
        status: Reservation.Status = Reservation.Status.CONFIRMED,
        userId: Uuid? = null,
    ): Uuid {
        val id = Uuid.random()
        userId?.let { insertUser(it) }
        transaction(db) {
            ReservationsTable.insert { row ->
                row[ReservationsTable.id] = id
                row[ReservationsTable.referenceId] = Uuid.random()
                row[ReservationsTable.referenceType] = ReferenceDbDiscriminator.INSTANCE
                row[ReservationsTable.registeredUserId] = userId
                row[ReservationsTable.contactName] = "Jan Host"
                row[ReservationsTable.contactEmail] = email
                row[ReservationsTable.seatCount] = 1
                row[ReservationsTable.totalPrice] = 100.0
                row[ReservationsTable.paidAmount] = 0.0
                row[ReservationsTable.status] = status
                row[ReservationsTable.createdAt] = Clock.System.now()
                row[ReservationsTable.customValues] = emptyMap()
                row[ReservationsTable.paymentType] = PaymentInfo.Type.BANK_TRANSFER
                row[ReservationsTable.locale] = "cs"
            }
        }
        return id
    }

    @Test
    fun `najde adresu s mezerami i velkymi pismeny`() = runBlocking {
        val id = insertReservation("  Mezery@Test.cz ")

        val found = repository.findUnclaimedByEmail("mezery@test.cz")

        assertEquals(listOf(id), found.map { it.id })
        Unit
    }

    @Test
    fun `vynecha rezervaci uz navazanou na ucet`() = runBlocking {
        insertReservation("navazana@test.cz", userId = Uuid.random())

        assertEquals(emptyList(), repository.findUnclaimedByEmail("navazana@test.cz").map { it.id })
        Unit
    }

    @Test
    fun `vynecha zrusenou rezervaci`() = runBlocking {
        insertReservation("zrusena@test.cz", status = Reservation.Status.CANCELLED)

        assertEquals(emptyList(), repository.findUnclaimedByEmail("zrusena@test.cz").map { it.id })
        Unit
    }

    @Test
    fun `cizi adresu nevrati`() = runBlocking {
        insertReservation("moje@test.cz")

        assertEquals(emptyList(), repository.findUnclaimedByEmail("cizi@test.cz").map { it.id })
        Unit
    }
}
