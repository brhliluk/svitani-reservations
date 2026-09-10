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
    fun `detail lekce vidi zaznamy sve lekce`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lekce = Uuid.random()
        repo.record(event(instanceId = lekce))
        repo.record(event(instanceId = Uuid.random()))

        val found = repo.findForEvent(lekce, isSeries = false, category = null, page = 0, pageSize = 50)

        assertEquals(1, found.size)
    }

    @Test
    fun `detail kurzu vidi i deni ve svych lekcich`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val kurz = Uuid.random()
        repo.record(event(seriesId = kurz))                              // zápis na kurz
        repo.record(event(seriesId = kurz, instanceId = Uuid.random()))  // omluvenka z lekce

        val found = repo.findForEvent(kurz, isSeries = true, category = null, page = 0, pageSize = 50)

        assertEquals(2, found.size)
    }

    /**
     * Účastníci jsou zapsaní na kurz, ne na jednotlivou lekci — bez tohohle
     * by detail lekce o nich neukázal vůbec nic.
     */
    @Test
    fun `detail lekce prebira i zaznamy vedene na kurzu`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val kurz = Uuid.random()
        val lekce = Uuid.random()
        repo.record(event(seriesId = kurz))                            // přihláška na kurz
        repo.record(event(seriesId = kurz, instanceId = lekce))        // omluvenka z téhle lekce
        repo.record(event(seriesId = kurz, instanceId = Uuid.random()))// omluvenka z jiné lekce

        val found = repo.findForEvent(
            lekce, isSeries = false, category = null, page = 0, pageSize = 50, parentSeriesId = kurz,
        )

        assertEquals(2, found.size)
    }

    @Test
    fun `filtr kategorie zabira`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lekce = Uuid.random()
        repo.record(event(type = AuditEventType.RESERVATION_CREATED, instanceId = lekce))
        repo.record(event(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, instanceId = lekce))

        val maily = repo.findForEvent(lekce, false, AuditCategory.EMAIL, 0, 50)

        assertEquals(1, maily.size)
        assertEquals(AuditEventType.EMAIL_RESERVATION_CONFIRMATION, maily.single().type)
        assertEquals(2, repo.countForEvent(lekce, false, null))
    }

    @Test
    fun `strankovani vraci od nejnovejsiho`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lekce = Uuid.random()
        repo.record(event(instanceId = lekce, daysAgo = 3))
        repo.record(event(instanceId = lekce, daysAgo = 1))
        repo.record(event(instanceId = lekce, daysAgo = 2))

        val prvni = repo.findForEvent(lekce, false, null, page = 0, pageSize = 2)
        val druha = repo.findForEvent(lekce, false, null, page = 1, pageSize = 2)

        assertEquals(2, prvni.size)
        assertEquals(1, druha.size)
        assertTrue(prvni[0].occurredAt > prvni[1].occurredAt)
        assertTrue(prvni[1].occurredAt > druha[0].occurredAt)
    }

    @Test
    fun `retence smaze starsi nez rok a novejsi nechá`() = runBlocking {
        val repo = InMemoryAuditRepository()
        val lekce = Uuid.random()
        repo.record(event(instanceId = lekce, daysAgo = 400))
        repo.record(event(instanceId = lekce, daysAgo = 300))

        val smazano = repo.deleteOlderThan(Clock.System.now() - 365.days)

        assertEquals(1, smazano)
        assertEquals(1, repo.countAll())
    }
}
