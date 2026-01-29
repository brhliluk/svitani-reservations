package cz.svitaninymburk.projects.reservations.repository.event

import arrow.core.getOrNone
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid


class InMemoryEventDefinitionRepository : EventDefinitionRepository {
    private val events = ConcurrentHashMap<String, EventDefinition>()

    override suspend fun get(id: String): EventDefinition? = events[id]
    override suspend fun getAll(definitionIds: List<String>?): List<EventDefinition> {
        return if (definitionIds == null) events.values.toList()
        else events.filterKeys { it in definitionIds }.values.toList()
    }

    
    override suspend fun create(event: EventDefinition): EventDefinition {
        val id = Uuid.random().toString()
        val newEvent = event.copy(id = id)
        events[id] = newEvent
        return newEvent
    }

    override suspend fun update(event: EventDefinition): EventDefinition {
        events[event.id] = event
        return event
    }

    override suspend fun delete(id: String): Boolean {
        if (events.containsKey(id)) {
            events.remove(id)
            return true
        }
        else return false
    }
}

class InMemoryEventInstanceRepository : EventInstanceRepository {

    private val instances = ConcurrentHashMap<String, EventInstance>()

    override suspend fun get(id: String): EventInstance? {
        return instances[id]
    }

    override suspend fun getAll(eventIds: List<String>?): List<EventInstance> {
        return if (eventIds == null) instances.values.toList()
        else instances.filterKeys { it in eventIds }.values.toList()
    }

    override suspend fun create(instance: EventInstance): EventInstance {
        val id = instance.id.ifBlank { Uuid.random().toString() }
        val newInstance = instance.copy(id = id)
        instances[id] = newInstance
        return newInstance
    }

    override suspend fun update(instance: EventInstance): EventInstance {
        instances[instance.id] = instance
        return instance
    }

    override suspend fun delete(id: String): Boolean {
        if (instances.containsKey(id)) {
            instances.remove(id)
            return true
        }
        return false
    }

    override suspend fun deleteAllByDefinitionId(definitionId: String) {
        instances.values.removeAll { it.definitionId == definitionId }
    }


    override suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance> {
        return instances.values.filter { instance -> instance.startDateTime in from..to }.toList()
    }

    override suspend fun incrementOccupiedSpots(instanceId: String, amount: Int): Int? {
        return instances.computeIfPresent(instanceId) { _, currentInstance ->
            currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots + amount)
        }?.occupiedSpots
    }

    override suspend fun decrementOccupiedSpots(instanceId: String, amount: Int): Int? {
        return instances.computeIfPresent(instanceId) { _, currentInstance ->
            currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots - amount)
        }?.occupiedSpots
    }

    override suspend fun attemptToReserveSpots(instanceId: String, amount: Int): Boolean {
        var reservationSuccess = false

        instances.computeIfPresent(instanceId) { _, currentInstance ->
            if (currentInstance.occupiedSpots + amount <= currentInstance.capacity) {
                reservationSuccess = true
                currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots + amount)
            } else {
                reservationSuccess = false
                currentInstance
            }
        }

        return reservationSuccess
    }
}

class InMemoryEventSeriesRepository : EventSeriesRepository {
    private val instances = ConcurrentHashMap<String, EventSeries>()

    override suspend fun get(id: String): EventSeries? = instances[id]
    override suspend fun getAll(seriesIds: List<String>?): List<EventSeries> {
        return if (seriesIds == null) instances.values.toList()
        else instances.filterKeys { it in seriesIds }.values.toList()
    }

    override suspend fun attemptToReserveSpots(seriesId: String, amount: Int): Boolean {
        var reservationSuccess = false

        instances.computeIfPresent(seriesId) { _, currentInstance ->
            if (currentInstance.occupiedSpots + amount <= currentInstance.capacity) {
                reservationSuccess = true
                currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots + amount)
            } else {
                reservationSuccess = false
                currentInstance
            }
        }

        return reservationSuccess
    }

    override suspend fun incrementOccupiedSpots(seriesId: String, amount: Int): Int? {
        return instances.computeIfPresent(seriesId) { _, currentInstance ->
            currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots + amount)
        }?.occupiedSpots
    }

    override suspend fun decrementOccupiedSpots(seriesId: String, amount: Int): Int? {
        return instances.computeIfPresent(seriesId) { _, currentInstance ->
            currentInstance.copy(occupiedSpots = currentInstance.occupiedSpots - amount)
        }?.occupiedSpots
    }
}