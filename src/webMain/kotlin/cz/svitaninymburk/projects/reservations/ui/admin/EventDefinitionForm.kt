package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import web.html.HTMLSelectElement
import kotlin.js.js
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun IComponent.AdminCreateEventDefinitionScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    // --- ZÁKLADNÍ STAVY FORMULÁŘE ---
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }

    // Čas rozdělený na hodiny a minuty
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }

    // Platby
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }

    // --- OPAKOVÁNÍ ---
    var recurrenceType by remember { mutableStateOf(RecurrenceType.NONE) }
    var recurrenceEndDateStr by remember { mutableStateOf("") } // "YYYY-MM-DD"

    // --- STAVY PRO CUSTOM FIELDS BUILDER ---
    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }

    fun updateCustomField(index: Int, newField: CustomFieldDefinition) {
        customFields = customFields.toMutableList().apply { set(index, newField) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { js("window.history.back()") }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newTemplateTitle }
                p(className = "text-base-content/60") { +currentStrings.newTemplateSubtitle }
            }
        }

        // --- ZÁKLADNÍ INFORMACE ---
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }

                div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                    // Název
                    div(className = "form-control w-full md:col-span-2") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.eventNameLabel } }
                        text(value = title, className = "input input-bordered w-full") {
                            onInput { title = value ?: "" }
                        }
                    }

                    // Popis (Opraveno: Nyní drží md:col-span-2 a šířku 100%)
                    div(className = "form-control w-full md:col-span-2") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                        textArea(value = description, className = "textarea textarea-bordered h-24 w-full") {
                            onInput { description = value ?: "" }
                        }
                    }

                    // Výchozí cena (s "Kč" uvnitř)
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.defaultPriceLabel } }
                        div(className = "relative flex items-center") {
                            numeric(value = price, min = 0, className = "input input-bordered w-full pr-12") {
                                onInput { price = value }
                            }
                            span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
                        }
                    }

                    // Výchozí kapacita (Jen celá čísla)
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.capacityPersonLabel } }
                        div(className = "relative flex items-center") {
                            numeric(value = capacity, min = 1, decimals = 0, className = "input input-bordered w-full pr-12") {
                                attribute("step", "1")
                                onInput { capacity = value?.toInt() ?: 1 }
                            }
                            span(className = "absolute right-4 text-base-content/50") {
                                span(className = "icon-[heroicons--users] size-5")
                            }
                        }
                    }

                    // Výchozí délka (Hodiny a minuty)
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.defaultDurationLabel } }
                        div(className = "flex gap-2") {
                            // Hodiny
                            div(className = "relative flex-1 items-center flex") {
                                numeric(value = durationHours, min = 0, decimals = 0, className = "input input-bordered w-full pr-8") {
                                    attribute("step", "1")
                                    onInput { durationHours = value?.toInt() ?: 0 }
                                }
                                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.hours }
                            }
                            // Minuty
                            div(className = "relative flex-1 items-center flex") {
                                numeric(value = durationMinutes, min = 0, max = 59, decimals = 0, className = "input input-bordered w-full pr-12") {
                                    attribute("step", "1")
                                    onInput { durationMinutes = value?.toInt() ?: 0 }
                                }
                                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.minutes }
                            }
                        }
                    }

                    // Povolené platby
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.allowedPaymentsLabel } }
                        div(className = "flex gap-4 mt-2") {
                            label(className = "cursor-pointer label justify-start gap-2") {
                                checkBox(value = allowBankTransfer, className = "checkbox checkbox-primary") {
                                    onChange { allowBankTransfer = value }
                                }
                                span(className = "label-text") { +currentStrings.bankTransfer }
                            }
                            label(className = "cursor-pointer label justify-start gap-2") {
                                checkBox(value = allowOnSite, className = "checkbox checkbox-primary") {
                                    onChange { allowOnSite = value }
                                }
                                span(className = "label-text") { +currentStrings.paymentOnSite }
                            }
                        }
                    }
                }
            }
        }

        // --- OPAKOVÁNÍ ---
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                h2(className = "card-title text-lg mb-4") { +currentStrings.recurrenceHeading }
                div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceTypeLabel } }
                        select(className = "select select-bordered w-full") {
                            option(value = "NONE", label = currentStrings.recurrenceNone) { if (recurrenceType == RecurrenceType.NONE) selected(true) }
                            option(value = "DAILY", label = currentStrings.recurrenceDaily) { if (recurrenceType == RecurrenceType.DAILY) selected(true) }
                            option(value = "WEEKLY", label = currentStrings.recurrenceWeekly) { if (recurrenceType == RecurrenceType.WEEKLY) selected(true) }
                            option(value = "MONTHLY", label = currentStrings.recurrenceMonthly) { if (recurrenceType == RecurrenceType.MONTHLY) selected(true) }
                            onChange { event ->
                                val v = (event.target as? HTMLSelectElement)?.value ?: "NONE"
                                recurrenceType = RecurrenceType.valueOf(v)
                                if (recurrenceType == RecurrenceType.NONE) recurrenceEndDateStr = ""
                            }
                        }
                    }

                    if (recurrenceType != RecurrenceType.NONE) {
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceEndLabel } }
                            text(value = recurrenceEndDateStr, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { recurrenceEndDateStr = value ?: "" }
                            }
                        }
                    }
                }
            }
        }

        // --- CUSTOM FIELDS BUILDER ---
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                div(className = "flex justify-between items-center mb-4") {
                    h2(className = "card-title text-lg") { +currentStrings.customFieldsHeading }

                    div(className = "dropdown dropdown-end") {
                        div(className = "btn btn-sm btn-outline btn-secondary") {
                            attribute("tabindex", "0")
                            attribute("role", "button")
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
                                                val updated = when(field) {
                                                    is TextFieldDefinition -> field.copy(key = newKey)
                                                    is NumberFieldDefinition -> field.copy(key = newKey)
                                                    is BooleanFieldDefinition -> field.copy(key = newKey)
                                                    is TimeRangeFieldDefinition -> field.copy(key = newKey)
                                                }
                                                updateCustomField(index, updated)
                                            }
                                        }
                                    }
                                    div(className = "form-control") {
                                        label(className = "label py-1") { span(className = "label-text text-xs") { +currentStrings.fieldLabelLabel } }
                                        text(value = field.label, className = "input input-sm input-bordered") {
                                            onInput {
                                                val newLabel = value ?: ""
                                                val updated = when(field) {
                                                    is TextFieldDefinition -> field.copy(label = newLabel)
                                                    is NumberFieldDefinition -> field.copy(label = newLabel)
                                                    is BooleanFieldDefinition -> field.copy(label = newLabel)
                                                    is TimeRangeFieldDefinition -> field.copy(label = newLabel)
                                                }
                                                updateCustomField(index, updated)
                                            }
                                        }
                                    }

                                    div(className = "form-control md:col-span-2") {
                                        label(className = "cursor-pointer label justify-start gap-2 py-1") {
                                            checkBox(value = field.isRequired, className = "checkbox checkbox-xs") {
                                                onChange {
                                                    val req = value
                                                    val updated = when(field) {
                                                        is TextFieldDefinition -> field.copy(isRequired = req)
                                                        is NumberFieldDefinition -> field.copy(isRequired = req)
                                                        is BooleanFieldDefinition -> field.copy(isRequired = req)
                                                        is TimeRangeFieldDefinition -> field.copy(isRequired = req)
                                                    }
                                                    updateCustomField(index, updated)
                                                }
                                            }
                                            span(className = "label-text text-xs") { +currentStrings.fieldRequired }

                                            when (field) {
                                                is TextFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeText }
                                                is NumberFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeNumber }
                                                is BooleanFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeBoolean }
                                                is TimeRangeFieldDefinition -> span(className = "badge badge-neutral badge-sm ml-4") { +currentStrings.fieldTypeTimeRange }
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

        // --- ULOŽIT ---
        div(className = "flex justify-end gap-2 mt-4") {
            button(className = "btn") {
                onClick { js("window.history.back()") }
                +currentStrings.cancel
            }
            button(className = "btn btn-primary") {
                onClick {
                    if (title.isBlank()) {
                        toastData = ToastData(currentStrings.validationNameRequired, ToastType.Error)
                        return@onClick
                    }
                    if (recurrenceType != RecurrenceType.NONE && recurrenceEndDateStr.isBlank()) {
                        toastData = ToastData(currentStrings.validationRecurrenceEndRequired, ToastType.Error)
                        return@onClick
                    }

                    val allowedPayments = mutableListOf<PaymentInfo.Type>()
                    if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                    if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                    val finalDuration = durationHours.hours + durationMinutes.minutes

                    val recurrenceEndInstant = if (recurrenceType != RecurrenceType.NONE && recurrenceEndDateStr.isNotBlank()) {
                        try {
                            LocalDate.parse(recurrenceEndDateStr).atStartOfDayIn(TimeZone.currentSystemDefault())
                        } catch (e: Exception) {
                            toastData = ToastData(currentStrings.validationRecurrenceDateFormat, ToastType.Error)
                            return@onClick
                        }
                    } else null

                    val request = CreateEventDefinitionRequest(
                        title = title,
                        description = description,
                        defaultPrice = price?.toDouble() ?: 0.0,
                        defaultCapacity = capacity,
                        defaultDuration = finalDuration,
                        allowedPaymentTypes = allowedPayments,
                        customFields = customFields,
                        recurrenceType = recurrenceType,
                        recurrenceEndDate = recurrenceEndInstant
                    )

                    scope.launch {
                        adminService.createEventDefinition(request)
                            .onRight {
                                toastData = ToastData(currentStrings.templateSavedToast, ToastType.Success)
                                kotlinx.coroutines.delay(500)
                                router.navigate("/admin/events") // Zpět do katalogu
                            }
                            .onLeft { error ->
                                toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                            }
                    }
                }
                span(className = "icon-[heroicons--check] size-5")
                +currentStrings.createTemplateButton
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}