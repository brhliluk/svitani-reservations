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
import cz.svitaninymburk.projects.reservations.ui.admin.events.EditGuard
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineState
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildUpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.validateSeriesForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import web.history.history
import kotlin.uuid.Uuid

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
    /** Jen pro náhled poměrného kreditu — počet lekcí se v tomhle formuláři neupravuje. */
    var lessonCount by mutableIntStateOf(0); private set
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())

    val deadline = ReservationDeadlineState()

    var isSubmitting by mutableStateOf(false); private set
    val guard = EditGuard(
        occupiedSpots = { occupiedSpots },
        capacity = { capacity },
        isPublished = { (uiState as? EditSeriesUiState.Loaded)?.series?.isPublished },
        save = ::performSave,
        setPublished = ::setPublished,
    )

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
                    lessonCount = s.lessonCount
                    deadline.restore(s.reservationDeadline, s.reservationDeadlineMessage)
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
        guard.requestSave()
    }

    private fun performSave() {
        val loaded = uiState as? EditSeriesUiState.Loaded ?: return
        val firstLesson = LocalDateTime(loaded.series.startDate, loaded.series.lessonStartTime ?: LocalTime(0, 0))
        val request = buildUpdateEventSeriesRequest(formData(), deadline.resolve(firstLesson))
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
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.setPublished(uuid, published) },
            onSuccess = {
                showToast(if (published) currentStrings.toastPublished else currentStrings.toastHidden)
                uiState = EditSeriesUiState.Loaded(loaded.series.copy(isPublished = published))
            },
        )
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
        deadlineMessage = deadline.message,
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
