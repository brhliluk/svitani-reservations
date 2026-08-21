package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Nasazení běží proti existující DB bez sloupce `allow_multiple_seats` — ověřuje, že ho
 * automatická migrace doplní na všech třech tabulkách, že se stará data chovají jako dřív
 * (výchozí `true`) a že vypnutá hodnota projde uložením i načtením.
 */
class AllowMultipleSeatsColumnTest {

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("allow-multiple-seats-column-test", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable,
            )
            // Simulace starší databáze, která sloupec ještě nemá.
            exec("ALTER TABLE event_definitions DROP COLUMN allow_multiple_seats")
            exec("ALTER TABLE event_series DROP COLUMN allow_multiple_seats")
            exec("ALTER TABLE event_instances DROP COLUMN allow_multiple_seats")
        }
    }

    private fun columnExists(table: String): Boolean = transaction {
        exec("SELECT count(*) FROM pragma_table_info('$table') WHERE name = 'allow_multiple_seats'") { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
    }

    private fun migrate() = transaction {
        listOf(EventDefinitionsTable, EventSeriesTable, EventInstancesTable).forEach { table ->
            MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false).forEach { exec(it) }
        }
    }

    private suspend fun createDefinition(allowMultipleSeats: Boolean = true) =
        ExposedEventDefinitionRepository().create(
            EventDefinition(
                id = Uuid.random(), title = "T", description = "D",
                defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = Duration.parse("1h"),
                allowMultipleSeats = allowMultipleSeats,
            )
        )

    @Test
    fun `migration adds the allow_multiple_seats column to all three event tables`() = runBlocking {
        listOf("event_definitions", "event_series", "event_instances").forEach {
            assertTrue(!columnExists(it), "výchozí stav testu: sloupec v $it chybí")
        }

        migrate()

        listOf("event_definitions", "event_series", "event_instances").forEach {
            assertTrue(columnExists(it), "migrace musí sloupec allow_multiple_seats doplnit v $it")
        }
    }

    @Test
    fun `definition allowMultipleSeats round-trips`() = runBlocking {
        migrate()
        val repo = ExposedEventDefinitionRepository()
        val definition = createDefinition(allowMultipleSeats = false)

        assertEquals(false, repo.get(definition.id)?.allowMultipleSeats)

        repo.update(definition.copy(allowMultipleSeats = true))
        assertEquals(true, repo.get(definition.id)?.allowMultipleSeats)
    }

    @Test
    fun `series allowMultipleSeats round-trips`() = runBlocking {
        migrate()
        val definition = createDefinition()
        val repo = ExposedEventSeriesRepository()
        val series = EventSeries(
            id = Uuid.random(), definitionId = definition.id, title = "S", description = "D",
            price = 1500.0, capacity = 10,
            startDate = LocalDate(2027, 1, 1), endDate = LocalDate(2027, 3, 1),
            lessonCount = 5, allowMultipleSeats = false,
        )
        repo.create(series)

        assertEquals(false, repo.get(series.id)?.allowMultipleSeats)

        repo.update(series.copy(allowMultipleSeats = true))
        assertEquals(true, repo.get(series.id)?.allowMultipleSeats)
    }

    @Test
    fun `instance allowMultipleSeats round-trips`() = runBlocking {
        migrate()
        val definition = createDefinition()
        val repo = ExposedEventInstanceRepository()
        val instance = EventInstance(
            id = Uuid.random(), definitionId = definition.id, title = "I", description = "D",
            startDateTime = LocalDateTime(2027, 1, 1, 10, 0),
            endDateTime = LocalDateTime(2027, 1, 1, 11, 0),
            price = 100.0, capacity = 10, allowMultipleSeats = false,
        )
        repo.create(instance)

        assertEquals(false, repo.get(instance.id)?.allowMultipleSeats)

        repo.update(instance.copy(allowMultipleSeats = true))
        assertEquals(true, repo.get(instance.id)?.allowMultipleSeats)
    }

    /**
     * Existující akce v produkční DB nesmí po nasazení změnit chování — migrace proto musí
     * sloupec doplnit jako NOT NULL s výchozím `true`, aby dosavadní řádky dál nabízely
     * volbu počtu míst.
     */
    @Test
    fun `migrated column defaults to true so existing rows keep offering multiple seats`() = runBlocking {
        migrate()

        listOf("event_definitions", "event_series", "event_instances").forEach { table ->
            val (notNull, default) = transaction {
                exec(
                    "SELECT \"notnull\", dflt_value FROM pragma_table_info('$table') " +
                            "WHERE name = 'allow_multiple_seats'"
                ) { rs ->
                    assertTrue(rs.next(), "sloupec v $table musí existovat")
                    rs.getInt(1) to rs.getString(2)
                }!!
            }
            assertEquals(1, notNull, "allow_multiple_seats v $table musí být NOT NULL")
            assertEquals("1", default?.trim('\''), "allow_multiple_seats v $table musí mít default true")
        }
    }
}
