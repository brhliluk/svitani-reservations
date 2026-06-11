package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.repository.event.EventOwnerEmailsTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LectorEmailMigrationTest {

    // Schema of the event tables as they looked before the owner emails migration,
    // built through Exposed so UUIDs are stored exactly like production data (16-byte blobs)
    private object OldEventDefinitionsTable : Table("event_definitions") {
        val id = uuid("id")
        val lectorEmail = varchar("lector_email", 255).nullable()
    }

    private object OldEventSeriesTable : Table("event_series") {
        val id = uuid("id")
        val lectorEmail = varchar("lector_email", 255).nullable()
    }

    private object OldEventInstancesTable : Table("event_instances") {
        val id = uuid("id")
        val lectorEmail = varchar("lector_email", 255).nullable()
    }

    private fun newDb(): Database {
        val dbFile = File.createTempFile("lector-migration-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    @Test
    fun `fresh database - migration is a no-op and does not throw`() {
        val db = newDb()
        transaction(db) {
            migrateLectorEmailsToOwnerEmails()
            assertEquals(0L, EventOwnerEmailsTable.selectAll().count())
        }
    }

    @Test
    fun `pre-migration database - lector emails are copied to event_owner_emails`() {
        val db = newDb()
        val definitionId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val seriesId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val instanceId = Uuid.parse("33333333-3333-3333-3333-333333333333")

        transaction(db) {
            SchemaUtils.create(OldEventDefinitionsTable, OldEventSeriesTable, OldEventInstancesTable)
            OldEventDefinitionsTable.insert {
                it[id] = definitionId
                it[lectorEmail] = "definition@example.com"
            }
            OldEventSeriesTable.insert {
                it[id] = seriesId
                it[lectorEmail] = "series@example.com"
            }
            OldEventInstancesTable.insert {
                it[id] = instanceId
                it[lectorEmail] = ""
            }
        }

        transaction(db) {
            migrateLectorEmailsToOwnerEmails()
        }

        transaction(db) {
            val migrated = EventOwnerEmailsTable.selectAll().associate {
                it[EventOwnerEmailsTable.entityId] to
                    (it[EventOwnerEmailsTable.entityType] to it[EventOwnerEmailsTable.email])
            }
            assertEquals(
                mapOf(
                    definitionId to ("definition" to "definition@example.com"),
                    seriesId to ("series" to "series@example.com"),
                ),
                migrated,
            )
        }
    }

    @Test
    fun `already migrated database - second run does not duplicate rows`() {
        val db = newDb()
        transaction(db) {
            SchemaUtils.create(OldEventDefinitionsTable, OldEventSeriesTable, OldEventInstancesTable)
            OldEventDefinitionsTable.insert {
                it[id] = Uuid.random()
                it[lectorEmail] = "definition@example.com"
            }
        }

        transaction(db) { migrateLectorEmailsToOwnerEmails() }
        transaction(db) { migrateLectorEmailsToOwnerEmails() }

        transaction(db) {
            assertEquals(1L, EventOwnerEmailsTable.selectAll().count())
        }
    }

    @Test
    fun `post-migration database - lector_email columns already dropped, does not throw`() {
        val db = newDb()
        transaction(db) {
            exec("CREATE TABLE event_definitions (id BINARY(16) PRIMARY KEY)")
            exec("CREATE TABLE event_series (id BINARY(16) PRIMARY KEY)")
            exec("CREATE TABLE event_instances (id BINARY(16) PRIMARY KEY)")
        }
        transaction(db) {
            migrateLectorEmailsToOwnerEmails()
            assertEquals(0L, EventOwnerEmailsTable.selectAll().count())
        }
    }
}
