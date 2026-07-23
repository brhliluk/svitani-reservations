package cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase

import cz.svitaninymburk.projects.reservations.event.CreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.event.generateRecurrenceDates
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AuthenticatedEventServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

data class EventInstanceCreateFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val capacity: Int,
    val waitlistCapacity: Int,
    val durationHours: Int,
    val durationMinutes: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val showAttendeeCount: Boolean,
    val customFields: List<CustomFieldDefinition>,
    val deadlineMessage: String,
)

/** Náhled dat opakování; prázdné, pokud se neopakuje nebo vstup nejde naparsovat. */
fun instanceRecurrencePreviewDates(
    startDate: String,
    startTime: String,
    recurrenceType: RecurrenceType,
    recurrenceEndDateStr: String,
): List<LocalDateTime> {
    val isRecurring = recurrenceType != RecurrenceType.NONE && recurrenceEndDateStr.isNotBlank()
    if (!isRecurring || startDate.isBlank() || startTime.isBlank()) return emptyList()
    val d = try { LocalDate.parse(startDate) } catch (_: Exception) { return emptyList() }
    val t = try { LocalTime.parse(startTime) } catch (_: Exception) { return emptyList() }
    val endInstant = try {
        LocalDate.parse(recurrenceEndDateStr).atStartOfDayIn(TimeZone.currentSystemDefault())
    } catch (_: Exception) { return emptyList() }
    return generateRecurrenceDates(d, t, recurrenceType, endInstant)
}

fun buildCreateEventInstanceRequest(
    form: EventInstanceCreateFormData,
    definitionId: Uuid,
    startDateTime: LocalDateTime,
    reservationDeadline: Duration?,
    isPublished: Boolean,
): CreateEventInstanceRequest = CreateEventInstanceRequest(
    definitionId = definitionId,
    startDateTime = startDateTime,
    title = form.title.takeIf { it.isNotBlank() },
    description = form.description.takeIf { it.isNotBlank() },
    duration = form.durationHours.hours + form.durationMinutes.minutes,
    price = form.price,
    capacity = form.capacity,
    waitlistCapacity = form.waitlistCapacity,
    allowedPaymentTypes = buildList {
        if (form.allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
        if (form.allowOnSite) add(PaymentInfo.Type.ON_SITE)
    },
    customFields = form.customFields,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    showAttendeeCount = form.showAttendeeCount,
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
    isPublished = isPublished,
)

// --- UseCase třídy (tenké, vrací Either) ---

class EventInstanceCreateQueries(private val event: EventServiceInterface) {
    suspend fun definitions() = event.getAllDefinitions()
}

class EventInstanceCreateMutations(private val authEvent: AuthenticatedEventServiceInterface) {
    suspend fun create(request: CreateEventInstanceRequest) = authEvent.createEventInstance(request)
}
