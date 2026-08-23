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
 * docházku přenese a nenamapovatelné řádky zahodí.
 */
class AttendanceKeyMigrationTest {

    private val rezervaceNaLekci = "00000000-0000-0000-0000-0000000000d1"
    private val lekce = "00000000-0000-0000-0000-0000000000c1"
    private val rezervaceNaKurz = "00000000-0000-0000-0000-0000000000d2"
    private val kurz = "00000000-0000-0000-0000-0000000000a1"

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
            exec("INSERT INTO reservations VALUES ('$rezervaceNaLekci', '$lekce', 'INSTANCE')")
            exec("INSERT INTO reservations VALUES ('$rezervaceNaKurz', '$kurz', 'SERIES')")
            exec("INSERT INTO reservation_attendance VALUES ('$rezervaceNaLekci', 1, NULL)")
            exec("INSERT INTO reservation_attendance VALUES ('$rezervaceNaKurz', 1, NULL)")
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

    @Test
    fun `migrace prestavi klic a prenese docházku`() = runBlocking {
        assertTrue(!columnExists(), "výchozí stav testu: sloupec instance_id chybí")

        transaction { migrateAttendanceToPerLessonKey() }

        assertTrue(columnExists(), "migrace musí sloupec instance_id doplnit")
        assertEquals(lekce, instanceIdOf(rezervaceNaLekci), "docházka se mapuje na instanci rezervace")
        assertEquals(1, rowCount(), "řádek u rezervace na sérii se nedá namapovat a zahazuje se")
    }

    @Test
    fun `migrace je idempotentni`() = runBlocking {
        transaction { migrateAttendanceToPerLessonKey() }
        transaction { migrateAttendanceToPerLessonKey() }

        assertEquals(1, rowCount())
        assertEquals(lekce, instanceIdOf(rezervaceNaLekci))
    }
}
