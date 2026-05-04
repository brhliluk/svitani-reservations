package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.history.history
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private sealed interface EditDefinitionUiState {
    data object Loading : EditDefinitionUiState
    data class Loaded(val definition: EventDefinition) : EditDefinitionUiState
    data class Error(val message: String) : EditDefinitionUiState
}

@Composable
fun IComponent.AdminEditEventDefinitionScreen(id: String) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    var uiState by remember { mutableStateOf<EditDefinitionUiState>(EditDefinitionUiState.Loading) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }
    var propagateToChildren by remember { mutableStateOf(false) }

    fun updateCustomField(index: Int, newField: CustomFieldDefinition) {
        customFields = customFields.toMutableList().apply { set(index, newField) }
    }

    LaunchedEffect(id) {
        val uuid = try { Uuid.parse(id) } catch (_: IllegalArgumentException) { null }
        if (uuid == null) { uiState = EditDefinitionUiState.Error(currentStrings.invalidEventId); return@LaunchedEffect }
        adminService.getEventDefinitionForEdit(uuid)
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
                uiState = EditDefinitionUiState.Loaded(def)
            }
            .onLeft { uiState = EditDefinitionUiState.Error(it.localizedMessage(currentStrings)) }
    }

    fun doSave() {
        val uuid = Uuid.parse(id)
        val allowedPayments = buildList {
            if (allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
            if (allowOnSite) add(PaymentInfo.Type.ON_SITE)
        }
        val request = UpdateEventDefinitionRequest(
            title = title,
            description = description,
            defaultPrice = price?.toDouble() ?: 0.0,
            defaultCapacity = capacity,
            defaultDuration = durationHours.hours + durationMinutes.minutes,
            allowedPaymentTypes = allowedPayments,
            customFields = customFields,
            propagateToChildren = propagateToChildren,
        )
        scope.launch {
            adminService.updateEventDefinition(uuid, request)
                .onRight {
                    toastData = ToastData(currentStrings.toastDefinitionUpdated, ToastType.Success)
                    kotlinx.coroutines.delay(500)
                    router.navigate("/admin/events")
                }
                .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
        }
    }

    when (val state = uiState) {
        is EditDefinitionUiState.Loading -> Loading()
        is EditDefinitionUiState.Error -> div(className = "alert alert-error max-w-lg mx-auto mt-10") { +state.message }
        is EditDefinitionUiState.Loaded -> {
            div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5")
                        onClick { history.back() }
                    }
                    div {
                        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.editTemplateTitle }
                        p(className = "text-base-content/60") { +state.definition.title }
                    }
                }

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.eventNameLabel } }
                                text(value = title, className = "input input-bordered w-full") { onInput { title = value ?: "" } }
                            }
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = description, className = "textarea textarea-bordered h-24 w-full") { onInput { description = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.defaultPriceLabel } }
                                div(className = "relative flex items-center") {
                                    numeric(value = price, min = 0, className = "input input-bordered w-full pr-12") { onInput { price = value } }
                                    span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.capacityPersonLabel } }
                                div(className = "relative flex items-center") {
                                    numeric(value = capacity, min = 1, decimals = 0, className = "input input-bordered w-full pr-12") {
                                        attribute("step", "1"); onInput { capacity = value?.toInt() ?: 1 }
                                    }
                                    span(className = "absolute right-4 text-base-content/50") { span(className = "icon-[heroicons--users] size-5") }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.defaultDurationLabel } }
                                div(className = "flex gap-2") {
                                    div(className = "relative flex-1 items-center flex") {
                                        numeric(value = durationHours, min = 0, decimals = 0, className = "input input-bordered w-full pr-8") {
                                            attribute("step", "1"); onInput { durationHours = value?.toInt() ?: 0 }
                                        }
                                        span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.hours }
                                    }
                                    div(className = "relative flex-1 items-center flex") {
                                        numeric(value = durationMinutes, min = 0, max = 59, decimals = 0, className = "input input-bordered w-full pr-12") {
                                            attribute("step", "1"); onInput { durationMinutes = value?.toInt() ?: 0 }
                                        }
                                        span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.minutes }
                                    }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.allowedPaymentsLabel } }
                                div(className = "flex gap-4 mt-2") {
                                    label(className = "cursor-pointer label justify-start gap-2") {
                                        checkBox(value = allowBankTransfer, className = "checkbox checkbox-primary") { onChange { allowBankTransfer = value } }
                                        span(className = "label-text") { +currentStrings.bankTransfer }
                                    }
                                    label(className = "cursor-pointer label justify-start gap-2") {
                                        checkBox(value = allowOnSite, className = "checkbox checkbox-primary") { onChange { allowOnSite = value } }
                                        span(className = "label-text") { +currentStrings.paymentOnSite }
                                    }
                                }
                            }
                        }
                    }
                }

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        div(className = "flex justify-between items-center mb-4") {
                            h2(className = "card-title text-lg") { +currentStrings.customFieldsHeading }
                            div(className = "dropdown dropdown-end") {
                                div(className = "btn btn-sm btn-outline btn-secondary") {
                                    attribute("tabindex", "0"); attribute("role", "button")
                                    span(className = "icon-[heroicons--plus] size-4")
                                    +currentStrings.addFieldButton
                                }
                                ul(className = "dropdown-content z-[1] menu p-2 shadow bg-base-100 rounded-box w-52") {
                                    attribute("tabindex", "0")
                                    li { a { onClick { customFields = customFields + TextFieldDefinition("field_${customFields.size}", "Nové textové pole") }; +currentStrings.addTextField } }
                                    li { a { onClick { customFields = customFields + NumberFieldDefinition("field_${customFields.size}", "Nové číselné pole") }; +currentStrings.addNumberField } }
                                    li { a { onClick { customFields = customFields + BooleanFieldDefinition("field_${customFields.size}", "Nové zaškrtávací pole") }; +currentStrings.addBooleanField } }
                                    li { a { onClick { customFields = customFields + TimeRangeFieldDefinition("field_${customFields.size}", "Nový časový úsek") }; +currentStrings.addTimeRangeField } }
                                }
                            }
                        }
                        if (customFields.isEmpty()) {
                            p(className = "text-base-content/50 italic text-sm") { +currentStrings.noCustomFieldsMessage }
                        } else {
                            div(className = "flex flex-col gap-4") {
                                customFields.forEachIndexed { index, field ->
                                    div(className = "flex gap-4 items-start p-4 bg-base-200/50 rounded-lg border border-base-300 relative") {
                                        button(className = "btn btn-circle btn-ghost btn-xs absolute top-2 right-2 text-error") {
                                            onClick { customFields = customFields.toMutableList().apply { removeAt(index) } }
                                            span(className = "icon-[heroicons--trash] size-4")
                                        }
                                        div(className = "flex-1 grid grid-cols-1 md:grid-cols-2 gap-3") {
                                            div(className = "form-control") {
                                                label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldKeyLabel } }
                                                text(value = field.key, className = "input input-sm input-bordered") {
                                                    onInput {
                                                        val newKey = value ?: ""
                                                        updateCustomField(index, when(field) {
                                                            is TextFieldDefinition -> field.copy(key = newKey)
                                                            is NumberFieldDefinition -> field.copy(key = newKey)
                                                            is BooleanFieldDefinition -> field.copy(key = newKey)
                                                            is TimeRangeFieldDefinition -> field.copy(key = newKey)
                                                        })
                                                    }
                                                }
                                            }
                                            div(className = "form-control") {
                                                label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldLabelLabel } }
                                                text(value = field.label, className = "input input-sm input-bordered") {
                                                    onInput {
                                                        val newLabel = value ?: ""
                                                        updateCustomField(index, when(field) {
                                                            is TextFieldDefinition -> field.copy(label = newLabel)
                                                            is NumberFieldDefinition -> field.copy(label = newLabel)
                                                            is BooleanFieldDefinition -> field.copy(label = newLabel)
                                                            is TimeRangeFieldDefinition -> field.copy(label = newLabel)
                                                        })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        label(className = "cursor-pointer flex items-center gap-3") {
                            checkBox(value = propagateToChildren, className = "checkbox checkbox-primary") {
                                onChange { propagateToChildren = value }
                            }
                            div {
                                span(className = "font-medium") { +currentStrings.propagateToChildren }
                                if (propagateToChildren) {
                                    p(className = "text-sm text-warning mt-1") { +currentStrings.propagateToChildrenNote }
                                }
                            }
                        }
                    }
                }

                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") { onClick { history.back() }; +currentStrings.cancel }
                    button(className = "btn btn-primary") {
                        onClick {
                            if (title.isBlank()) { toastData = ToastData(currentStrings.validationNameRequired, ToastType.Error); return@onClick }
                            doSave()
                        }
                        span(className = "icon-[heroicons--check] size-5")
                        +currentStrings.saveChanges
                    }
                }
            }
        }
    }

    Toast(message = toastData?.message, type = toastData?.type ?: ToastType.Success, onDismiss = { toastData = null })
}
