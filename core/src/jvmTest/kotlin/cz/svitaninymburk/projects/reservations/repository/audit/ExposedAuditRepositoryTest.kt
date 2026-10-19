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
    fun `write and read of a single event`() = runBlocking {
        val lesson = Uuid.random()
        repository.record(event(instanceId = lesson, outcome = AuditOutcome.FAILURE))

        val found = repository.findForEvent(lesson, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals(1, found.size)
        assertEquals(AuditOutcome.FAILURE, found.single().outcome)
        assertEquals(AuditCategory.RESERVATION, found.single().category)
    }

    /** Správa akce má vlastní kategorii — musí se uložit a jít podle ní filtrovat. */
    @Test
    fun `event changes are stored under their own category`() = runBlocking {
        val series = Uuid.random()
        repository.record(event(type = AuditEventType.SERIES_UNPUBLISHED, seriesId = series))
        repository.record(event(seriesId = series))

        val found = repository.findForEvent(series, isSeries = true, category = AuditCategory.MANAGEMENT, page = 0, pageSize = 50)

        assertEquals(listOf(AuditEventType.SERIES_UNPUBLISHED), found.map { it.type })
        assertEquals(AuditCategory.MANAGEMENT, found.single().category)
    }

    @Test
    fun `series detail also includes activity in its lessons`() = runBlocking {
        val series = Uuid.random()
        repository.record(event(seriesId = series))
        repository.record(event(seriesId = series, instanceId = Uuid.random()))

        assertEquals(2, repository.countForEvent(series, isSeries = true, category = null))
    }

    /** Účastníci jsou zapsaní na kurz — bez tohohle by detail lekce zůstal prázdný. */
    @Test
    fun `lesson detail takes over records kept on the series`() = runBlocking {
        val series = Uuid.random()
        val lesson = Uuid.random()
        repository.record(event(seriesId = series))                              // přihláška na kurz
        repository.record(event(seriesId = series, instanceId = lesson))          // omluvenka z téhle lekce
        repository.record(event(seriesId = series, instanceId = Uuid.random()))  // z jiné lekce

        val withoutParent = repository.countForEvent(lesson, isSeries = false, category = null)
        val withParent = repository.countForEvent(lesson, isSeries = false, category = null, parentSeriesId = series)

        assertEquals(1, withoutParent)
        assertEquals(2, withParent)
    }

    @Test
    fun `category filter and newest-first ordering`() = runBlocking {
        val lesson = Uuid.random()
        repository.recordAll(
            listOf(
                event(instanceId = lesson, daysAgo = 3),
                event(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, instanceId = lesson, daysAgo = 1),
                event(type = AuditEventType.PAYMENT_PAIRED_AUTO, instanceId = lesson, daysAgo = 2),
            )
        )

        val all = repository.findForEvent(lesson, false, null, 0, 50)
        val emails = repository.findForEvent(lesson, false, AuditCategory.EMAIL, 0, 50)

        assertEquals(3, all.size)
        assertEquals(1, emails.size)
        assertTrue(all[0].occurredAt > all[1].occurredAt, "má se řadit od nejnovějšího")
    }

    @Test
    fun `retention deletes only entries older than a year`() = runBlocking {
        val lesson = Uuid.random()
        repository.recordAll(listOf(event(instanceId = lesson, daysAgo = 400), event(instanceId = lesson, daysAgo = 10)))

        repository.deleteOlderThan(Clock.System.now() - 365.days)

        assertEquals(1, repository.countForEvent(lesson, false, null))
    }

    @Test
    fun `wallet code is stored and read back`() = runBlocking {
        val lesson = Uuid.random()
        repository.record(
            event(type = AuditEventType.PAYMENT_REFUNDED, instanceId = lesson)
                .copy(walletCode = "SVIT-AB12-CD34")
        )

        val found = repository.findForEvent(lesson, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals("SVIT-AB12-CD34", found.single().walletCode)
    }

    @Test
    fun `without a wallet it stays null`() = runBlocking {
        val lesson = Uuid.random()
        repository.record(event(instanceId = lesson))
        assertEquals(null, repository.findForEvent(lesson, false, null, 0, 50).single().walletCode)
    }

    @Test
    fun `recordAll with an empty list does not fail`() = runBlocking {
        repository.recordAll(emptyList())
    }
}
