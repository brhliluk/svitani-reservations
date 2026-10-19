package cz.svitaninymburk.projects.reservations.plugins

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

class EventPublishedMigrationTest {

    private object TestInstancesTable : Table("event_instances") {
        val id = uuid("id")
        val isPublished = bool("is_published").default(false)
    }

    private fun newDb(): Database {
        val dbFile = File.createTempFile("published-migration-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    @Test
    fun `first run publishes all existing rows`() {
        val db = newDb()
        transaction(db) {
            SchemaUtils.create(TestInstancesTable)
            TestInstancesTable.insert { it[id] = Uuid.random() }
            TestInstancesTable.insert { it[id] = Uuid.random() }
        }
        transaction(db) { backfillPublishedIfFirstRun("event_instances", wasMissingBefore = true) }
        transaction(db) {
            val values = TestInstancesTable.selectAll().map { it[TestInstancesTable.isPublished] }
            assertEquals(listOf(true, true), values)
        }
    }

    @Test
    fun `subsequent run leaves admin-hidden rows hidden`() {
        val db = newDb()
        transaction(db) {
            SchemaUtils.create(TestInstancesTable)
            TestInstancesTable.insert { it[id] = Uuid.random(); it[isPublished] = false }
        }
        transaction(db) { backfillPublishedIfFirstRun("event_instances", wasMissingBefore = false) }
        transaction(db) {
            assertEquals(false, TestInstancesTable.selectAll().single()[TestInstancesTable.isPublished])
        }
    }

    @Test
    fun `columnExists detects presence`() {
        val db = newDb()
        transaction(db) {
            assertEquals(false, columnExists("event_instances", "is_published"))
            SchemaUtils.create(TestInstancesTable)
            assertEquals(true, columnExists("event_instances", "is_published"))
        }
    }
}
