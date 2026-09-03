package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Nasazení běží proti existující DB bez sloupce `lesson_price` — ověřuje, že ho
 * automatická migrace doplní a že cena za lekci pak projde uložením i načtením.
 */
class EventSeriesLessonPriceColumnTest {

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("lesson-price-column-test", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable,
            )
            // Simulace starší databáze, která sloupec ještě nemá.
            exec("ALTER TABLE event_series DROP COLUMN lesson_price")
        }
    }

    private fun columnExists(): Boolean = transaction {
        exec("SELECT count(*) FROM pragma_table_info('event_series') WHERE name = 'lesson_price'") { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
    }

    @Test
    fun `migration adds the lesson_price column and the value round-trips`() = runBlocking {
        assertTrue(!columnExists(), "výchozí stav testu: sloupec chybí")

        transaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(EventSeriesTable, withLogs = false)
                .forEach { exec(it) }
        }
        assertTrue(columnExists(), "migrace musí sloupec lesson_price doplnit")

        val def = ExposedEventDefinitionRepository().create(
            EventDefinition(
                id = Uuid.random(), title = "T", description = "D",
                defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = kotlin.time.Duration.parse("1h"),
            )
        )
        val repo = ExposedEventSeriesRepository()
        val series = EventSeries(
            id = Uuid.random(), definitionId = def.id, title = "S", description = "D",
            price = 1500.0, capacity = 10,
            startDate = LocalDate(2027, 1, 1), endDate = LocalDate(2027, 3, 1),
            lessonCount = 5, lessonPrice = 250.0,
        )
        repo.create(series)
        assertEquals(250.0, repo.get(series.id)?.lessonPrice)

        repo.update(series.copy(lessonPrice = null))
        assertEquals(null, repo.get(series.id)?.lessonPrice)
    }
}
