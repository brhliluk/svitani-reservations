package cz.svitaninymburk.projects.reservations.repository.audit

import cz.svitaninymburk.projects.reservations.audit.AuditCategory
import cz.svitaninymburk.projects.reservations.audit.AuditEvent
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class InMemoryAuditRepository : AuditRepository {
    private val events = java.util.Collections.synchronizedList(mutableListOf<AuditEvent>())

    override suspend fun record(event: NewAuditEvent) {
        events.add(
            AuditEvent(
                id = Uuid.random(),
                occurredAt = event.occurredAt ?: Clock.System.now(),
                type = event.type,
                seriesId = event.seriesId,
                instanceId = event.instanceId,
                reservationId = event.reservationId,
                walletCode = event.walletCode,
                actorType = event.actorType,
                actorLabel = event.actorLabel,
                subjectLabel = event.subjectLabel,
                outcome = event.outcome,
                recipient = event.recipient,
                amount = event.amount,
                detail = event.detail,
            )
        )
    }

    override suspend fun recordAll(events: List<NewAuditEvent>) = events.forEach { record(it) }

    override suspend fun findById(id: Uuid): AuditEvent? = synchronized(events) { events.find { it.id == id } }

    override suspend fun findForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        page: Int,
        pageSize: Int,
        parentSeriesId: Uuid?,
    ): List<AuditEvent> = matching(eventId, isSeries, category, parentSeriesId)
        .sortedByDescending { it.occurredAt }
        .drop(page * pageSize)
        .take(pageSize)

    override suspend fun countForEvent(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        parentSeriesId: Uuid?,
    ): Long = matching(eventId, isSeries, category, parentSeriesId).size.toLong()

    override suspend fun findForReservations(
        subjectId: Uuid,
        reservationIds: Collection<Uuid>,
        category: AuditCategory?,
        page: Int,
        pageSize: Int,
    ): List<AuditEvent> = matchingReservations(subjectId, reservationIds, category)
        .sortedByDescending { it.occurredAt }
        .drop(page * pageSize)
        .take(pageSize)

    override suspend fun countForReservations(
        subjectId: Uuid,
        reservationIds: Collection<Uuid>,
        category: AuditCategory?,
    ): Long = matchingReservations(subjectId, reservationIds, category).size.toLong()

    private fun matchingReservations(
        subjectId: Uuid,
        reservationIds: Collection<Uuid>,
        category: AuditCategory?,
    ): List<AuditEvent> = synchronized(events) {
        val ids = reservationIds.toSet()
        events.filter { event ->
            (event.instanceId == subjectId || event.reservationId in ids) &&
                (category == null || event.category == category)
        }
    }

    override suspend fun countAll(): Long = synchronized(events) { events.size.toLong() }

    override suspend fun deleteOlderThan(cutoff: Instant): Int = synchronized(events) {
        val old = events.filter { it.occurredAt < cutoff }
        events.removeAll(old)
        old.size
    }

    private fun matching(
        eventId: Uuid,
        isSeries: Boolean,
        category: AuditCategory?,
        parentSeriesId: Uuid?,
    ): List<AuditEvent> = synchronized(events) {
        events.filter { event ->
            val subjectMatches = when {
                isSeries -> event.seriesId == eventId
                parentSeriesId != null ->
                    event.instanceId == eventId || (event.seriesId == parentSeriesId && event.instanceId == null)
                else -> event.instanceId == eventId
            }
            subjectMatches && (category == null || event.category == category)
        }
    }

    fun recordedEvents(): List<AuditEvent> = synchronized(events) { events.toList() }
}
