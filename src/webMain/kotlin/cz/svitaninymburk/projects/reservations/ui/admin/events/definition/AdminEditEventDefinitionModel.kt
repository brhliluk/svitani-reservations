package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.DefinitionFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildUpdateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.validateDefinitionForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

sealed interface EditEventDefinitionUiState {
    data object Loading : EditEventDefinitionUiState
    data class Loaded(val definition: EventDefinition) : EditEventDefinitionUiState
    data class Error(val message: String) : EditEventDefinitionUiState
}

class AdminEditEventDefinitionModel(
    scope: CoroutineScope,
    private val queries: EventDefinitionQueries,
    private val mutations: EventDefinitionMutations,
    private val router: Router,
    private val id: String,
) : ScreenModel(scope) {

    var uiState: EditEventDefinitionUiState by mutableStateOf(EditEventDefinitionUiState.Loading); private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var price: Number? by mutableStateOf(0)
    var capacity by mutableIntStateOf(10)
    var durationHours by mutableIntStateOf(1)
    var durationMinutes by mutableIntStateOf(0)
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())
    var propagateToChildren by mutableStateOf(false)
    var ownerEmails by mutableStateOf(listOf(""))
    var showAttendeeCount by mutableStateOf(true)
    var isSubmitting by mutableStateOf(false); private set

    fun load() {
        val uuid = runCatching { Uuid.parse(id) }.getOrNull()
            ?: run { uiState = EditEventDefinitionUiState.Error(currentStrings.invalidEventId); return }
        scope.launch {
            queries.forEdit(uuid)
                .onRight { def ->
                    title = def.title
                    description = def.description
                    price = def.defaultPrice
                    capacity = def.defaultCapacity
                    durationHours = def.defaultDuration.inWholeHours.toInt()
                    durationMinutes = (def.defaultDuration.inWholeMinutes % 60).toInt()
                    allowBankTransfer = def.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
                    allowOnSite = def.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
                    customFields = def.customFields
                    ownerEmails = def.ownerEmails.ifEmpty { listOf("") }
                    showAttendeeCount = def.showAttendeeCount
                    uiState = EditEventDefinitionUiState.Loaded(def)
                }
                .onLeft { uiState = EditEventDefinitionUiState.Error(it.localizedMessage(currentStrings)) }
        }
    }

    fun submit() {
        val validationError = validateDefinitionForm(title, ownerEmails)
        if (validationError != null) {
            showToast(validationErrorMessage(validationError), ToastType.Error)
            return
        }
        val request = buildUpdateEventDefinitionRequest(formData(), propagateToChildren)
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.update(Uuid.parse(id), request) },
            onSuccess = {
                showToast(currentStrings.toastDefinitionUpdated)
                scope.launch { delay(500); router.navigate("/admin/events") }
            },
        )
    }

    private fun formData() = EventDefinitionFormData(
        title = title,
        description = description,
        ownerEmails = ownerEmails,
        price = price?.toDouble() ?: 0.0,
        capacity = capacity,
        durationHours = durationHours,
        durationMinutes = durationMinutes,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        customFields = customFields,
        showAttendeeCount = showAttendeeCount,
    )

    private fun validationErrorMessage(error: DefinitionFormValidationError): String = when (error) {
        DefinitionFormValidationError.MissingTitle -> currentStrings.validationNameRequired
        DefinitionFormValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
    }
}

fun IComponent.buildAdminEditEventDefinitionModel(
    scope: CoroutineScope,
    router: Router,
    id: String,
): AdminEditEventDefinitionModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminEditEventDefinitionModel(
        scope = scope,
        queries = EventDefinitionQueries(admin),
        mutations = EventDefinitionMutations(admin),
        router = router,
        id = id,
    )
}
