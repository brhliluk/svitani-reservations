package cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase

import cz.svitaninymburk.projects.reservations.event.CreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.event.CreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.LessonConfig
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// --- Pure helpery (testovatelné bez RPC) ---

/** Co zakládáme: jednorázovou akci, opakovanou akci, nebo kurz (sérii lekcí). */
enum class EventCreateType { SINGLE, RECURRING, COURSE }

/** Pole společná všem třem typům; co je jen pro jeden typ, chodí do builderu zvlášť. */
data class EventCreateFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val capacity: Int,
    val durationHours: Int,
    val durationMinutes: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val showAttendeeCount: Boolean,
    val allowMultipleSeats: Boolean,
    val customFields: List<CustomFieldDefinition>,
    val deadlineMessage: String,
) {
    val duration: Duration get() = durationHours.hours + durationMinutes.minutes
    val totalDurationMinutes: Int get() = durationHours * 60 + durationMinutes
    val allowedPaymentTypes: List<PaymentType>
        get() = buildList {
            if (allowBankTransfer) add(PaymentType.BANK_TRANSFER)
            if (allowOnSite) add(PaymentType.ON_SITE)
        }
}

enum class EventCreateValidationError {
    MissingTitle,
    MissingOwnerEmail,
    MissingDateOrTime,
    DateTimeFormat,
    NoRecurrenceDates,
    MissingCourseStartDate,
    CourseStartDateFormat,
    AllLessonsExcluded,
}

/** Kontrola polí, která musí sedět bez ohledu na typ akce; null = v pořádku. */
fun validateEventCreateCommon(title: String, ownerEmails: List<String>): EventCreateValidationError? {
    if (title.isBlank()) return EventCreateValidationError.MissingTitle
    if (parseOwnerEmails(ownerEmails).isEmpty()) return EventCreateValidationError.MissingOwnerEmail
    return null
}

sealed interface SingleEventDateTime {
    data class Valid(val dateTime: LocalDateTime) : SingleEventDateTime
    data class Invalid(val error: EventCreateValidationError) : SingleEventDateTime
}

/** Datum a čas jednorázové akce; prázdné pole a nečitelný formát hlásíme zvlášť. */
fun parseSingleEventDateTime(startDate: String, startTime: String): SingleEventDateTime {
    if (startDate.isBlank() || startTime.isBlank()) {
        return SingleEventDateTime.Invalid(EventCreateValidationError.MissingDateOrTime)
    }
    return try {
        SingleEventDateTime.Valid(LocalDateTime.parse("${startDate}T${startTime}"))
    } catch (_: Exception) {
        SingleEventDateTime.Invalid(EventCreateValidationError.DateTimeFormat)
    }
}

sealed interface CourseStartDate {
    data class Valid(val date: LocalDate) : CourseStartDate
    data class Invalid(val error: EventCreateValidationError) : CourseStartDate
}

fun parseCourseStartDate(courseStartDate: String): CourseStartDate {
    if (courseStartDate.isBlank()) return CourseStartDate.Invalid(EventCreateValidationError.MissingCourseStartDate)
    return try {
        CourseStartDate.Valid(LocalDate.parse(courseStartDate))
    } catch (_: Exception) {
        CourseStartDate.Invalid(EventCreateValidationError.CourseStartDateFormat)
    }
}

/**
 * Kurz bez jediné lekce nemá smysl zakládat — ale jen když nějaké termíny
 * vznikly a uživatel je všechny vyřadil. Dokud není vybraný den lekce,
 * `generated` je prázdné a kurz se založí bez rozpisu.
 */
fun allLessonsExcluded(generated: List<LocalDate>, effective: List<LocalDate>): Boolean =
    generated.isNotEmpty() && effective.isEmpty()

fun buildCreateEventAndInstancesRequest(
    form: EventCreateFormData,
    dateTimes: List<LocalDateTime>,
    reservationDeadline: Duration?,
    isPublished: Boolean,
): CreateEventAndInstancesRequest = CreateEventAndInstancesRequest(
    title = form.title,
    description = form.description,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    defaultPrice = form.price,
    defaultCapacity = form.capacity,
    defaultDuration = form.duration,
    allowedPaymentTypes = form.allowedPaymentTypes,
    customFields = form.customFields,
    showAttendeeCount = form.showAttendeeCount,
    allowMultipleSeats = form.allowMultipleSeats,
    dateTimes = dateTimes,
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
    isPublished = isPublished,
)

fun buildCreateEventAndSeriesRequest(
    form: EventCreateFormData,
    startDate: LocalDate,
    endDate: LocalDate,
    lessonCount: Int,
    customLessons: List<LessonConfig>?,
    lessonPrice: Double?,
    reservationDeadline: Duration?,
    isPublished: Boolean,
): CreateEventAndSeriesRequest = CreateEventAndSeriesRequest(
    title = form.title,
    description = form.description,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    defaultPrice = form.price,
    defaultCapacity = form.capacity,
    defaultDuration = form.duration,
    allowedPaymentTypes = form.allowedPaymentTypes,
    customFields = form.customFields,
    startDate = startDate,
    endDate = endDate,
    lessonCount = lessonCount,
    customLessons = customLessons,
    showAttendeeCount = form.showAttendeeCount,
    allowMultipleSeats = form.allowMultipleSeats,
    lessonPrice = lessonPrice?.takeIf { it > 0 },
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
    isPublished = isPublished,
)

// --- UseCase třídy (tenké, vrací Either) ---

class EventCreateMutations(private val admin: AdminServiceInterface) {
    suspend fun createWithInstances(request: CreateEventAndInstancesRequest) = admin.createEventAndInstances(request)
    suspend fun createWithSeries(request: CreateEventAndSeriesRequest) = admin.createEventAndSeries(request)
}
