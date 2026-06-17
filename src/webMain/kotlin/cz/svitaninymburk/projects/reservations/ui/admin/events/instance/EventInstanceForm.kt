package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AuthenticatedEventServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastData
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.WaitlistCapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import cz.svitaninymburk.projects.reservations.util.humanReadable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import dev.kilua.html.*
import dev.kilua.rpc.getService
import cz.svitaninymburk.projects.reservations.event.generateRecurrenceDates
import cz.svitaninymburk.projects.reservations.i18n.strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import web.history.history
import web.html.HTMLSelectElement
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid


@Composable
fun IComponent.AdminCreateEventInstanceScreen(preselectedDefinitionId: String? = null) {
    val router = Router.current

    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val authEventService = getService<AuthenticatedEventServiceInterface>(RpcSerializersModules)

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var definitions by remember { mutableStateOf<List<EventDefinition>>(emptyList()) }
    var isLoadingDefinitions by remember { mutableStateOf(true) }

    var selectedDefinitionId by remember { mutableStateOf(preselectedDefinitionId) }

    var startDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) } // Formát: YYYY-MM-DD
    var startTime by remember { mutableStateOf("") } // Formát: HH:mm

    var titleOverride by remember { mutableStateOf("") }
    var descriptionOverride by remember { mutableStateOf("") }
    var ownerEmails by remember { mutableStateOf(listOf("")) }
    var priceOverride by remember { mutableStateOf<Number?>(0) }
    var capacityOverride by remember { mutableIntStateOf(10) }
    var waitlistCapacityOverride by remember { mutableIntStateOf(0) }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var showAttendeeCount by remember { mutableStateOf(true) }

    var deadlineEnabled by remember { mutableStateOf(false) }
    var deadlineTypeIsHours by remember { mutableStateOf(true) }
    var deadlineHours by remember { mutableIntStateOf(2) }
    var deadlineDaysBefore by remember { mutableIntStateOf(1) }
    var deadlineTimeStr by remember { mutableStateOf("18:00") }
    var deadlineMessage by remember { mutableStateOf("") }

    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }

    // Inline recurrence (no longer read from definition)
    var recurrenceType by remember { mutableStateOf(RecurrenceType.NONE) }
    var recurrenceEndDateStr by remember { mutableStateOf("") }

    val selectedDefinition = definitions.find { it.id.toString() == selectedDefinitionId }
    val isRecurring = recurrenceType != RecurrenceType.NONE && recurrenceEndDateStr.isNotBlank()

    val previewDates: List<LocalDateTime> = remember(startDate, startTime, recurrenceType, recurrenceEndDateStr) {
        if (!isRecurring || startDate.isBlank() || startTime.isBlank()) return@remember emptyList()
        val d = try { LocalDate.parse(startDate) } catch (_: Exception) { return@remember emptyList() }
        val t = try { LocalTime.parse(startTime) } catch (_: Exception) { return@remember emptyList() }
        val endInstant = try {
            LocalDate.parse(recurrenceEndDateStr).atStartOfDayIn(TimeZone.currentSystemDefault())
        } catch (_: Exception) { return@remember emptyList() }
        generateRecurrenceDates(d, t, recurrenceType, endInstant)
    }

    // Funkce pro předvyplnění formuláře při změně šablony
    fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        ownerEmails = definition.ownerEmails.ifEmpty { listOf("") }
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity

        definition.defaultDuration.toComponents { hours, minutes, _, _ ->
            durationHours = hours.toInt()
            durationMinutes = minutes
        }

        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
        showAttendeeCount = definition.showAttendeeCount
    }

    LaunchedEffect(Unit) {
        eventService.getAllDefinitions()
            .onRight { defs ->
                definitions = defs
                isLoadingDefinitions = false
                if (preselectedDefinitionId != null) {
                    defs.find { it.id.toString() == preselectedDefinitionId }?.let { applyDefinitionDefaults(it) }
                }
            }
            .onLeft { error ->
                toastData = ToastData(currentStrings.toastTemplatesLoadError(error.localizedMessage(currentStrings)), ToastType.Error)
                isLoadingDefinitions = false
            }
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Hlavička
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newInstanceTitle }
                p(className = "text-base-content/60") { +currentStrings.newInstanceSubtitle }
            }
        }

        if (isLoadingDefinitions) {
            div(className = "flex justify-center p-10") {
                span(className = "loading loading-spinner loading-lg text-primary")
            }
        } else if (definitions.isEmpty()) {
            div(className = "alert alert-warning shadow-sm") {
                span(className = "icon-[heroicons--exclamation-triangle] size-6")
                div {
                    h3(className = "font-bold") { +currentStrings.noTemplatesHeading }
                    div(className = "text-sm") { +currentStrings.noTemplatesInstanceMessage }
                }
                button(className = "btn btn-sm btn-primary") {
                    onClick { router.navigate("/admin/events/new") }
                    +currentStrings.createTemplateButton
                }
            }
        } else {
            // --- VÝBĚR ŠABLONY A TERMÍNU ---
            div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
                div(className = "card-body") {

                    div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                        // Výběr šablony
                        if (preselectedDefinitionId == null) {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.templateSelectionLabel } }
                                select(className = "select select-bordered w-full text-base") {
                                    // Výchozí prázdná volba
                                    option(value = "", label = currentStrings.templatePlaceholder) {
                                        if (selectedDefinitionId == null) attribute("selected", "true")
                                        attribute("disabled", "true")
                                    }
                                    definitions.forEach { def ->
                                        option(value = def.id.toString(), label = def.title)
                                    }
                                    onChange { event ->
                                        val targetSelect = event.target as? HTMLSelectElement
                                        val selectedValue = targetSelect?.value
                                        selectedDefinitionId = selectedValue
                                        definitions.find { it.id.toString() == selectedValue }?.let { applyDefinitionDefaults(it) }
                                    }
                                }
                            }
                        }

                        // Datum
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.dateLabelField } }
                            text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { startDate = value ?: "" }
                            }
                        }

                        // Čas
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.timeLabelField } }
                            text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { startTime = value ?: "" }
                            }
                        }

                        // Opakování
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

            // --- MOŽNOST PŘEPSÁNÍ HODNOT (Zobrazí se až po výběru šablony) ---
            if (selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +currentStrings.overrideHeading }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +currentStrings.overrideDescription
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            // Název
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.instanceTitleLabel } }
                                text(value = titleOverride, className = "input input-bordered w-full") {
                                    onInput { titleOverride = value ?: "" }
                                }
                            }

                            // Popis
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { descriptionOverride = value ?: "" }
                                }
                            }

                            OwnerEmailsField(ownerEmails) { ownerEmails = it }

                            PriceCurrencyField(currentStrings.priceLabel, priceOverride) { priceOverride = it }

                            CapacityField(capacityOverride) { capacityOverride = it }

                            WaitlistCapacityField(waitlistCapacityOverride) { waitlistCapacityOverride = it }

                            DurationField(
                                currentStrings.durationLabel,
                                durationHours, durationMinutes,
                                { durationHours = it }, { durationMinutes = it },
                            )

                            AllowedPaymentsField(allowBankTransfer, allowOnSite, { allowBankTransfer = it }, { allowOnSite = it })

                            ShowAttendeeCountCheckbox(value = showAttendeeCount) { showAttendeeCount = it }

                        }
                    }
                }

                // --- NÁHLED OPAKOVÁNÍ ---
                if (isRecurring && startDate.isNotBlank() && startTime.isNotBlank()) {
                    div(className = "card bg-base-100 shadow-sm border border-primary/30") {
                        div(className = "card-body") {
                            div(className = "flex items-center gap-2 mb-3") {
                                span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                                h2(className = "card-title text-base") {
                                    +currentStrings.recurrencePreviewHeading(previewDates.size)
                                }
                            }
                            if (previewDates.isEmpty()) {
                                p(className = "text-warning text-sm") { +currentStrings.recurrencePreviewError }
                            } else {
                                div(className = "flex flex-wrap gap-2") {
                                    previewDates.forEach { dt ->
                                        span(className = "badge badge-outline badge-primary") {
                                            +dt.humanReadable
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CUSTOM FIELDS BUILDER ---
                CustomFieldsBuilderSection(customFields) { customFields = it }

                // --- UZÁVĚRKA REZERVACÍ ---
                ReservationDeadlineSection(
                    enabled = deadlineEnabled,
                    typeIsHours = deadlineTypeIsHours,
                    hours = deadlineHours,
                    daysBefore = deadlineDaysBefore,
                    timeStr = deadlineTimeStr,
                    message = deadlineMessage,
                    onEnabledChange = { deadlineEnabled = it },
                    onTypeChange = { deadlineTypeIsHours = it },
                    onHoursChange = { deadlineHours = it },
                    onDaysBeforeChange = { deadlineDaysBefore = it },
                    onTimeStrChange = { deadlineTimeStr = it },
                    onMessageChange = { deadlineMessage = it },
                )

                // --- ULOŽIT ---
                fun submitForm(isPublished: Boolean) {
                    if (startDate.isBlank() || startTime.isBlank()) {
                        toastData = ToastData(currentStrings.validationDateTimeRequired, ToastType.Error)
                        return
                    }
                    val validOwnerEmails = ownerEmails.filter { it.isNotBlank() }
                    if (validOwnerEmails.isEmpty()) {
                        toastData = ToastData(currentStrings.validationOwnerEmailRequired, ToastType.Error)
                        return
                    }

                    val isoDateTimeString = "${startDate}T${startTime}"
                    val parsedDateTime = try {
                        LocalDateTime.parse(isoDateTimeString)
                    } catch (e: Exception) {
                        toastData = ToastData(currentStrings.validationDateTimeFormat, ToastType.Error)
                        return
                    }

                    val allowedPayments = mutableListOf<PaymentInfo.Type>()
                    if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                    if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                    val resolvedDeadline: Duration? = if (deadlineEnabled) {
                        if (deadlineTypeIsHours) {
                            deadlineHours.hours
                        } else {
                            try {
                                val tz = TimeZone.of("Europe/Prague")
                                val deadlineDate = parsedDateTime.date.minus(deadlineDaysBefore, DateTimeUnit.DAY)
                                val deadlineDateTime = LocalDateTime(deadlineDate, LocalTime.parse(deadlineTimeStr))
                                parsedDateTime.toInstant(tz) - deadlineDateTime.toInstant(tz)
                            } catch (_: Exception) { null }
                        }
                    } else null

                    val baseRequest = CreateEventInstanceRequest(
                        definitionId = Uuid.parse(selectedDefinitionId!!),
                        startDateTime = parsedDateTime,
                        title = titleOverride.takeIf { it.isNotBlank() },
                        description = descriptionOverride.takeIf { it.isNotBlank() },
                        duration = durationHours.hours + durationMinutes.minutes,
                        price = priceOverride?.toDouble() ?: 0.0,
                        capacity = capacityOverride,
                        waitlistCapacity = waitlistCapacityOverride,
                        allowedPaymentTypes = allowedPayments,
                        customFields = customFields,
                        ownerEmails = validOwnerEmails,
                        showAttendeeCount = showAttendeeCount,
                        reservationDeadline = resolvedDeadline,
                        reservationDeadlineMessage = deadlineMessage.takeIf { it.isNotBlank() },
                        isPublished = isPublished,
                    )

                    val dateTimes = if (isRecurring && previewDates.isNotEmpty()) previewDates else listOf(parsedDateTime)

                    if (dateTimes.isEmpty()) {
                        toastData = ToastData(currentStrings.validationNoDates, ToastType.Error)
                        return
                    }

                    isSubmitting = true
                    scope.launch {
                        var failed = false
                        for (dt in dateTimes) {
                            authEventService.createEventInstance(baseRequest.copy(startDateTime = dt))
                                .onLeft { error ->
                                    isSubmitting = false
                                    toastData = ToastData(currentStrings.toastInstanceCreateError(dt.toString(), error.localizedMessage(currentStrings)), ToastType.Error)
                                    failed = true
                                }
                            if (failed) break
                        }
                        if (!failed) {
                            isSubmitting = false
                            toastData = ToastData(
                                if (dateTimes.size > 1) currentStrings.toastInstancesCreated(dateTimes.size) else currentStrings.toastInstanceCreated,
                                ToastType.Success
                            )
                            delay(500.milliseconds)
                            router.navigate("/admin/events")
                        }
                    }
                }

                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { history.back() }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-outline") {
                        disabled(isSubmitting)
                        onClick { submitForm(false) }
                        +currentStrings.saveDraftButton
                    }
                    button(className = "btn btn-primary") {
                        disabled(isSubmitting)
                        onClick { submitForm(true) }
                        if (isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--check] size-5")
                        +(if (isRecurring && previewDates.size > 1) currentStrings.createInstancesButton(previewDates.size) else currentStrings.createInstanceButton)
                    }
                }
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}