package cz.svitaninymburk.projects.reservations.event

import cz.svitaninymburk.projects.reservations.reservation.PaymentType
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
    val defaultWaitlistCapacity: Int = 10,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val showAttendeeCount: Boolean = true,
    /** Výchozí nastavení pro nové kurzy a akce; viz [EventInstance.allowMultipleSeats]. */
    val allowMultipleSeats: Boolean = true,
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
    val waitlistCapacity: Int = 10,
    val occupiedWaitlist: Int = 0,
    val isPublished: Boolean = false,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
    val showAttendeeCount: Boolean = true,
    /** Když je false, rezervační formulář skryje pole s počtem míst a rezervuje se vždy 1 místo. */
    val allowMultipleSeats: Boolean = true,
    /** Cena jedné lekce; null = lekce se prodávají za cenu celého kurzu ([price]). */
    val lessonPrice: Double? = null,
    val lessonRefundAmount: Double? = null,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    val isCancelled: Boolean = false,
    /** Viz [EventInstance.reservationClosesAt]. */
    val reservationClosesAt: Instant? = null,
) {
    val isFull: Boolean get() = occupiedSpots >= capacity
    val hasWaitlist: Boolean get() = waitlistCapacity > 0
    val isWaitlistFull: Boolean get() = occupiedWaitlist >= waitlistCapacity

    /** Viz [EventInstance.isReservationClosed]. */
    fun isReservationClosed(now: Instant): Boolean = reservationClosesAt?.let { now >= it } ?: false
}

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
    val waitlistCapacity: Int = 10,
    val occupiedWaitlist: Int = 0,
    val isCancelled: Boolean = false,
    val isPublished: Boolean = false,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val isDropIn: Boolean = false,
    val showAttendeeCount: Boolean = true,
    /** Když je false, rezervační formulář skryje pole s počtem míst a rezervuje se vždy 1 místo. */
    val allowMultipleSeats: Boolean = true,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    /**
     * Kdy se zavírá rezervace ([reservationDeadline] před začátkem), spočítané
     * serverem v provozní zóně; null = bez uzávěrky, nebo objekt z úložiště, kterému
     * ji server ještě nedopočítal. Prohlížeč nemá databázi časových pásem, takže si
     * uzávěrku sám spočítat nemůže — stejně jako u uzávěrky storna ji dostává hotovou.
     * Server se na tohle pole při rezervaci nespoléhá a počítá si ji znovu.
     */
    val reservationClosesAt: Instant? = null,
) {
    val currentTimeZone get() = TimeZone.currentSystemDefault()
    val isFull: Boolean
        get() = occupiedSpots >= capacity
    val hasWaitlist: Boolean get() = waitlistCapacity > 0
    val isWaitlistFull: Boolean get() = occupiedWaitlist >= waitlistCapacity
    val duration: Duration
        get() = endDateTime.toInstant(currentTimeZone) - startDateTime.toInstant(currentTimeZone)
    val isSeries: Boolean
        get() = seriesId != null

    /** Uplynula uzávěrka rezervace? Jen porovnání s hotovým [reservationClosesAt], bez časové zóny. */
    fun isReservationClosed(now: Instant): Boolean = reservationClosesAt?.let { now >= it } ?: false
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
    val defaultWaitlistCapacity: Int = 10,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
)

@Serializable
data class CreateEventAndInstancesRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultWaitlistCapacity: Int = 10,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val dateTimes: List<LocalDateTime>,
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    val isPublished: Boolean = false,
)

@Serializable
data class CreateEventAndSeriesRequest(
    val title: String,
    val description: String,
    val defaultPrice: Double,
    val defaultCapacity: Int,
    val defaultWaitlistCapacity: Int = 10,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    /**
     * Den a čas lekcí pro týdenní rozpis, když formulář nepošle [customLessons].
     * Stejná pole a stejné pravidlo jako u [CreateEventSeriesRequest].
     */
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
    val customLessons: List<LessonConfig>? = null,
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    /** Cena jedné lekce; null = lekce se zakládají za cenu celého kurzu ([defaultPrice]). */
    val lessonPrice: Double? = null,
    val lessonRefundAmount: Double? = null,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    val isPublished: Boolean = false,
)

@Serializable
data class LessonConfig(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isDropIn: Boolean = false,
)

@Serializable
data class AddSeriesLessonRequest(
    val seriesId: Uuid,
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
    val waitlistCapacity: Int = 10,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lessonCount: Int,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val lessonDayOfWeek: DayOfWeek? = null,
    val lessonStartTime: LocalTime? = null,
    val lessonEndTime: LocalTime? = null,
    val customLessons: List<LessonConfig>? = null,
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    /** Cena jedné lekce; null = lekce se zakládají za cenu celého kurzu ([price]). */
    val lessonPrice: Double? = null,
    val lessonRefundAmount: Double? = null,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    val isPublished: Boolean = false,
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
    val waitlistCapacity: Int = 10,
    val allowedPaymentTypes: List<PaymentType> = listOf(PaymentType.BANK_TRANSFER, PaymentType.ON_SITE),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val ownerEmails: List<String> = emptyList(),
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
    val isPublished: Boolean = false,
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
    val defaultWaitlistCapacity: Int = 10,
    val defaultDuration: Duration,
    val allowedPaymentTypes: List<PaymentType>,
    val customFields: List<CustomFieldDefinition>,
    val ownerEmails: List<String> = emptyList(),
    val propagateToChildren: Boolean,
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
)

@Serializable
data class UpdateEventInstanceRequest(
    val title: String,
    val description: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val price: Double,
    val capacity: Int,
    val waitlistCapacity: Int = 10,
    val allowedPaymentTypes: List<PaymentType>,
    val customFields: List<CustomFieldDefinition>,
    val ownerEmails: List<String> = emptyList(),
    val isDropIn: Boolean = false,
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
)

@Serializable
data class UpdateEventSeriesRequest(
    val title: String,
    val description: String,
    val price: Double,
    val capacity: Int,
    val waitlistCapacity: Int = 10,
    val allowedPaymentTypes: List<PaymentType>,
    val customFields: List<CustomFieldDefinition>,
    val ownerEmails: List<String> = emptyList(),
    val showAttendeeCount: Boolean = true,
    val allowMultipleSeats: Boolean = true,
    /** Cena jedné lekce; null = lekce se prodávají za cenu celého kurzu ([price]). */
    val lessonPrice: Double? = null,
    val lessonRefundAmount: Double? = null,
    val reservationDeadline: Duration? = null,
    val reservationDeadlineMessage: String? = null,
)
