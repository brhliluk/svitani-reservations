package cz.svitaninymburk.projects.reservations.ui.admin

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

                    div(className = "form-control w-full md:col-span-2") {
                        label(className = "label") {
                            span(className = "label-text font-medium") { +currentStrings.ownerEmailsLabel }
                        }
                        div(className = "flex flex-col gap-2") {
                            ownerEmails.forEachIndexed { index, email ->
                                div(className = "flex gap-2 items-center") {
                                    text(value = email, className = "input input-bordered flex-1") {
                                        placeholder(currentStrings.ownerEmailPlaceholder)
                                        onInput {
                                            ownerEmails = ownerEmails.toMutableList().apply { set(index, value ?: "") }
                                        }
                                    }
                                    if (ownerEmails.size > 1) {
                                        button(className = "btn btn-ghost btn-sm btn-circle text-error") {
                                            onClick {
                                                ownerEmails = ownerEmails.toMutableList().apply { removeAt(index) }
                                            }
                                            span(className = "icon-[heroicons--x-mark] size-4")
                                        }
                                    }
                                }
                            }
                            button(className = "btn btn-outline btn-sm gap-2 self-start mt-1") {
                                onClick { ownerEmails = ownerEmails + "" }
                                span(className = "icon-[heroicons--plus] size-4")
                                +currentStrings.addOwnerEmailButton
                            }
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

                    div(className = "form-control w-full md:col-span-2") {
                        p(className = "label-text font-medium mb-1") { +currentStrings.showAttendeeCount }
                        label(className = "cursor-pointer label justify-start gap-3") {
                            checkBox(value = showAttendeeCount, className = "checkbox checkbox-primary") {
                                onChange { showAttendeeCount = value }
                            }
                            span(className = "label-text text-sm text-base-content/70") { +currentStrings.showAttendeeCountHint }
                        }
                    }
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