package cz.svitaninymburk.projects.reservations.repository.audit

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class AuditRepositorySpec {

    private fun event(
        type: AuditEventType = AuditEventType.RESERVATION_CREATED,
        seriesId: Uuid? = null,
        instanceId: Uuid? = null,
        daysAgo: Long = 0,
    ) = NewAuditEvent(
        type = type,
        actorType = AuditActorType.CUSTOMER,
        actorLabel = "kdo@example.com",
        subjectLabel = "Jana Nováková",
        seriesId = seriesId,
        instanceId = instanceId,
        occurredAt = Clock.System.now() - daysAgo.days,
    )

    @Test
    fun `lesson detail shows records of its own lesson`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lesson = Uuid.random()
        repo.record(event(instanceId = lesson))
        repo.record(event(instanceId = Uuid.random()))

        val found = repo.findForEvent(lesson, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals(1, found.size)
    }

    @Test
    fun `series detail also shows activity in its lessons`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val series = Uuid.random()
        repo.record(event(seriesId = series))                              // zápis na kurz
        repo.record(event(seriesId = series, instanceId = Uuid.random()))  // omluvenka z lekce

        val found = repo.findForEvent(series, isSeries = true, category = null, page = 0, pageSize = 50)

        assertEquals(2, found.size)
    }

    /**
     * Účastníci jsou zapsaní na kurz, ne na jednotlivou lekci — bez tohohle
     * by detail lekce o nich neukázal vůbec nic.
     */
    @Test
    fun `lesson detail also takes over records kept on the series`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val series = Uuid.random()
        val lesson = Uuid.random()
        repo.record(event(seriesId = series))                            // přihláška na kurz
        repo.record(event(seriesId = series, instanceId = lesson))        // omluvenka z téhle lekce
        repo.record(event(seriesId = series, instanceId = Uuid.random()))// omluvenka z jiné lekce

        val found = repo.findForEvent(
            lesson, isSeries = false, category = null, page = 0, pageSize = 50, parentSeriesId = series,
        )

        assertEquals(2, found.size)
    }

    @Test
    fun `category filter applies`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lesson = Uuid.random()
        repo.record(event(type = AuditEventType.RESERVATION_CREATED, instanceId = lesson))
        repo.record(event(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, instanceId = lesson))

        val emails = repo.findForEvent(lesson, false, AuditCategory.EMAIL, 0, 50)

        assertEquals(1, emails.size)
        assertEquals(AuditEventType.EMAIL_RESERVATION_CONFIRMATION, emails.single().type)
        assertEquals(2, repo.countForEvent(lesson, false, null))
    }

    @Test
    fun `paging returns newest first`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lesson = Uuid.random()
        repo.record(event(instanceId = lesson, daysAgo = 3))
        repo.record(event(instanceId = lesson, daysAgo = 1))
        repo.record(event(instanceId = lesson, daysAgo = 2))

        val firstPage = repo.findForEvent(lesson, false, null, page = 0, pageSize = 2)
        val secondPage = repo.findForEvent(lesson, false, null, page = 1, pageSize = 2)

        assertEquals(2, firstPage.size)
        assertEquals(1, secondPage.size)
        assertTrue(firstPage[0].occurredAt > firstPage[1].occurredAt)
        assertTrue(firstPage[1].occurredAt > secondPage[0].occurredAt)
    }

    @Test
    fun `retention deletes entries older than a year and keeps newer ones`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lesson = Uuid.random()
        repo.record(event(instanceId = lesson, daysAgo = 400))
        repo.record(event(instanceId = lesson, daysAgo = 300))

        val deleted = repo.deleteOlderThan(Clock.System.now() - 365.days)

        assertEquals(1, deleted)
        assertEquals(1, repo.countAll())
    }
}
