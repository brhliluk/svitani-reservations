package cz.svitaninymburk.projects.reservations.slot.repository

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nasazení hlídání nad existující produkční DB nesmí potřebovat ruční migraci:
 * start (stejně jako `configureDatabases`) doplní tabulky modulu i sloupce úseku
 * v `reservations` a další start už nemá co měnit.
 */
class SlotSchemaMigrationSpec {

    private val tables = (listOf(ReservationsTable) + SLOT_TABLES).toTypedArray()

    private fun JdbcTransaction.migrate() {
        SchemaUtils.create(*tables)
        MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false).forEach { exec(it) }
    }

    private fun JdbcTransaction.columns(table: String): List<String> =
        exec("SELECT name FROM pragma_table_info('$table')") { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        } ?: emptyList()

    @Test
    fun `existing database gets slot tables and columns and is stable afterwards`() {
        val dbFile = File.createTempFile("slot-migration", ".db").also { it.deleteOnExit() }
        val db = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")

        // Stav před hlídáním: rezervace bez sloupců úseku a s jedním řádkem.
        transaction(db) {
            SchemaUtils.create(ReservationsTable)
            exec("ALTER TABLE reservations DROP COLUMN slot_start")
            exec("ALTER TABLE reservations DROP COLUMN slot_end")
            exec(
                """
                INSERT INTO reservations (id, reference_id, reference_type, contact_name, contact_email,
                    total_price, status, created_at, custom_values, payment_type)
                VALUES (randomblob(16), randomblob(16), 'INSTANCE', 'Jana', 'jana@test.cz',
                    100.0, 'CONFIRMED', '2026-10-01 10:00:00', '{}', 'ON_SITE')
                """.trimIndent()
            )
        }

        transaction(db) { migrate() }

        transaction(db) {
            assertTrue(columns("reservations").containsAll(listOf("slot_start", "slot_end")))
            SLOT_TABLES.forEach { assertTrue(columns(it.tableName).isNotEmpty(), it.tableName) }
            val rows = exec("SELECT count(*) FROM reservations WHERE slot_start IS NULL") { rs -> rs.next(); rs.getInt(1) }
            assertEquals(1, rows)

            assertEquals(emptyList(), MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false))
        }
    }
}
