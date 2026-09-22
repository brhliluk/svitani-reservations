package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildUpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.resolveReservationDeadline
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.validateSeriesForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import web.history.history

sealed interface EditSeriesUiState {
    data object Loading : EditSeriesUiState
    data class Loaded(val series: EventSeries) : EditSeriesUiState
    data class Error(val message: String) : EditSeriesUiState
}

class AdminEditEventSeriesModel(
    scope: CoroutineScope,
    private val queries: EventSeriesEditQueries,
    private val mutations: EventSeriesEditMutations,
    private val id: String,
) : ScreenModel(scope) {

    var uiState: EditSeriesUiState by mutableStateOf(EditSeriesUiState.Loading); private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var price: Number? by mutableStateOf(0)
    var capacity by mutableIntStateOf(10)
    var waitlistCapacity by mutableIntStateOf(0)
    var occupiedSpots by mutableIntStateOf(0); private set
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var ownerEmails by mutableStateOf(listOf(""))
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)
    var lessonPriceInput: Number? by mutableStateOf(null)
    var lessonRefundAmountInput: Number? by mutableStateOf(null)
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())

    var deadlineEnabled by mutableStateOf(false)
    var deadlineTypeIsHours by mutableStateOf(true)
    var deadlineHours by mutableIntStateOf(2)
    var deadlineDaysBefore by mutableIntStateOf(1)
    var deadlineTimeStr by mutableStateOf("18:00")
    var deadlineMessage by mutableStateOf("")

    var isSubmitting by mutableStateOf(false); private set
    var showCapacityWarning by mutableStateOf(false); private set
    var showHideConfirm by mutableStateOf(false); private set

    fun load() {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull()
            ?: run { uiState = EditSeriesUiState.Error(currentStrings.invalidEventId); return }
        scope.launch {
            queries.forEdit(uuid)
                .onRight { s ->
                    title = s.title
                    description = s.description
                    price = s.price
                    capacity = s.capacity
                    waitlistCapacity = s.waitlistCapacity
                    occupiedSpots = s.occupiedSpots
                    allowBankTransfer = s.allowedPaymentTypes.contains(PaymentType.BANK_TRANSFER)
                    allowOnSite = s.allowedPaymentTypes.contains(PaymentType.ON_SITE)
                    ownerEmails = s.ownerEmails.ifEmpty { listOf("") }
                    showAttendeeCount = s.showAttendeeCount
                    allowMultipleSeats = s.allowMultipleSeats
                    lessonPriceInput = s.lessonPrice
                    lessonRefundAmountInput = s.lessonRefundAmount
                    val seriesDeadline = s.reservationDeadline
                    if (seriesDeadline != null) {
                        deadlineEnabled = true
                        deadlineHours = seriesDeadline.inWholeHours.toInt()
                        deadlineTypeIsHours = true
                    }
                    deadlineMessage = s.reservationDeadlineMessage ?: ""
                    customFields = s.customFields
                    uiState = EditSeriesUiState.Loaded(s)
                }
                .onLeft { uiState = EditSeriesUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun submit() {
        val validationError = validateSeriesForm(title, ownerEmails)
        if (validationError != null) {
            showToast(validationErrorMessage(validationError), ToastType.Error)
            return
        }
        if (capacity < occupiedSpots) {
            showCapacityWarning = true
            return
        }
        performSave()
    }

    fun confirmCapacityWarning() {
        showCapacityWarning = false
        performSave()
    }

    fun dismissCapacityWarning() {
        showCapacityWarning = false
    }

    fun requestTogglePublished() {
        val loaded = uiState as? EditSeriesUiState.Loaded ?: return
        val isPublished = loaded.series.isPublished
        if (isPublished && occupiedSpots > 0) {
            showHideConfirm = true
            return
        }
        setPublished(!isPublished)
    }

    fun confirmHide() {
        showHideConfirm = false
        setPublished(false)
    }

    fun dismissHideConfirm() {
        showHideConfirm = false
    }

    private fun performSave() {
        val loaded = uiState as? EditSeriesUiState.Loaded ?: return
        val deadline = resolveReservationDeadline(
            loaded.series.startDate,
            loaded.series.lessonStartTime,
            deadlineEnabled, deadlineTypeIsHours, deadlineHours, deadlineDaysBefore, deadlineTimeStr,
        )
        val request = buildUpdateEventSeriesRequest(formData(), deadline)
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.update(Uuid.parse(id), request) },
            onSuccess = {
                showToast(currentStrings.toastSeriesUpdated)
                scope.launch { delay(500); history.back() }
            },
        )
    }

    private fun setPublished(published: Boolean) {
        val loaded = uiState as? EditSeriesUiState.Loaded ?: return
        val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return
        scope.launch {
            mutations.setPublished(uuid, published)
                .onRight {
                    showToast(if (published) currentStrings.toastPublished else currentStrings.toastHidden)
                    uiState = EditSeriesUiState.Loaded(loaded.series.copy(isPublished = published))
                }
                .onLeft { showToast(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
        }
    }

    private fun formData() = EventSeriesEditFormData(
        title = title,
        description = description,
        ownerEmails = ownerEmails,
        price = price?.toDouble() ?: 0.0,
        lessonPrice = lessonPriceInput?.toDouble(),
        capacity = capacity,
        waitlistCapacity = waitlistCapacity,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        showAttendeeCount = showAttendeeCount,
        allowMultipleSeats = allowMultipleSeats,
        lessonRefundAmount = lessonRefundAmountInput?.toDouble(),
        customFields = customFields,
        deadlineMessage = deadlineMessage,
    )

    private fun validationErrorMessage(error: SeriesFormValidationError): String = when (error) {
        SeriesFormValidationError.MissingTitle -> currentStrings.validationSeriesTitleRequired
        SeriesFormValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
    }
}

fun IComponent.buildAdminEditEventSeriesModel(
    scope: CoroutineScope,
    id: String,
): AdminEditEventSeriesModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminEditEventSeriesModel(
        scope = scope,
        queries = EventSeriesEditQueries(admin),
        mutations = EventSeriesEditMutations(admin),
        id = id,
    )
}
