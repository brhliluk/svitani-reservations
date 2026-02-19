package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid


interface EventDefinitionRepository {
    suspend fun get(id: Uuid): EventDefinition?
    suspend fun getAll(definitionIds: List<Uuid>?): List<EventDefinition>
    suspend fun create(event: EventDefinition): EventDefinition
    suspend fun update(event: EventDefinition): EventDefinition
    suspend fun delete(id: Uuid): Boolean
}

interface EventInstanceRepository {
    suspend fun get(id: Uuid): EventInstance?
    suspend fun getAll(eventIds: List<Uuid>?): List<EventInstance>

    suspend fun create(instance: EventInstance): EventInstance
    suspend fun update(instance: EventInstance): EventInstance
    suspend fun delete(id: Uuid): Boolean
    suspend fun deleteAllByDefinitionId(definitionId: Uuid)

    suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance>

    suspend fun incrementOccupiedSpots(instanceId: Uuid, amount: Int): Int?
    suspend fun decrementOccupiedSpots(instanceId: Uuid, amount: Int): Int?

    suspend fun attemptToReserveSpots(instanceId: Uuid, amount: Int): Boolean
}

interface EventSeriesRepository {
    suspend fun get(id: Uuid): EventSeries?
    suspend fun getAll(seriesIds: List<Uuid>?): List<EventSeries>

    suspend fun create(series: EventSeries): EventSeries
    suspend fun attemptToReserveSpots(seriesId: Uuid, amount: Int): Boolean
    suspend fun incrementOccupiedSpots(seriesId: Uuid, amount: Int): Int?
    suspend fun decrementOccupiedSpots(seriesId: Uuid, amount: Int): Int?
}
