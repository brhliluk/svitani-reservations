package cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.UpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

data class EventInstanceEditFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val capacity: Int,
    val waitlistCapacity: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val isDropIn: Boolean,
    val showAttendeeCount: Boolean,
    val allowMultipleSeats: Boolean,
    val customFields: List<CustomFieldDefinition>,
    val deadlineMessage: String,
)

sealed interface InstanceFormValidationError {
    data object MissingTitle : InstanceFormValidationError
    data object MissingOwnerEmail : InstanceFormValidationError
}

fun validateInstanceForm(title: String, ownerEmails: List<String>): InstanceFormValidationError? {
    if (title.isBlank()) return InstanceFormValidationError.MissingTitle
    if (parseOwnerEmails(ownerEmails).isEmpty()) return InstanceFormValidationError.MissingOwnerEmail
    return null
}

fun parseInstanceStartDateTime(dateStr: String, timeStr: String): LocalDateTime? {
    val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { return null }
    val parts = timeStr.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return try { LocalDateTime(date, LocalTime(hour, minute)) } catch (_: Exception) { null }
}

fun buildUpdateEventInstanceRequest(
    form: EventInstanceEditFormData,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    reservationDeadline: Duration?,
): UpdateEventInstanceRequest = UpdateEventInstanceRequest(
    title = form.title,
    description = form.description,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    price = form.price,
    capacity = form.capacity,
    waitlistCapacity = form.waitlistCapacity,
    allowedPaymentTypes = buildList {
        if (form.allowBankTransfer) add(PaymentType.BANK_TRANSFER)
        if (form.allowOnSite) add(PaymentType.ON_SITE)
    },
    customFields = form.customFields,
    isDropIn = form.isDropIn,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    showAttendeeCount = form.showAttendeeCount,
    allowMultipleSeats = form.allowMultipleSeats,
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
)

// --- UseCase třídy (tenké, vrací Either) ---

class EventInstanceEditQueries(private val admin: AdminServiceInterface) {
    suspend fun forEdit(id: Uuid) = admin.getEventInstanceForEdit(id)
}

class EventInstanceEditMutations(private val admin: AdminServiceInterface) {
    suspend fun update(id: Uuid, request: UpdateEventInstanceRequest) = admin.updateEventInstance(id, request)
    suspend fun setPublished(id: Uuid, published: Boolean) = admin.setInstancePublished(id, published)
}
