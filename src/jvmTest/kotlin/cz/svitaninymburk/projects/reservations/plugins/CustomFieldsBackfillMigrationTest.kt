package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class CustomFieldsBackfillMigrationTest {

    private fun newDb(): Database {
        val dbFile = File.createTempFile("custom-fields-migration-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    private val templateFields = listOf(TextFieldDefinition(key = "allergies", label = "Alergie"))
    private val customCustomization = listOf(TextFieldDefinition(key = "shoe_size", label = "Velikost boty"))

    private fun insertDefinition(definitionId: Uuid, customFields: List<TextFieldDefinition>) {
        EventDefinitionsTable.insert {
            it[id] = definitionId
            it[title] = "Kroužek"
            it[description] = "desc"
            it[defaultPrice] = 100.0
            it[defaultCapacity] = 10
            it[defaultDurationMs] = 3_600_000L
            it[allowedPaymentTypes] = listOf(PaymentType.BANK_TRANSFER)
            it[EventDefinitionsTable.customFields] = customFields
        }
    }

    private fun insertSeries(seriesId: Uuid, definitionId: Uuid, customFields: List<TextFieldDefinition>) {
        EventSeriesTable.insert {
            it[id] = seriesId
            it[EventSeriesTable.definitionId] = definitionId
            it[title] = "Kroužek"
            it[description] = "desc"
            it[price] = 500.0
            it[capacity] = 8
            it[startDate] = LocalDate(2026, 9, 1)
            it[endDate] = LocalDate(2026, 12, 1)
            it[lessonCount] = 10
            it[allowedPaymentTypes] = listOf(PaymentType.BANK_TRANSFER)
            it[EventSeriesTable.customFields] = customFields
        }
    }

    private fun insertInstance(
        instanceId: Uuid,
        definitionId: Uuid,
        seriesId: Uuid?,
        customFields: List<TextFieldDefinition>,
    ) {
        EventInstancesTable.insert {
            it[id] = instanceId
            it[EventInstancesTable.definitionId] = definitionId
            it[EventInstancesTable.seriesId] = seriesId
            it[title] = "Lekce"
            it[description] = "desc"
            it[startDateTime] = LocalDateTime(2026, 9, 2, 9, 0)
            it[endDateTime] = LocalDateTime(2026, 9, 2, 10, 0)
            it[price] = 50.0
            it[capacity] = 8
            it[allowedPaymentTypes] = listOf(PaymentType.BANK_TRANSFER)
            it[EventInstancesTable.customFields] = customFields
        }
    }

    @Test
    fun `fresh database - migration is a no-op and does not throw`() {
        val db = newDb()
        transaction(db) {
            SchemaUtils.create(EventDefinitionsTable, EventSeriesTable, EventInstancesTable)
            backfillCustomFieldsFromTemplates()
        }
    }

    @Test
    fun `series and instances with empty custom fields inherit them from the template`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val seriesId = Uuid.random()
        val seriesLessonId = Uuid.random()
        val standaloneInstanceId = Uuid.random()

        transaction(db) {
            SchemaUtils.create(EventDefinitionsTable, EventSeriesTable, EventInstancesTable)
            insertDefinition(definitionId, templateFields)
            insertSeries(seriesId, definitionId, emptyList())
            insertInstance(seriesLessonId, definitionId, seriesId, emptyList())
            insertInstance(standaloneInstanceId, definitionId, null, emptyList())
        }

        transaction(db) { backfillCustomFieldsFromTemplates() }

        transaction(db) {
            val series = EventSeriesTable.selectAll().single { it[EventSeriesTable.id] == seriesId }
            assertEquals(templateFields, series[EventSeriesTable.customFields])

            val seriesLesson = EventInstancesTable.selectAll().single { it[EventInstancesTable.id] == seriesLessonId }
            assertEquals(templateFields, seriesLesson[EventInstancesTable.customFields])

            val standaloneInstance = EventInstancesTable.selectAll().single { it[EventInstancesTable.id] == standaloneInstanceId }
            assertEquals(templateFields, standaloneInstance[EventInstancesTable.customFields])
        }
    }

    @Test
    fun `series and instances with their own custom fields are left untouched`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val seriesId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            SchemaUtils.create(EventDefinitionsTable, EventSeriesTable, EventInstancesTable)
            insertDefinition(definitionId, templateFields)
            insertSeries(seriesId, definitionId, customCustomization)
            insertInstance(instanceId, definitionId, seriesId, customCustomization)
        }

        transaction(db) { backfillCustomFieldsFromTemplates() }

        transaction(db) {
            val series = EventSeriesTable.selectAll().single { it[EventSeriesTable.id] == seriesId }
            assertEquals(customCustomization, series[EventSeriesTable.customFields])

            val instance = EventInstancesTable.selectAll().single { it[EventInstancesTable.id] == instanceId }
            assertEquals(customCustomization, instance[EventInstancesTable.customFields])
        }
    }

    @Test
    fun `running the backfill twice does not change the outcome`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val seriesId = Uuid.random()

        transaction(db) {
            SchemaUtils.create(EventDefinitionsTable, EventSeriesTable, EventInstancesTable)
            insertDefinition(definitionId, templateFields)
            insertSeries(seriesId, definitionId, emptyList())
        }

        transaction(db) { backfillCustomFieldsFromTemplates() }
        transaction(db) { backfillCustomFieldsFromTemplates() }

        transaction(db) {
            val series = EventSeriesTable.selectAll().single { it[EventSeriesTable.id] == seriesId }
            assertEquals(templateFields, series[EventSeriesTable.customFields])
        }
    }
}
