package cz.svitaninymburk.projects.reservations.plugins

import cz.svitaninymburk.projects.reservations.event.BooleanFieldDefinition
import cz.svitaninymburk.projects.reservations.event.BooleanValue
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.NumberValue
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.repository.event.EventDefinitionsTable
import cz.svitaninymburk.projects.reservations.repository.event.EventInstancesTable
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesTable
import cz.svitaninymburk.projects.reservations.repository.reservation.ReferenceDbDiscriminator
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationsTable
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
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Reprodukuje produkční chybu: termíny vytvořené před opravou generování klíčů
 * (klíč se počítal jako "field_${customFields.size}", takže po smazání pole a přidání
 * nového vznikla duplicita) mají v custom_fields dvě pole se stejným `key`.
 * Hodnoty v rezervačním formuláři jsou klíčované právě přes `key`, takže si taková
 * pole navzájem přepisují hodnotu — uživatelsky se "vzájemně vylučují".
 */
class CustomFieldKeysDeduplicationMigrationTest {

    private fun newDb(): Database {
        val dbFile = File.createTempFile("custom-field-keys-migration-test", ".db").also { it.deleteOnExit() }
        return Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
    }

    private fun createSchema() = SchemaUtils.create(
        EventDefinitionsTable,
        EventSeriesTable,
        EventInstancesTable,
        ReservationsTable,
    )

    private val duplicatedFields = listOf(
        TextFieldDefinition(key = "field_1", label = "Jméno dítěte"),
        TextFieldDefinition(key = "field_1", label = "Věk dítěte"),
    )

    private fun insertDefinition(definitionId: Uuid, customFields: List<CustomFieldDefinition>) {
        EventDefinitionsTable.insert {
            it[id] = definitionId
            it[title] = "Podpůrná skupina"
            it[description] = "desc"
            it[defaultPrice] = 100.0
            it[defaultCapacity] = 10
            it[defaultDurationMs] = 3_600_000L
            it[allowedPaymentTypes] = listOf(PaymentInfo.Type.BANK_TRANSFER)
            it[EventDefinitionsTable.customFields] = customFields
        }
    }

    private fun insertSeries(seriesId: Uuid, definitionId: Uuid, customFields: List<CustomFieldDefinition>) {
        EventSeriesTable.insert {
            it[id] = seriesId
            it[EventSeriesTable.definitionId] = definitionId
            it[title] = "Podpůrná skupina"
            it[description] = "desc"
            it[price] = 500.0
            it[capacity] = 8
            it[startDate] = LocalDate(2026, 9, 1)
            it[endDate] = LocalDate(2026, 12, 1)
            it[lessonCount] = 10
            it[allowedPaymentTypes] = listOf(PaymentInfo.Type.BANK_TRANSFER)
            it[EventSeriesTable.customFields] = customFields
        }
    }

    private fun insertInstance(
        instanceId: Uuid,
        definitionId: Uuid,
        customFields: List<CustomFieldDefinition>,
        seriesId: Uuid? = null,
    ) {
        EventInstancesTable.insert {
            it[id] = instanceId
            it[EventInstancesTable.definitionId] = definitionId
            it[EventInstancesTable.seriesId] = seriesId
            it[title] = "Podpůrná skupina v náhradních prostorách"
            it[description] = "desc"
            it[startDateTime] = LocalDateTime(2026, 8, 25, 9, 0)
            it[endDateTime] = LocalDateTime(2026, 8, 25, 10, 0)
            it[price] = 50.0
            it[capacity] = 8
            it[allowedPaymentTypes] = listOf(PaymentInfo.Type.BANK_TRANSFER)
            it[EventInstancesTable.customFields] = customFields
        }
    }

    private fun insertReservation(referenceId: Uuid, customValues: Map<String, CustomFieldValue>) {
        ReservationsTable.insert {
            it[id] = Uuid.random()
            it[ReservationsTable.referenceId] = referenceId
            it[referenceType] = ReferenceDbDiscriminator.INSTANCE
            it[contactName] = "Jana Nováková"
            it[contactEmail] = "jana@example.com"
            it[seatCount] = 1
            it[totalPrice] = 50.0
            it[status] = Reservation.Status.CONFIRMED
            it[createdAt] = Clock.System.now()
            it[ReservationsTable.customValues] = customValues
            it[paymentType] = PaymentInfo.Type.BANK_TRANSFER
        }
    }

    private fun keysOfInstance(instanceId: Uuid): List<String> =
        EventInstancesTable.selectAll()
            .single { it[EventInstancesTable.id] == instanceId }[EventInstancesTable.customFields]
            .map { it.key }

    @Test
    fun `fresh database - migration is a no-op and does not throw`() {
        val db = newDb()
        transaction(db) {
            createSchema()
            deduplicateCustomFieldKeys()
        }
    }

