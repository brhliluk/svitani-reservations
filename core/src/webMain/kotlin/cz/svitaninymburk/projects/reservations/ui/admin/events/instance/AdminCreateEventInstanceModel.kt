package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AuthenticatedEventServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineState
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceCreateMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceCreateQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.buildCreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.instanceRecurrencePreviewDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.parseInstanceStartDateTime
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class AdminCreateEventInstanceModel(
    scope: CoroutineScope,
    private val queries: EventInstanceCreateQueries,
    private val mutations: EventInstanceCreateMutations,
    private val router: Router,
    private val currentUserEmail: String,
    private val preselectedDefinitionId: String?,
) : ScreenModel(scope) {

    var isLoadingDefinitions by mutableStateOf(true); private set
    var definitions by mutableStateOf<List<EventDefinition>>(emptyList()); private set
    var selectedDefinitionId by mutableStateOf(preselectedDefinitionId); private set

    var startDate by mutableStateOf(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
    )
    var startTime by mutableStateOf("")
    var titleOverride by mutableStateOf("")
    var descriptionOverride by mutableStateOf("")
    var ownerEmails by mutableStateOf(listOf(currentUserEmail))
    var priceOverride: Number? by mutableStateOf(0)
    var capacityOverride by mutableIntStateOf(10)
    var waitlistCapacityOverride by mutableIntStateOf(10)
    var durationHours by mutableIntStateOf(1)
    var durationMinutes by mutableIntStateOf(0)
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)

    val deadline = ReservationDeadlineState()

    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())
    var recurrenceType by mutableStateOf(RecurrenceType.NONE)
    var recurrenceEndDateStr by mutableStateOf("")

    var isSubmitting by mutableStateOf(false); private set

    val selectedDefinition: EventDefinition?
        get() = definitions.find { it.id.toString() == selectedDefinitionId }

    val isRecurring: Boolean
        get() = recurrenceType != RecurrenceType.NONE && recurrenceEndDateStr.isNotBlank()

    val previewDates: List<LocalDateTime>
        get() = instanceRecurrencePreviewDates(startDate, startTime, recurrenceType, recurrenceEndDateStr)

    fun load() {
        scope.launch {
            queries.definitions()
                .onRight { defs ->
                    definitions = defs
                    isLoadingDefinitions = false
                    if (preselectedDefinitionId != null) {
                        defs.find { it.id.toString() == preselectedDefinitionId }?.let { applyDefinitionDefaults(it) }
                    }
                }
                .onLeft { error ->
                    showToast(currentStrings.toastTemplatesLoadError(error.localizedMessage(currentStrings)), ToastType.Error)
                    isLoadingDefinitions = false
                }
        }
    }

    fun onDefinitionSelected(id: String?) {
        selectedDefinitionId = id
        definitions.find { it.id.toString() == id }?.let { applyDefinitionDefaults(it) }
    }

    fun onRecurrenceTypeChanged(type: RecurrenceType) {
        recurrenceType = type
        if (type == RecurrenceType.NONE) recurrenceEndDateStr = ""
    }

    private fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        ownerEmails = definition.ownerEmails.ifEmpty { listOf(currentUserEmail) }
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity
        definition.defaultDuration.toComponents { hours, minutes, _, _ ->
            durationHours = hours.toInt()
            durationMinutes = minutes
        }
        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentType.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentType.ON_SITE)
        showAttendeeCount = definition.showAttendeeCount
        allowMultipleSeats = definition.allowMultipleSeats
        customFields = definition.customFields
    }

    fun submit(isPublished: Boolean) {
        val definitionId = selectedDefinitionId ?: return
        if (startDate.isBlank() || startTime.isBlank()) {
            showToast(currentStrings.validationDateTimeRequired, ToastType.Error)
            return
        }
        if (parseOwnerEmails(ownerEmails).isEmpty()) {
            showToast(currentStrings.validationOwnerEmailRequired, ToastType.Error)
            return
        }
        val parsedDateTime = parseInstanceStartDateTime(startDate, startTime)
        if (parsedDateTime == null) {
            showToast(currentStrings.validationDateTimeFormat, ToastType.Error)
            return
        }
        val baseRequest = buildCreateEventInstanceRequest(
            form = formData(),
            definitionId = Uuid.parse(definitionId),
            startDateTime = parsedDateTime,
            reservationDeadline = deadline.resolve(parsedDateTime),
            isPublished = isPublished,
        )
        val preview = previewDates
        val dateTimes = if (isRecurring && preview.isNotEmpty()) preview else listOf(parsedDateTime)
        if (dateTimes.isEmpty()) {
            showToast(currentStrings.validationNoDates, ToastType.Error)
            return
        }

        isSubmitting = true
        scope.launch {
            var failed = false
            for (dt in dateTimes) {
                mutations.create(baseRequest.copy(startDateTime = dt))
                    .onLeft { error ->
                        isSubmitting = false
                        showToast(currentStrings.toastInstanceCreateError(dt.toString(), error.localizedMessage(currentStrings)), ToastType.Error)
                        failed = true
                    }
                if (failed) break
            }
            if (!failed) {
                isSubmitting = false
                showToast(
                    if (dateTimes.size > 1) currentStrings.toastInstancesCreated(dateTimes.size) else currentStrings.toastInstanceCreated,
                )
                delay(500.milliseconds)
                router.navigate("/admin/events")
            }
        }
    }

    private fun formData() = EventInstanceCreateFormData(
        title = titleOverride,
        description = descriptionOverride,
        ownerEmails = ownerEmails,
        price = priceOverride?.toDouble() ?: 0.0,
        capacity = capacityOverride,
        waitlistCapacity = waitlistCapacityOverride,
        durationHours = durationHours,
        durationMinutes = durationMinutes,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        showAttendeeCount = showAttendeeCount,
        allowMultipleSeats = allowMultipleSeats,
        customFields = customFields,
        deadlineMessage = deadline.message,
    )
}

fun IComponent.buildAdminCreateEventInstanceModel(
    scope: CoroutineScope,
    router: Router,
    currentUserEmail: String,
    preselectedDefinitionId: String?,
): AdminCreateEventInstanceModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    val authEvent = getService<AuthenticatedEventServiceInterface>(RpcSerializersModules)
    return AdminCreateEventInstanceModel(
        scope = scope,
        queries = EventInstanceCreateQueries(event),
        mutations = EventInstanceCreateMutations(authEvent),
        router = router,
        currentUserEmail = currentUserEmail,
        preselectedDefinitionId = preselectedDefinitionId,
    )
}
