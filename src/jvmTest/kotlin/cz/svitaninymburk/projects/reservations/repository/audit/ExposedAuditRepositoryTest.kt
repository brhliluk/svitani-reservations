package cz.svitaninymburk.projects.reservations.repository.audit

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.audit.AuditOutcome
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Proti skutečné SQLite — filtr v [findForEvent] skládá `OR` s `IS NULL`,
 * což in-memory varianta nedokáže ověřit.
 */
class ExposedAuditRepositoryTest {

    companion object {
        private val dbFile: File = File.createTempFile("audit-test", ".db").also { it.deleteOnExit() }

        val db: Database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
        )
        val repository = ExposedAuditRepository(db)

        init {
            transaction(db) { SchemaUtils.create(AuditEventsTable) }
        }
    }

    private fun event(
        type: AuditEventType = AuditEventType.RESERVATION_CREATED,
        seriesId: Uuid? = null,
        instanceId: Uuid? = null,
        daysAgo: Long = 0,
        outcome: AuditOutcome? = null,
    ) = NewAuditEvent(
        type = type,
        actorType = AuditActorType.CUSTOMER,
        actorLabel = "kdo@example.com",
        subjectLabel = "Jana Nováková",
        seriesId = seriesId,
        instanceId = instanceId,
        outcome = outcome,
        occurredAt = Clock.System.now() - daysAgo.days,
    )

    @Test
    fun `zapis a cteni jedne akce`() = runBlocking {
        val lekce = Uuid.random()
        repository.record(event(instanceId = lekce, outcome = AuditOutcome.FAILURE))

        val found = repository.findForEvent(lekce, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals(1, found.size)
        assertEquals(AuditOutcome.FAILURE, found.single().outcome)
        assertEquals(AuditCategory.RESERVATION, found.single().category)
    }

    /** Správa akce má vlastní kategorii — musí se uložit a jít podle ní filtrovat. */
    @Test
    fun `zmeny akce se ukladaji pod vlastni kategorii`() = runBlocking {
        val kurz = Uuid.random()
        repository.record(event(type = AuditEventType.SERIES_UNPUBLISHED, seriesId = kurz))
        repository.record(event(seriesId = kurz))

        val found = repository.findForEvent(kurz, isSeries = true, category = AuditCategory.MANAGEMENT, page = 0, pageSize = 50)

        assertEquals(listOf(AuditEventType.SERIES_UNPUBLISHED), found.map { it.type })
        assertEquals(AuditCategory.MANAGEMENT, found.single().category)
    }

    @Test
    fun `detail kurzu bere i deni ve svych lekcich`() = runBlocking {
        val kurz = Uuid.random()
        repository.record(event(seriesId = kurz))
        repository.record(event(seriesId = kurz, instanceId = Uuid.random()))

        assertEquals(2, repository.countForEvent(kurz, isSeries = true, category = null))
    }

    /** Účastníci jsou zapsaní na kurz — bez tohohle by detail lekce zůstal prázdný. */
    @Test
    fun `detail lekce prebira zaznamy vedene na kurzu`() = runBlocking {
        val kurz = Uuid.random()
        val lekce = Uuid.random()
        repository.record(event(seriesId = kurz))                              // přihláška na kurz
        repository.record(event(seriesId = kurz, instanceId = lekce))          // omluvenka z téhle lekce
        repository.record(event(seriesId = kurz, instanceId = Uuid.random()))  // z jiné lekce

        val bezRodice = repository.countForEvent(lekce, isSeries = false, category = null)
        val sRodicem = repository.countForEvent(lekce, isSeries = false, category = null, parentSeriesId = kurz)

        assertEquals(1, bezRodice)
        assertEquals(2, sRodicem)
    }

    @Test
    fun `filtr kategorie a razeni od nejnovejsiho`() = runBlocking {
        val lekce = Uuid.random()
        repository.recordAll(
            listOf(
                event(instanceId = lekce, daysAgo = 3),
                event(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, instanceId = lekce, daysAgo = 1),
                event(type = AuditEventType.PAYMENT_PAIRED_AUTO, instanceId = lekce, daysAgo = 2),
            )
        )

        val vse = repository.findForEvent(lekce, false, null, 0, 50)
        val maily = repository.findForEvent(lekce, false, AuditCategory.EMAIL, 0, 50)

        assertEquals(3, vse.size)
        assertEquals(1, maily.size)
        assertTrue(vse[0].occurredAt > vse[1].occurredAt, "má se řadit od nejnovějšího")
    }

    @Test
    fun `retence smaze jen starsi nez rok`() = runBlocking {
        val lekce = Uuid.random()
        repository.recordAll(listOf(event(instanceId = lekce, daysAgo = 400), event(instanceId = lekce, daysAgo = 10)))

        repository.deleteOlderThan(Clock.System.now() - 365.days)

        assertEquals(1, repository.countForEvent(lekce, false, null))
    }

    @Test
    fun `wallet code se ulozi a precte`() = runBlocking {
        val lekce = Uuid.random()
        repository.record(
            event(type = AuditEventType.PAYMENT_REFUNDED, instanceId = lekce)
                .copy(walletCode = "SVIT-AB12-CD34")
        )

        val found = repository.findForEvent(lekce, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals("SVIT-AB12-CD34", found.single().walletCode)
    }

    @Test
    fun `bez peněženky zustane null`() = runBlocking {
        val lekce = Uuid.random()
        repository.record(event(instanceId = lekce))
        assertEquals(null, repository.findForEvent(lekce, false, null, 0, 50).single().walletCode)
    }

    @Test
    fun `recordAll s prazdnym seznamem nespadne`() = runBlocking {
        repository.recordAll(emptyList())
    }
}
