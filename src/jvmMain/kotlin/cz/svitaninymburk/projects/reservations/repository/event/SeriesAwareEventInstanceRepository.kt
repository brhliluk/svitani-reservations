package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Doplní k uložené obsazenosti lekce zátěž kurzu ([SeriesLessonLoad]).
 *
 * `event_instances.occupied_spots` drží jen místa zabraná přímými rezervacemi na
 * tu lekci; účastníci kurzu se do něj nezapisují. Protože `isFull` i
 * `spotsRemaining` jsou computed properties na [EventInstance], stačí opravit
 * `occupiedSpots` tady a celé UI i mobilní API se srovná samo.
 *
 * Zápisová cesta musí odvozenou část zase odečíst: `update()` zapisuje
 * `occupiedSpots` z předaného objektu a volá se stylem
 * `update(instance.copy(...))` nad instancí, která přišla ze čtení. Bez odečtení
 * by se odvozená místa zabetonovala do sloupce a při dalším čtení přičetla podruhé.
 */
class SeriesAwareEventInstanceRepository(
    private val delegate: EventInstanceRepository,
    private val load: SeriesLessonLoad,
    private val guard: SeriesAwareCapacityGuard,
) : EventInstanceRepository {

    private suspend fun enrich(instances: List<EventInstance>): List<EventInstance> {
        if (instances.isEmpty()) return instances
        val loads = load.forInstances(instances)
        return instances.map { instance ->
            val extra = loads[instance.id] ?: 0
            if (extra == 0) instance else instance.copy(occupiedSpots = instance.occupiedSpots + extra)
        }
    }

    private suspend fun enrich(instance: EventInstance?): EventInstance? =
        instance?.let { enrich(listOf(it)).first() }

    /** Opak [enrich] — vrací instanci do podoby, v jaké patří do databáze. */
    private suspend fun strip(instance: EventInstance): EventInstance {
        val extra = load.forInstance(instance)
        return if (extra == 0) instance
        else instance.copy(occupiedSpots = (instance.occupiedSpots - extra).coerceAtLeast(0))
    }

    // --- čtení ---

    override suspend fun get(id: Uuid): EventInstance? = enrich(delegate.get(id))

    override suspend fun getAll(eventIds: List<Uuid>?): List<EventInstance> =
        enrich(delegate.getAll(eventIds))

    override suspend fun getAllPublished(): List<EventInstance> =
        enrich(delegate.getAllPublished())

    override suspend fun getAllByDefinitionIds(definitionIds: List<Uuid>): List<EventInstance> =
        enrich(delegate.getAllByDefinitionIds(definitionIds))

    override suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance> =
        enrich(delegate.findByDateRange(from, to))

    override suspend fun findBySeriesPaged(seriesId: Uuid, page: Int, pageSize: Int): List<EventInstance> =
        enrich(delegate.findBySeriesPaged(seriesId, page, pageSize))

    override suspend fun findBySeries(seriesId: Uuid): List<EventInstance> =
        enrich(delegate.findBySeries(seriesId))

    override suspend fun findScheduledPaged(from: LocalDateTime?, page: Int, pageSize: Int): List<EventInstance> =
        enrich(delegate.findScheduledPaged(from, page, pageSize))

    // --- zápis ---

    override suspend fun create(instance: EventInstance): EventInstance =
        enrich(delegate.create(strip(instance)))!!

    override suspend fun update(instance: EventInstance): EventInstance =
        enrich(delegate.update(strip(instance)))!!

    /**
     * Kapacitu lekce ukrajují i účastníci kurzu. Lekce mimo sérii žádnou zátěž
     * nemá, takže pro ni zůstává původní cesta.
     */
    override suspend fun attemptToReserveSpots(instanceId: Uuid, amount: Int): Boolean {
        val seriesId = delegate.get(instanceId)?.seriesId
            ?: return delegate.attemptToReserveSpots(instanceId, amount)
        return guard.attemptToReserveSpots(instanceId, seriesId, amount)
    }

    // --- beze změny ---

    override suspend fun delete(id: Uuid): Boolean = delegate.delete(id)

    override suspend fun deleteAllByDefinitionId(definitionId: Uuid) =
        delegate.deleteAllByDefinitionId(definitionId)

    override suspend fun countBySeries(seriesId: Uuid): Long = delegate.countBySeries(seriesId)

    override suspend fun countActiveBySeries(seriesId: Uuid): Long = delegate.countActiveBySeries(seriesId)

    override suspend fun countScheduled(from: LocalDateTime?, until: LocalDateTime?): Long =
        delegate.countScheduled(from, until)

    override suspend fun setCancelled(id: Uuid) = delegate.setCancelled(id)

    override suspend fun incrementOccupiedSpots(instanceId: Uuid, amount: Int): Int? =
        delegate.incrementOccupiedSpots(instanceId, amount)

    override suspend fun decrementOccupiedSpots(instanceId: Uuid, amount: Int): Int? =
        delegate.decrementOccupiedSpots(instanceId, amount)

    override suspend fun attemptToReserveWaitlistSpot(instanceId: Uuid): Boolean =
        delegate.attemptToReserveWaitlistSpot(instanceId)

    override suspend fun decrementOccupiedWaitlist(instanceId: Uuid, amount: Int): Int? =
        delegate.decrementOccupiedWaitlist(instanceId, amount)
}
