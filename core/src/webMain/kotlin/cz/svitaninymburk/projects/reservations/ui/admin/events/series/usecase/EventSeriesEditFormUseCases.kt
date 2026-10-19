package cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.UpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlin.time.Duration
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

data class EventSeriesEditFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val lessonPrice: Double?,
    val capacity: Int,
    val waitlistCapacity: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val showAttendeeCount: Boolean,
    val allowMultipleSeats: Boolean,
    val lessonRefundAmount: Double?,
    val customFields: List<CustomFieldDefinition>,
    val deadlineMessage: String,
)

sealed interface SeriesFormValidationError {
    data object MissingTitle : SeriesFormValidationError
    data object MissingOwnerEmail : SeriesFormValidationError
}

fun validateSeriesForm(title: String, ownerEmails: List<String>): SeriesFormValidationError? {
    if (title.isBlank()) return SeriesFormValidationError.MissingTitle
    if (parseOwnerEmails(ownerEmails).isEmpty()) return SeriesFormValidationError.MissingOwnerEmail
    return null
}

fun buildUpdateEventSeriesRequest(
    form: EventSeriesEditFormData,
    reservationDeadline: Duration?,
): UpdateEventSeriesRequest = UpdateEventSeriesRequest(
    title = form.title,
    description = form.description,
    price = form.price,
    capacity = form.capacity,
    waitlistCapacity = form.waitlistCapacity,
    allowedPaymentTypes = buildList {
        if (form.allowBankTransfer) add(PaymentType.BANK_TRANSFER)
        if (form.allowOnSite) add(PaymentType.ON_SITE)
    },
    customFields = form.customFields,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    showAttendeeCount = form.showAttendeeCount,
    allowMultipleSeats = form.allowMultipleSeats,
    lessonPrice = form.lessonPrice?.takeIf { it > 0 },
    lessonRefundAmount = form.lessonRefundAmount?.takeIf { it >= 0 },
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
)

// --- UseCase třídy (tenké, vrací Either) ---

class EventSeriesEditQueries(private val admin: AdminServiceInterface) {
    suspend fun forEdit(id: Uuid) = admin.getEventSeriesForEdit(id)
}

class EventSeriesEditMutations(private val admin: AdminServiceInterface) {
    suspend fun update(id: Uuid, request: UpdateEventSeriesRequest) = admin.updateEventSeries(id, request)
    suspend fun setPublished(id: Uuid, published: Boolean) = admin.setSeriesPublished(id, published)
}
