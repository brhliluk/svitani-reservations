package cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase

import cz.svitaninymburk.projects.reservations.event.CreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.UpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

data class EventDefinitionFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val capacity: Int,
    val durationHours: Int,
    val durationMinutes: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val customFields: List<CustomFieldDefinition>,
    val showAttendeeCount: Boolean,
    val allowMultipleSeats: Boolean,
)

sealed interface DefinitionFormValidationError {
    data object MissingTitle : DefinitionFormValidationError
    data object MissingOwnerEmail : DefinitionFormValidationError
}

fun validateDefinitionForm(title: String, ownerEmails: List<String>): DefinitionFormValidationError? {
    if (title.isBlank()) return DefinitionFormValidationError.MissingTitle
    if (parseOwnerEmails(ownerEmails).isEmpty()) return DefinitionFormValidationError.MissingOwnerEmail
    return null
}

fun buildAllowedPaymentTypes(allowBankTransfer: Boolean, allowOnSite: Boolean): List<PaymentInfo.Type> = buildList {
    if (allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
    if (allowOnSite) add(PaymentInfo.Type.ON_SITE)
}

fun buildCreateEventDefinitionRequest(form: EventDefinitionFormData): CreateEventDefinitionRequest =
    CreateEventDefinitionRequest(
        title = form.title,
        description = form.description,
        ownerEmails = parseOwnerEmails(form.ownerEmails),
        defaultPrice = form.price,
        defaultCapacity = form.capacity,
        defaultDuration = form.durationHours.hours + form.durationMinutes.minutes,
        allowedPaymentTypes = buildAllowedPaymentTypes(form.allowBankTransfer, form.allowOnSite),
        customFields = form.customFields,
        showAttendeeCount = form.showAttendeeCount,
        allowMultipleSeats = form.allowMultipleSeats,
    )

fun buildUpdateEventDefinitionRequest(form: EventDefinitionFormData, propagateToChildren: Boolean): UpdateEventDefinitionRequest =
    UpdateEventDefinitionRequest(
        title = form.title,
        description = form.description,
        defaultPrice = form.price,
        defaultCapacity = form.capacity,
        defaultDuration = form.durationHours.hours + form.durationMinutes.minutes,
        allowedPaymentTypes = buildAllowedPaymentTypes(form.allowBankTransfer, form.allowOnSite),
        customFields = form.customFields,
        propagateToChildren = propagateToChildren,
        ownerEmails = parseOwnerEmails(form.ownerEmails),
        showAttendeeCount = form.showAttendeeCount,
        allowMultipleSeats = form.allowMultipleSeats,
    )

// --- UseCase třídy (tenké, vrací Either) ---

class EventDefinitionQueries(private val admin: AdminServiceInterface) {
    suspend fun forEdit(id: Uuid) = admin.getEventDefinitionForEdit(id)
}

class EventDefinitionMutations(private val admin: AdminServiceInterface) {
    suspend fun create(request: CreateEventDefinitionRequest) = admin.createEventDefinition(request)
    suspend fun update(id: Uuid, request: UpdateEventDefinitionRequest) = admin.updateEventDefinition(id, request)
}
