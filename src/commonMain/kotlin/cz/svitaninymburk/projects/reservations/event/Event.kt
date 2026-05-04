package cz.svitaninymburk.projects.reservations.event

import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EventDefinition(
    val id: Uuid,
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
)

@Serializable
data class EventSeries(
    val id: Uuid,
    val definitionId: Uuid,
    val title: String,
    val description: String,
    val price: Double,
    val capacity: Int,
    val occupiedSpots: Int = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
)

@Serializable
data class EventInstance(
    val id: Uuid,
    val definitionId: Uuid,
    val seriesId: Uuid? = null,
    val title: String,
    val description: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val price: Double,
    val capacity: Int,
    val occupiedSpots: Int = 0,
    val isCancelled: Boolean = false,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val isDropIn: Boolean = false,
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
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
)

@Serializable
data class CreateEventAndInstancesRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val dateTimes: List<LocalDateTime>,
)

@Serializable
data class CreateEventAndSeriesRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val customLessons: List<LessonConfig>? = null,
)

@Serializable
data class LessonConfig(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isDropIn: Boolean = false,
)

@Serializable
data class CreateEventSeriesRequest(
    val definitionId: Uuid,
    val title: String,
    val description: String,
    val price: Double,
    val capacity: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
    val customLessons: List<LessonConfig>? = null,
)

@Serializable
data class CreateEventInstanceRequest(
    val definitionId: Uuid,
    val startDateTime: LocalDateTime,
    val title: String? = null,
    val description: String? = null,
    val duration: Duration? = null,
    val price: Double? = null,
    val capacity: Int? = null,
    val allowedPaymentTypes: List<PaymentInfo.Type> = listOf(PaymentInfo.Type.BANK_TRANSFER, PaymentInfo.Type.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
)

@Serializable
data class DashboardData(
    val instances: List<EventInstance>,
    val series: List<EventSeries>,
    val definitions: List<EventDefinition>
)

@Serializable
data class UpdateEventDefinitionRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentInfo.Type>,
    val customFields: List<CustomFieldDefinition>,
    val propagateToChildren: Boolean,
)

@Serializable
data class UpdateEventInstanceRequest(
    val title: String,
    val description: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val price: Double,
    val capacity: Int,
    val allowedPaymentTypes: List<PaymentInfo.Type>,
    val customFields: List<CustomFieldDefinition>,
    val isDropIn: Boolean = false,
)

@Serializable
data class UpdateEventSeriesRequest(
    val title: String,
    val description: String,
    val price: Double,
    val capacity: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val allowedPaymentTypes: List<PaymentInfo.Type>,
    val customFields: List<CustomFieldDefinition>,
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
)
