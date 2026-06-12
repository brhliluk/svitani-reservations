package cz.svitaninymburk.projects.reservations.ui.admin.events.definition

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import web.history.history
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun IComponent.AdminCreateEventDefinitionScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // --- ZÁKLADNÍ STAVY FORMULÁŘE ---
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var ownerEmails by remember { mutableStateOf(listOf("")) }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }

    // Čas rozdělený na hodiny a minuty
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }

    // Platby
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var showAttendeeCount by remember { mutableStateOf(true) }

    // --- STAVY PRO CUSTOM FIELDS BUILDER ---
    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
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

                    OwnerEmailsField(ownerEmails) { ownerEmails = it }

                    PriceCurrencyField(currentStrings.defaultPriceLabel, price) { price = it }

                    CapacityField(capacity) { capacity = it }

                    DurationField(
                        currentStrings.defaultDurationLabel,
                        durationHours, durationMinutes,
                        { durationHours = it }, { durationMinutes = it },
                    )

                    AllowedPaymentsField(allowBankTransfer, allowOnSite, { allowBankTransfer = it }, { allowOnSite = it })

                    ShowAttendeeCountCheckbox(showAttendeeCount) { showAttendeeCount = it }
                }
            }
        }


        // --- CUSTOM FIELDS BUILDER ---
        CustomFieldsBuilderSection(customFields) { customFields = it }


        // --- ULOŽIT ---
        div(className = "flex justify-end gap-2 mt-4") {
            button(className = "btn") {
                onClick { history.back() }
                +currentStrings.cancel
            }
            button(className = "btn btn-primary") {
                disabled(isSubmitting)
                onClick {
                    if (title.isBlank()) {
                        toastData = ToastData(currentStrings.validationNameRequired, ToastType.Error)
                        return@onClick
                    }
                    val validOwnerEmails = ownerEmails.filter { it.isNotBlank() }
                    if (validOwnerEmails.isEmpty()) {
                        toastData = ToastData(currentStrings.validationOwnerEmailRequired, ToastType.Error)
                        return@onClick
                    }
                    val allowedPayments = mutableListOf<PaymentInfo.Type>()
                    if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                    if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                    val finalDuration = durationHours.hours + durationMinutes.minutes

                    val request = CreateEventDefinitionRequest(
                        title = title,
                        description = description,
                        ownerEmails = validOwnerEmails,
                        defaultPrice = price?.toDouble() ?: 0.0,
                        defaultCapacity = capacity,
                        defaultDuration = finalDuration,
                        allowedPaymentTypes = allowedPayments,
                        customFields = customFields,
                        showAttendeeCount = showAttendeeCount,
                    )

                    isSubmitting = true
                    scope.launch {
                        adminService.createEventDefinition(request)
                            .onRight {
                                isSubmitting = false
                                toastData = ToastData(currentStrings.templateSavedToast, ToastType.Success)
                                kotlinx.coroutines.delay(500)
                                router.navigate("/admin/events") // Zpět do katalogu
                            }
                            .onLeft { error ->
                                isSubmitting = false
                                toastData = ToastData(currentStrings.errorToast(error.localizedMessage(currentStrings)), ToastType.Error)
                            }
                    }
                }
                if (isSubmitting) span(className = "loading loading-spinner loading-sm")
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