    @Test
    fun `duplicate keys on an instance are renamed so every field is addressable`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, duplicatedFields)
            insertInstance(instanceId, definitionId, duplicatedFields)
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            val fields = EventInstancesTable.selectAll()
                .single { it[EventInstancesTable.id] == instanceId }[EventInstancesTable.customFields]
            assertEquals(2, fields.map { it.key }.toSet().size, "oba fieldy musí mít unikátní klíč")
            assertEquals(listOf("Jméno dítěte", "Věk dítěte"), fields.map { it.label }, "labely a pořadí zůstávají")
            assertEquals("field_1", fields.first().key, "první výskyt si klíč drží")
        }
    }

    @Test
    fun `duplicate keys are repaired on definitions and series too`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val seriesId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, duplicatedFields)
            insertSeries(seriesId, definitionId, duplicatedFields)
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            val definitionKeys = EventDefinitionsTable.selectAll()
                .single { it[EventDefinitionsTable.id] == definitionId }[EventDefinitionsTable.customFields]
                .map { it.key }
            assertEquals(2, definitionKeys.toSet().size)

            val seriesKeys = EventSeriesTable.selectAll()
                .single { it[EventSeriesTable.id] == seriesId }[EventSeriesTable.customFields]
                .map { it.key }
            assertEquals(2, seriesKeys.toSet().size)
        }
    }

    @Test
    fun `a key already used by an existing reservation value is not reused`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, duplicatedFields)
            insertInstance(instanceId, definitionId, duplicatedFields)
            // Hodnota po dávno smazaném poli field_0 — nový klíč ji nesmí "zdědit".
            insertReservation(
                instanceId,
                mapOf(
                    "field_0" to TextValue("field_0", "hodnota smazaného pole"),
                    "field_1" to TextValue("field_1", "Petr"),
                ),
            )
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(listOf("field_1", "field_2"), keysOfInstance(instanceId))
        }
    }

    @Test
    fun `rows with unique keys are left untouched`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()
        val uniqueFields = listOf(
            TextFieldDefinition(key = "field_1", label = "Jméno dítěte"),
            TextFieldDefinition(key = "field_0", label = "Věk dítěte"),
        )

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, uniqueFields)
            insertInstance(instanceId, definitionId, uniqueFields)
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) { assertEquals(listOf("field_1", "field_0"), keysOfInstance(instanceId)) }
    }

    @Test
    fun `running the migration twice does not change the outcome`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, duplicatedFields)
            insertInstance(instanceId, definitionId, duplicatedFields)
        }

        transaction(db) { deduplicateCustomFieldKeys() }
        val afterFirstRun = transaction(db) { keysOfInstance(instanceId) }
        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) { assertEquals(afterFirstRun, keysOfInstance(instanceId)) }
    }

    private fun reservationValues(referenceId: Uuid): List<Map<String, CustomFieldValue>> =
        ReservationsTable.selectAll()
            .filter { it[ReservationsTable.referenceId] == referenceId }
            .map { it[ReservationsTable.customValues] }

    /** Přesně tvar produkčního dat: checkbox a textové pole sdílely klíč field_1. */
    private val realWorldFields = listOf(
        BooleanFieldDefinition(key = "field_1", label = "Beru s sebou dítě/děti"),
        TextFieldDefinition(key = "field_1", label = "Napište prosím jméno/a a věk/y dětí."),
    )

    @Test
    fun `an orphaned value is moved to the field whose type matches it`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, realWorldFields)
            insertInstance(instanceId, definitionId, realWorldFields)
            // Textová odpověď skončila pod klíčem checkboxu, protože obě pole
            // zapisovala do stejného slotu.
            insertReservation(instanceId, mapOf("field_1" to TextValue("field_1", "Miky, 2 roky")))
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(listOf("field_1", "field_0"), keysOfInstance(instanceId))
            assertEquals(
                listOf(mapOf("field_0" to TextValue("field_0", "Miky, 2 roky"))),
                reservationValues(instanceId),
                "hodnota i její fieldKey musí ukazovat na textové pole",
            )
        }
    }

    @Test
    fun `a value matching the field that kept its key stays put`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, realWorldFields)
            insertInstance(instanceId, definitionId, realWorldFields)
            insertReservation(instanceId, mapOf("field_1" to BooleanValue("field_1", true)))
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(
                listOf(mapOf("field_1" to BooleanValue("field_1", true))),
                reservationValues(instanceId),
            )
        }
    }

    @Test
    fun `a value whose type matches neither duplicate field is left alone`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, realWorldFields)
            insertInstance(instanceId, definitionId, realWorldFields)
            insertReservation(instanceId, mapOf("field_1" to NumberValue("field_1", 3f)))
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(
                listOf(mapOf("field_1" to NumberValue("field_1", 3f))),
                reservationValues(instanceId),
            )
        }
    }

    @Test
    fun `an ambiguous value is left alone when both duplicate fields have the same type`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, duplicatedFields)
            insertInstance(instanceId, definitionId, duplicatedFields)
            insertReservation(instanceId, mapOf("field_1" to TextValue("field_1", "Petr")))
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(
                listOf(mapOf("field_1" to TextValue("field_1", "Petr"))),
                reservationValues(instanceId),
                "typ nerozliší, kterému poli hodnota patří — nehádáme",
            )
        }
    }

    @Test
    fun `reservations of untouched events are not rewritten`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val cleanInstanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, realWorldFields)
            insertInstance(cleanInstanceId, definitionId, listOf(realWorldFields[0], TextFieldDefinition("field_0", "Jména")))
            insertReservation(cleanInstanceId, mapOf("field_1" to TextValue("field_1", "nechat být")))
        }

        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) {
            assertEquals(
                listOf(mapOf("field_1" to TextValue("field_1", "nechat být"))),
                reservationValues(cleanInstanceId),
            )
        }
    }

    @Test
    fun `remapping reservation values is idempotent`() {
        val db = newDb()
        val definitionId = Uuid.random()
        val instanceId = Uuid.random()

        transaction(db) {
            createSchema()
            insertDefinition(definitionId, realWorldFields)
            insertInstance(instanceId, definitionId, realWorldFields)
            insertReservation(instanceId, mapOf("field_1" to TextValue("field_1", "Miky, 2 roky")))
        }

        transaction(db) { deduplicateCustomFieldKeys() }
        val afterFirstRun = transaction(db) { reservationValues(instanceId) }
        transaction(db) { deduplicateCustomFieldKeys() }

        transaction(db) { assertEquals(afterFirstRun, reservationValues(instanceId)) }
    }
}
