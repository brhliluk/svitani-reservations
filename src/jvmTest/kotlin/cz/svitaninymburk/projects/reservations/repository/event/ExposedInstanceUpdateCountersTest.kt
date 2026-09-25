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
 * `update()` volají úpravy v administraci stylem `update(instance.copy(...))` nad
 * instancí načtenou o chvíli dřív. Čítače obsazenosti mezitím mohla posunout
 * rezervace nebo zápis do pořadníku — zapsat je z načtené kopie by tu změnu smazalo.
 */
class ExposedInstanceUpdateCountersTest {

    private val repo = ExposedEventInstanceRepository()
    private lateinit var defId: Uuid

    @BeforeTest
    fun setup() {
        val dbFile = File.createTempFile("instance-update-counters-test", ".db").also { it.deleteOnExit() }
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

    @Test
    fun `update does not overwrite counters with values from a previously loaded copy`() = runBlocking {
        val created = repo.create(
            EventInstance(
                id = Uuid.random(), definitionId = defId, title = "Původní", description = "D",
                startDateTime = LocalDateTime(2099, 1, 1, 9, 0), endDateTime = LocalDateTime(2099, 1, 1, 10, 0),
                price = 100.0, capacity = 2, waitlistCapacity = 3,
            )
        )
        val snapshot = repo.get(created.id)!!

        // Mezi načtením a uložením přijde rezervace i zápis do pořadníku.
        repo.attemptToReserveSpots(created.id, 2)
        repo.attemptToReserveWaitlistSpot(created.id)

        val returned = repo.update(snapshot.copy(title = "Upravený"))

        val stored = repo.get(created.id)!!
        assertEquals("Upravený", stored.title)
        assertEquals(2, stored.occupiedSpots, "rezervace mezi čtením a zápisem se nesmí ztratit")
        assertEquals(1, stored.occupiedWaitlist, "zápis do pořadníku mezi čtením a zápisem se nesmí ztratit")
        assertEquals(2, returned.occupiedSpots, "návratová hodnota má odpovídat databázi, ne předané kopii")
    }
}
