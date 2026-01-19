package cz.svitaninymburk.projects.reservations.event

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
data class EventSeries(
    val id: String,
    val definitionId: String,
    val title: String,
    val description: String,
    val price: Double,
    val capacity: Int,
    val occupiedSpots: Int = 0,

    val startDate: LocalDate,
    val endDate: LocalDate,

    val lessonCount: Int
)

@Serializable
data class EventInstance(
    val id: String,
    val definitionId: String,
    val seriesId: String? = null,
    val title: String,
    val description: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val price: Double,
    val capacity: Int,
    val occupiedSpots: Int = 0,
    val isCancelled: Boolean = false,
) {
    val currentTimeZone get() = TimeZone.currentSystemDefault()
    val isFull: Boolean
        get() = occupiedSpots >= capacity
    val duration: Duration
        get() = endDateTime.toInstant(currentTimeZone) - startDateTime.toInstant(currentTimeZone)
    val isSeries: Boolean
        get() = seriesId != null
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
