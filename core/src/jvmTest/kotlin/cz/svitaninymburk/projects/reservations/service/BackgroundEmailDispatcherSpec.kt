package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.audit.AuditActorType
import cz.svitaninymburk.projects.reservations.audit.AuditEventType
import cz.svitaninymburk.projects.reservations.repository.audit.InMemoryAuditRepository
import cz.svitaninymburk.projects.reservations.util.AuditSubject
import cz.svitaninymburk.projects.reservations.util.withAuditSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Odesílání mailů se přesunulo mimo požadavek, a tím se ztratil `currentCall()`,
 * ze kterého se dosud bral aktér do historie. Bez přenosu kontextu by se všechny
 * mailové řádky zapsaly jako „systém“ místo admina, který akci spustil.
 */
class BackgroundEmailDispatcherSpec {

    private class Fixture {
        val repository = InMemoryAuditRepository()
        val audit = AuditService(repository)

        /**
         * Schválně obyčejný `Job`, ne `SupervisorJob`: pád potomka takovou scope
         * zruší i s ní. Odchytávání výjimek v dispatcheru je tedy load-bearing —
         * v běhu je tou scope `Application`, tedy celý server.
         */
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val dispatcher = BackgroundEmailDispatcher(scope, audit)

        /** Odložené odeslání běží jinde — bez dojetí by test tvrdil o prázdném logu. */
        suspend fun awaitDispatched() = scope.coroutineContext.job.children.toList().joinAll()
    }

    private val admin = AuditService.Actor(AuditActorType.ADMIN, "brhlikluk@gmail.com")

    @Test
    fun `deferred sending carries the actor from the request`() = runBlocking {
        val f = Fixture()

        withContext(AuditService.ActorContextLocal.asContextElement(admin)) {
            f.dispatcher.dispatch {
                f.audit.record(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, subjectLabel = "Anežka Brhlíková")
            }
        }
        f.awaitDispatched()

        val recorded = f.repository.recordedEvents().single()
        assertEquals(AuditActorType.ADMIN, recorded.actorType)
        assertEquals("brhlikluk@gmail.com", recorded.actorLabel)
    }

    /** Bez subjektu by řádek historie nešlo navěsit na akci a v detailu by chyběl. */
    @Test
    fun `event subject survives the jump out of the request`() = runBlocking {
        val f = Fixture()
        val instanceId = Uuid.random()
        val seriesId = Uuid.random()
        val reservationId = Uuid.random()

        withContext(AuditService.ActorContextLocal.asContextElement(admin)) {
            withAuditSubject(AuditSubject(seriesId, instanceId, reservationId, "Kurz")) {
                f.dispatcher.dispatch {
                    f.audit.record(type = AuditEventType.EMAIL_RESERVATION_CONFIRMATION, subjectLabel = "Anežka Brhlíková")
                }
            }
        }
        f.awaitDispatched()

        val recorded = f.repository.recordedEvents().single()
        assertEquals(seriesId, recorded.seriesId)
        assertEquals(instanceId, recorded.instanceId)
        assertEquals(reservationId, recorded.reservationId)
    }

    /** Mimo request žádný principal není a nic ho nenahrazuje — pak je to systém. */
    @Test
    fun `without an actor in the context it stays system`() = runBlocking {
        val f = Fixture()

        f.dispatcher.dispatch {
            f.audit.record(type = AuditEventType.EMAIL_PAYMENT_RECEIVED, subjectLabel = "Anežka Brhlíková")
        }
        f.awaitDispatched()

        assertEquals(AuditActorType.SYSTEM, f.repository.recordedEvents().single().actorType)
    }

    /**
     * Storno kurzu předá dispatcheru mail za každého účastníka. Kdyby jeden pád
     * shodil scope, zbylí účastníci by se o zrušení nedozvěděli.
     */
    @Test
    fun `failure of one email does not bring down the others`() = runBlocking {
        val f = Fixture()

        f.dispatcher.dispatch { error("SMTP spadl na hubu") }
        f.awaitDispatched()

        f.dispatcher.dispatch {
            f.audit.record(type = AuditEventType.EMAIL_CANCELLATION_NOTICE, subjectLabel = "Druhý účastník")
        }
        f.awaitDispatched()

        assertEquals(listOf("Druhý účastník"), f.repository.recordedEvents().map { it.subjectLabel })
    }
}
