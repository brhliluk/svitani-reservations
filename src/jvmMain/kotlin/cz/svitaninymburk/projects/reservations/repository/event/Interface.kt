package cz.svitaninymburk.projects.reservations.repository.event

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.LocalDateTime


interface EventDefinitionRepository {
    suspend fun get(id: String): EventDefinition?
    suspend fun getAll(definitionIds: List<String>?): List<EventDefinition>
    suspend fun create(event: EventDefinition): EventDefinition
    suspend fun update(event: EventDefinition): EventDefinition
    suspend fun delete(id: String): Boolean
}

interface EventInstanceRepository {
    suspend fun get(id: String): EventInstance?
    suspend fun getAll(eventIds: List<String>?): List<EventInstance>

    suspend fun create(instance: EventInstance): EventInstance
    suspend fun update(instance: EventInstance): EventInstance
    suspend fun delete(id: String): Boolean
    suspend fun deleteAllByDefinitionId(definitionId: String)

    suspend fun findByDateRange(from: LocalDateTime, to: LocalDateTime): List<EventInstance>

    suspend fun incrementOccupiedSpots(instanceId: String, amount: Int): Int?
    suspend fun decrementOccupiedSpots(instanceId: String, amount: Int): Int?

    suspend fun attemptToReserveSpots(instanceId: String, amount: Int): Boolean
}

interface EventSeriesRepository {
    suspend fun get(id: String): EventSeries?
    suspend fun getAll(seriesIds: List<String>?): List<EventSeries>

    suspend fun create(series: EventSeries): EventSeries
    suspend fun attemptToReserveSpots(seriesId: String, amount: Int): Boolean
    suspend fun incrementOccupiedSpots(seriesId: String, amount: Int): Int?
    suspend fun decrementOccupiedSpots(seriesId: String, amount: Int): Int?
}
