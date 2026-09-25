package cz.svitaninymburk.projects.reservations.repository.attendance

import cz.svitaninymburk.projects.reservations.plugins.migrateAttendanceToPerLessonKey
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nasazení běží proti existující DB, kde má `reservation_attendance` klíč jen
 * na `reservation_id`. Ověřuje, že ruční migrace tabulku přestaví, odškrtnutou
 * docházku přenese a nenamapovatelné řádky zahodí. Kromě šťastné cesty a
 * idempotence pokrývá i dva okrajové stavy: čerstvou databázi, kde tabulka
 * ještě neexistuje, a pozůstatek dřívějšího nedokončeného pokusu (zastaralá
 * `reservation_attendance_new`), který by migraci mohl natrvalo zablokovat.
 */
class AttendanceKeyMigrationTest {

    private val lessonReservationId = "00000000-0000-0000-0000-0000000000d1"
    private val lessonId = "00000000-0000-0000-0000-0000000000c1"
    private val seriesReservationId = "00000000-0000-0000-0000-0000000000d2"
    private val seriesId = "00000000-0000-0000-0000-0000000000a1"

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("attendance-key-migration", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            // Zjednodušená podoba staré databáze — jen sloupce, na kterých migrace stojí.
            exec("""
                CREATE TABLE reservations (
                    id TEXT PRIMARY KEY,
                    reference_id TEXT NOT NULL,
                    reference_type TEXT NOT NULL
                )
            """.trimIndent())
            exec("""
                CREATE TABLE reservation_attendance (
                    reservation_id TEXT PRIMARY KEY,
                    checked_in INTEGER NOT NULL DEFAULT 0,
                    checked_in_at TEXT NULL
                )
            """.trimIndent())
            exec("INSERT INTO reservations VALUES ('$lessonReservationId', '$lessonId', 'INSTANCE')")
            exec("INSERT INTO reservations VALUES ('$seriesReservationId', '$seriesId', 'SERIES')")
            exec("INSERT INTO reservation_attendance VALUES ('$lessonReservationId', 1, NULL)")
            exec("INSERT INTO reservation_attendance VALUES ('$seriesReservationId', 1, NULL)")
        }
    }

    private fun columnExists(): Boolean = transaction {
        exec("SELECT count(*) FROM pragma_table_info('reservation_attendance') WHERE name = 'instance_id'") { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
    }

    private fun rowCount(): Int = transaction {
        exec("SELECT count(*) FROM reservation_attendance") { rs -> rs.next(); rs.getInt(1) } ?: 0
    }

    private fun instanceIdOf(reservationId: String): String? = transaction {
        exec("SELECT instance_id FROM reservation_attendance WHERE reservation_id = '$reservationId'") { rs ->
            if (rs.next()) rs.getString(1) else null
        }
    }

    private fun tableExists(table: String): Boolean = transaction {
        exec("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'") { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
    }

    @Test
    fun `migration rebuilds the key and carries over attendance`() = runBlocking {
        assertTrue(!columnExists(), "výchozí stav testu: sloupec instance_id chybí")

        transaction { migrateAttendanceToPerLessonKey() }

        assertTrue(columnExists(), "migrace musí sloupec instance_id doplnit")
        assertEquals(lessonId, instanceIdOf(lessonReservationId), "docházka se mapuje na instanci rezervace")
        assertEquals(1, rowCount(), "řádek u rezervace na sérii se nedá namapovat a zahazuje se")
    }

    @Test
    fun `migration is idempotent`() = runBlocking {
        transaction { migrateAttendanceToPerLessonKey() }
        transaction { migrateAttendanceToPerLessonKey() }

        assertEquals(1, rowCount())
        assertEquals(lessonId, instanceIdOf(lessonReservationId))
    }

    @Test
    fun `migration is a no-op on a fresh database without the table`() = runBlocking {
        // Vlastní čerstvá DB bez jediné tabulky — nahrazuje stav před SchemaUtils.create,
        // kdy setup() z @BeforeTest ještě neproběhl.
        val freshDbFile = File.createTempFile("attendance-key-migration-fresh", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${freshDbFile.absolutePath}", driver = "org.sqlite.JDBC")

        transaction { migrateAttendanceToPerLessonKey() }

        assertTrue(!tableExists("reservation_attendance"), "no-op nesmí tabulku založit")
        assertTrue(!tableExists("reservation_attendance_new"), "no-op nesmí zanechat pomocnou tabulku")
    }

    @Test
    fun `stale reservation_attendance_new from an earlier attempt does not block the migration`() = runBlocking {
        // Simuluje pozůstatek dřívějšího nedokončeného běhu migrace.
        transaction {
            exec("""
                CREATE TABLE reservation_attendance_new (
                    reservation_id TEXT PRIMARY KEY,
                    checked_in INTEGER NOT NULL DEFAULT 0,
                    checked_in_at TEXT NULL
                )
            """.trimIndent())
        }

        transaction { migrateAttendanceToPerLessonKey() }

        assertTrue(columnExists(), "migrace i přes zastaralou pomocnou tabulku doplní instance_id")
        assertEquals(lessonId, instanceIdOf(lessonReservationId), "docházka se i tak namapuje na instanci rezervace")
        assertEquals(1, rowCount(), "řádek u rezervace na sérii se stále zahazuje")
    }
}
