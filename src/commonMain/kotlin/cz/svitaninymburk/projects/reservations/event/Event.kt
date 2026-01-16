package cz.svitaninymburk.projects.reservations.event

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
data class EventDefinition(
    val id: String,
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceEndDate: Instant? = null,
)

@Serializable
data class EventInstance(
    val id: String,
    val definitionId: String,
    val title: String,
    val description: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val price: Double,
    val capacity: Int,
    val occupiedSpots: Int = 0,
    val isCancelled: Boolean = false,
) {
    val isFull: Boolean
        get() = occupiedSpots >= capacity
}

@Serializable
enum class RecurrenceType {
    NONE, DAILY, WEEKLY, MONTHLY
}

@Serializable
data class CreateEventDefinitionRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceEndDate: Instant? = null,
    val customFields: List<CustomFieldDefinition> = emptyList(),
)

@Serializable
data class CreateEventInstanceRequest(
    val definitionId: String,
    val startDateTime: LocalDateTime,
    val title: String? = null,
    val description: String? = null,
    val duration: Duration? = null,
    val price: Double? = null,
    val capacity: Int? = null,
    val customFields: List<CustomFieldDefinition> = emptyList(),
)
