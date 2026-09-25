package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.EditGuard
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineState
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceEditMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceEditQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.InstanceFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.buildUpdateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.parseInstanceStartDateTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.validateInstanceForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import web.history.history
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

sealed interface EditInstanceUiState {
    data object Loading : EditInstanceUiState
    data class Loaded(val instance: EventInstance) : EditInstanceUiState
    data class Error(val message: String) : EditInstanceUiState
}

class AdminEditEventInstanceModel(
    scope: CoroutineScope,
    private val queries: EventInstanceEditQueries,
    private val mutations: EventInstanceEditMutations,
    private val id: String,
) : ScreenModel(scope) {

    var uiState: EditInstanceUiState by mutableStateOf(EditInstanceUiState.Loading); private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var ownerEmails by mutableStateOf(listOf(""))
    var startDate by mutableStateOf("")
    var startTime by mutableStateOf("")
    var durationHours by mutableIntStateOf(1)
    var durationMinutes by mutableIntStateOf(0)
    var price: Number? by mutableStateOf(0)
    var capacity by mutableIntStateOf(10)
    var waitlistCapacity by mutableIntStateOf(0)
    var occupiedSpots by mutableIntStateOf(0); private set
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var isDropIn by mutableStateOf(false)
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())

    val deadline = ReservationDeadlineState()

    var isSubmitting by mutableStateOf(false); private set
    val guard = EditGuard(
        occupiedSpots = { occupiedSpots },
        capacity = { capacity },
        isPublished = { (uiState as? EditInstanceUiState.Loaded)?.instance?.isPublished },
        save = ::performSave,
        setPublished = ::setPublished,
    )

    fun load() {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull()
            ?: run { uiState = EditInstanceUiState.Error(currentStrings.invalidEventId); return }
        scope.launch {
            queries.forEdit(uuid)
                .onRight { inst ->
                    title = inst.title
                    description = inst.description
                    startDate = inst.startDateTime.date.toString()
                    startTime = "${inst.startDateTime.hour.toString().padStart(2, '0')}:${inst.startDateTime.minute.toString().padStart(2, '0')}"
                    val dur = inst.duration
                    durationHours = dur.inWholeHours.toInt()
                    durationMinutes = (dur.inWholeMinutes % 60).toInt()
                    price = inst.price
                    capacity = inst.capacity
                    waitlistCapacity = inst.waitlistCapacity
                    occupiedSpots = inst.occupiedSpots
                    allowBankTransfer = inst.allowedPaymentTypes.contains(PaymentType.BANK_TRANSFER)
                    allowOnSite = inst.allowedPaymentTypes.contains(PaymentType.ON_SITE)
                    isDropIn = inst.isDropIn
                    ownerEmails = inst.ownerEmails.ifEmpty { listOf("") }
                    showAttendeeCount = inst.showAttendeeCount
                    allowMultipleSeats = inst.allowMultipleSeats
                    deadline.restore(inst.reservationDeadline, inst.reservationDeadlineMessage)
                    customFields = inst.customFields
                    uiState = EditInstanceUiState.Loaded(inst)
                }
                .onLeft { uiState = EditInstanceUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun submit() {
        val validationError = validateInstanceForm(title, ownerEmails)
        if (validationError != null) {
            showToast(validationErrorMessage(validationError), ToastType.Error)
            return
        }
        guard.requestSave()
    }

    private fun performSave() {
        val startDt = parseInstanceStartDateTime(startDate, startTime)
        if (startDt == null) {
            showToast(currentStrings.validationDateTimeFormat, ToastType.Error)
            return
        }
        val tz = TimeZone.currentSystemDefault()
        val endDt = (startDt.toInstant(tz) + durationHours.hours + durationMinutes.minutes).toLocalDateTime(tz)
        val request = buildUpdateEventInstanceRequest(formData(), startDt, endDt, deadline.resolve(startDt))
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.update(Uuid.parse(id), request) },
            onSuccess = {
                showToast(currentStrings.toastEventUpdated)
                scope.launch { delay(500); history.back() }
            },
        )
    }

    private fun setPublished(published: Boolean) {
        val loaded = uiState as? EditInstanceUiState.Loaded ?: return
        val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.setPublished(uuid, published) },
            onSuccess = {
                showToast(if (published) currentStrings.toastPublished else currentStrings.toastHidden)
                uiState = EditInstanceUiState.Loaded(loaded.instance.copy(isPublished = published))
            },
        )
    }

    private fun formData() = EventInstanceEditFormData(
        title = title,
        description = description,
        ownerEmails = ownerEmails,
        price = price?.toDouble() ?: 0.0,
        capacity = capacity,
        waitlistCapacity = waitlistCapacity,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        isDropIn = isDropIn,
        showAttendeeCount = showAttendeeCount,
        allowMultipleSeats = allowMultipleSeats,
        customFields = customFields,
        deadlineMessage = deadline.message,
    )

    private fun validationErrorMessage(error: InstanceFormValidationError): String = when (error) {
        InstanceFormValidationError.MissingTitle -> currentStrings.validationNameRequired
        InstanceFormValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
    }
}

fun IComponent.buildAdminEditEventInstanceModel(
    scope: CoroutineScope,
    id: String,
): AdminEditEventInstanceModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminEditEventInstanceModel(
        scope = scope,
        queries = EventInstanceEditQueries(admin),
        mutations = EventInstanceEditMutations(admin),
        id = id,
    )
}
