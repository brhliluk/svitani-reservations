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

    private val lekce = "00000000-0000-0000-0000-0000000000c1"
    private val kurz = "00000000-0000-0000-0000-0000000000a1"

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("occupancy-backfill", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            exec("CREATE TABLE event_instances (id TEXT PRIMARY KEY, occupied_spots INTEGER NOT NULL)")
            exec("CREATE TABLE event_series (id TEXT PRIMARY KEY, occupied_spots INTEGER NOT NULL)")
            exec("""
                CREATE TABLE reservations (
                    id TEXT PRIMARY KEY,
                    reference_id TEXT NOT NULL,
                    reference_type TEXT NOT NULL,
                    seat_count INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
            """.trimIndent())

            exec("INSERT INTO event_instances VALUES ('$lekce', -2)")
            exec("INSERT INTO event_series VALUES ('$kurz', 99)")
            exec("INSERT INTO reservations VALUES ('r1', '$lekce', 'INSTANCE', 2, 'CONFIRMED')")
            exec("INSERT INTO reservations VALUES ('r2', '$lekce', 'INSTANCE', 1, 'CANCELLED')")
            exec("INSERT INTO reservations VALUES ('r3', '$kurz', 'SERIES', 3, 'PENDING_PAYMENT')")
            exec("INSERT INTO reservations VALUES ('r4', '$kurz', 'SERIES', 5, 'WAITLISTED')")
        }
    }

    private fun instanceSpots(): Int = transaction {
        exec("SELECT occupied_spots FROM event_instances WHERE id = '$lekce'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    private fun seriesSpots(): Int = transaction {
        exec("SELECT occupied_spots FROM event_series WHERE id = '$kurz'") { rs ->
            rs.next(); rs.getInt(1)
        } ?: -1
    }

    @Test
    fun `backfill prepocte citace z aktivnich rezervaci`() = runBlocking {
        recomputeOccupiedSpots()

        assertEquals(2, instanceSpots(), "zrušená rezervace se nepočítá, záporná hodnota zmizí")
        assertEquals(3, seriesSpots(), "čekatel z pořadníku místo nedrží")
    }

    @Test
    fun `backfill je idempotentni`() = runBlocking {
        recomputeOccupiedSpots()
        recomputeOccupiedSpots()

        assertEquals(2, instanceSpots())
        assertEquals(3, seriesSpots())
    }
}
