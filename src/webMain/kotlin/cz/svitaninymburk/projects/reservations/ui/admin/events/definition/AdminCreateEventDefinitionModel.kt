package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.DefinitionFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.EventDefinitionMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.buildCreateEventDefinitionRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.definition.usecase.validateDefinitionForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdminCreateEventDefinitionModel(
    scope: CoroutineScope,
    private val mutations: EventDefinitionMutations,
    private val router: Router,
    currentUser: User,
) : ScreenModel(scope) {

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var ownerEmails by mutableStateOf(listOf(currentUser.email))
    var price: Number? by mutableStateOf(0)
    var capacity by mutableIntStateOf(10)
    var durationHours by mutableIntStateOf(1)
    var durationMinutes by mutableIntStateOf(0)
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())
    var isSubmitting by mutableStateOf(false); private set

    fun submit() {
        val validationError = validateDefinitionForm(title, ownerEmails)
        if (validationError != null) {
            showToast(validationErrorMessage(validationError), ToastType.Error)
            return
        }
        val request = buildCreateEventDefinitionRequest(formData())
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.create(request) },
            onSuccess = {
                showToast(currentStrings.templateSavedToast)
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
        allowMultipleSeats = allowMultipleSeats,
    )

    private fun validationErrorMessage(error: DefinitionFormValidationError): String = when (error) {
        DefinitionFormValidationError.MissingTitle -> currentStrings.validationNameRequired
        DefinitionFormValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
    }
}

fun IComponent.buildAdminCreateEventDefinitionModel(
    scope: CoroutineScope,
    router: Router,
    currentUser: User,
): AdminCreateEventDefinitionModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminCreateEventDefinitionModel(
        scope = scope,
        mutations = EventDefinitionMutations(admin),
        router = router,
        currentUser = currentUser,
    )
}
