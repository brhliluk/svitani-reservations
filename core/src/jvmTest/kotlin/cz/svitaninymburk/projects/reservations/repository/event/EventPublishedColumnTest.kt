package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class EventPublishedColumnTest {

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("published-column-test", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable,
            )
        }
    }

    private fun definitionId(): Uuid = runBlocking {
        val def = EventDefinition(
            id = Uuid.random(), title = "T", description = "D",
            defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = kotlin.time.Duration.parse("1h"),
        )
        ExposedEventDefinitionRepository().create(def).id
    }

    @Test
    fun `instance isPublished round-trips through create and get`() = runBlocking {
        val repo = ExposedEventInstanceRepository()
        val defId = definitionId()
        val instance = EventInstance(
            id = Uuid.random(), definitionId = defId, title = "I", description = "D",
            startDateTime = LocalDateTime(2027, 1, 1, 9, 0), endDateTime = LocalDateTime(2027, 1, 1, 10, 0),
            price = 100.0, capacity = 10, isPublished = true,
        )
        repo.create(instance)
        assertEquals(true, repo.get(instance.id)?.isPublished)
    }

    @Test
    fun `instance isPublished defaults to false and survives update`() = runBlocking {
        val repo = ExposedEventInstanceRepository()
        val defId = definitionId()
        val instance = EventInstance(
            id = Uuid.random(), definitionId = defId, title = "I", description = "D",
            startDateTime = LocalDateTime(2027, 1, 1, 9, 0), endDateTime = LocalDateTime(2027, 1, 1, 10, 0),
            price = 100.0, capacity = 10,
        )
        repo.create(instance)
        assertEquals(false, repo.get(instance.id)?.isPublished)
        repo.update(instance.copy(isPublished = true))
        assertEquals(true, repo.get(instance.id)?.isPublished)
    }

    @Test
    fun `series isPublished round-trips`() = runBlocking {
        val repo = ExposedEventSeriesRepository()
        val defId = definitionId()
        val series = EventSeries(
            id = Uuid.random(), definitionId = defId, title = "S", description = "D",
            price = 100.0, capacity = 10,
            startDate = kotlinx.datetime.LocalDate(2027, 1, 1), endDate = kotlinx.datetime.LocalDate(2027, 3, 1),
            lessonCount = 5, isPublished = true,
        )
        repo.create(series)
        assertEquals(true, repo.get(series.id)?.isPublished)
    }
}
