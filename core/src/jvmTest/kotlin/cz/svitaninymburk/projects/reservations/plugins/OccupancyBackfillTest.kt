package cz.svitaninymburk.projects.reservations.plugins

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Omluvenky dřív dekrementovaly čítač lekce, který přihláška na kurz nikdy
 * nenavýšila, takže v produkci jsou záporné hodnoty. Backfill je přepočte
 * z rezervací.
 */
class OccupancyBackfillTest {

    private val lessonId = "00000000-0000-0000-0000-0000000000c1"
    private val courseId = "00000000-0000-0000-0000-0000000000a1"

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("occupancy-backfill", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            exec("CREATE TABLE event_instances (id TEXT PRIMARY KEY, occupied_spots INTEGER NOT NULL, occupied_waitlist INTEGER NOT NULL)")
            exec("CREATE TABLE event_series (id TEXT PRIMARY KEY, occupied_spots INTEGER NOT NULL, occupied_waitlist INTEGER NOT NULL)")
            exec("""
                CREATE TABLE reservations (
                    id TEXT PRIMARY KEY,
                    reference_id TEXT NOT NULL,
                    reference_type TEXT NOT NULL,
                    seat_count INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
            """.trimIndent())

            exec("INSERT INTO event_instances VALUES ('$lessonId', -2, 7)")
            exec("INSERT INTO event_series VALUES ('$courseId', 99, -3)")
            exec("INSERT INTO reservations VALUES ('r1', '$lessonId', 'INSTANCE', 2, 'CONFIRMED')")
            exec("INSERT INTO reservations VALUES ('r2', '$lessonId', 'INSTANCE', 1, 'CANCELLED')")
            exec("INSERT INTO reservations VALUES ('r3', '$courseId', 'SERIES', 3, 'PENDING_PAYMENT')")
            exec("INSERT INTO reservations VALUES ('r4', '$courseId', 'SERIES', 5, 'WAITLISTED')")
            // Dva čekatelé na lekci, dohromady 4 místa — pořadník se ale počítá
            // po přihláškách, takže výsledek musí být 2, ne 4.
            exec("INSERT INTO reservations VALUES ('r5', '$lessonId', 'INSTANCE', 3, 'WAITLISTED')")
            exec("INSERT INTO reservations VALUES ('r6', '$lessonId', 'INSTANCE', 1, 'WAITLISTED')")
        }
    }

    private fun instanceSpots(): Int = transaction {
        exec("SELECT occupied_spots FROM event_instances WHERE id = '$lessonId'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    private fun seriesSpots(): Int = transaction {
        exec("SELECT occupied_spots FROM event_series WHERE id = '$courseId'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    private fun instanceWaitlist(): Int = transaction {
        exec("SELECT occupied_waitlist FROM event_instances WHERE id = '$lessonId'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    private fun seriesWaitlist(): Int = transaction {
        exec("SELECT occupied_waitlist FROM event_series WHERE id = '$courseId'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    @Test
    fun `backfill recomputes the waitlist too, by reservations not by seats`() = runBlocking {
        recomputeOccupiedSpots()

        assertEquals(2, instanceWaitlist(), "dva čekatelé (3 + 1 místo) jsou dvě přihlášky, ne čtyři")
        assertEquals(1, seriesWaitlist(), "jeden čekatel s pěti místy je jedna přihláška; záporná hodnota zmizí")
    }

    @Test
    fun `backfill recomputes counters from active reservations`() = runBlocking {
        recomputeOccupiedSpots()

        assertEquals(2, instanceSpots(), "zrušená rezervace se nepočítá, záporná hodnota zmizí")
        assertEquals(3, seriesSpots(), "čekatel z pořadníku místo nedrží")
    }

    @Test
    fun `backfill is idempotent`() = runBlocking {
        recomputeOccupiedSpots()
        recomputeOccupiedSpots()

        assertEquals(2, instanceSpots())
        assertEquals(3, seriesSpots())
    }
}
