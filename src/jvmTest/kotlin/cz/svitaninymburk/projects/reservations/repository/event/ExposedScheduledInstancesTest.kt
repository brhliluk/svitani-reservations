package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * `AdminScheduleSpec` jede přes in-memory repozitář, takže by neodhalil chybu
 * v SQL predikátu ani v `ORDER BY`/`LIMIT`/`OFFSET` v [ExposedEventInstanceRepository].
 * Rozvrh je jediné místo, kde se stránkuje přes všechny termíny naráz, proto se
 * ta cesta ověřuje proti reálné SQLite.
 */
class ExposedScheduledInstancesTest {

    private val repo = ExposedEventInstanceRepository()
    private lateinit var defId: Uuid

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("scheduled-instances-test", ".db").also { it.deleteOnExit() }
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                EventDefinitionsTable, EventSeriesTable, EventInstancesTable, EventOwnerEmailsTable,
            )
        }
        defId = runBlocking {
            ExposedEventDefinitionRepository().create(
                EventDefinition(
                    id = Uuid.random(), title = "T", description = "D",
                    defaultPrice = 100.0, defaultCapacity = 10, defaultDuration = 1.hours,
                )
            ).id
        }
    }

    private suspend fun add(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        isPublished: Boolean = true,
        isCancelled: Boolean = false,
    ) = repo.create(
        EventInstance(
            id = Uuid.random(), definitionId = defId, title = title, description = "D",
            startDateTime = start, endDateTime = end, price = 100.0, capacity = 10,
            isPublished = isPublished, isCancelled = isCancelled,
        )
    )

    private fun at(day: Int, hour: Int = 9) = LocalDateTime(2027, 1, day, hour, 0)

    @Test
    fun `ordering is ascending by start and paging slices that order`() = runBlocking {
        add("d3", at(3), at(3, 10))
        add("d1", at(1), at(1, 10))
        add("d2", at(2), at(2, 10))

        assertEquals(listOf("d1", "d2"), repo.findScheduledPaged(null, 0, 2).map { it.title })
        assertEquals(listOf("d3"), repo.findScheduledPaged(null, 1, 2).map { it.title })
        assertEquals(3L, repo.countScheduled())
    }

    @Test
    fun `unpublished and cancelled rows are filtered out in SQL`() = runBlocking {
        add("verejna", at(1), at(1, 10))
        add("skryta", at(2), at(2, 10), isPublished = false)
        add("zrusena", at(3), at(3, 10), isCancelled = true)

        assertEquals(listOf("verejna"), repo.findScheduledPaged(null, 0, 20).map { it.title })
        assertEquals(1L, repo.countScheduled())
    }

    @Test
    fun `from and until bound on endDateTime, so a running term counts as upcoming`() = runBlocking {
        add("skoncila", at(1), at(1, 10))
        add("probiha", at(1), at(5, 10))
        add("budouci", at(6), at(6, 10))
        val now = at(2)

        assertEquals(
            listOf("probiha", "budouci"),
            repo.findScheduledPaged(now, 0, 20).map { it.title },
            "'probiha' začala před now, ale končí po něm",
        )
        assertEquals(2L, repo.countScheduled(from = now))
        assertEquals(1L, repo.countScheduled(until = now), "pastCount = jen ta, co skončila")
    }
}
