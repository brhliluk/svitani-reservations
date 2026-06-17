package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

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
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import web.history.history
import web.html.HTMLSelectElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private sealed interface EditInstanceUiState {
    data object Loading : EditInstanceUiState
    data class Loaded(val instance: EventInstance) : EditInstanceUiState
    data class Error(val message: String) : EditInstanceUiState
}

@Composable
fun IComponent.AdminEditEventInstanceScreen(id: String) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var uiState by remember { mutableStateOf<EditInstanceUiState>(EditInstanceUiState.Loading) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }
    var waitlistCapacity by remember { mutableIntStateOf(0) }
    var occupiedSpots by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var showCapacityWarning by remember { mutableStateOf(false) }
    var isDropIn by remember { mutableStateOf(false) }
    var ownerEmails by remember { mutableStateOf(listOf("")) }
    var showAttendeeCount by remember { mutableStateOf(true) }
    var showHideConfirm by remember { mutableStateOf(false) }

    var deadlineEnabled by remember { mutableStateOf(false) }
    var deadlineTypeIsHours by remember { mutableStateOf(true) }
    var deadlineHours by remember { mutableIntStateOf(2) }
    var deadlineDaysBefore by remember { mutableIntStateOf(1) }
    var deadlineTimeStr by remember { mutableStateOf("18:00") }
    var deadlineMessage by remember { mutableStateOf("") }
    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }

    LaunchedEffect(id) {
        val uuid = try { Uuid.parse(id) } catch (_: IllegalArgumentException) { null }
        if (uuid == null) { uiState = EditInstanceUiState.Error(currentStrings.invalidEventId); return@LaunchedEffect }
        adminService.getEventInstanceForEdit(uuid)
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
                allowBankTransfer = inst.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
                allowOnSite = inst.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
                isDropIn = inst.isDropIn
                ownerEmails = inst.ownerEmails.ifEmpty { listOf("") }
                showAttendeeCount = inst.showAttendeeCount
                val instDeadline = inst.reservationDeadline
                if (instDeadline != null) {
                    deadlineEnabled = true
                    deadlineHours = instDeadline.inWholeHours.toInt()
                    deadlineTypeIsHours = true
                }
                deadlineMessage = inst.reservationDeadlineMessage ?: ""
                customFields = inst.customFields
                uiState = EditInstanceUiState.Loaded(inst)
            }
            .onLeft { uiState = EditInstanceUiState.Error(it.localizedMessage(currentStrings)) }
    }

    fun doSave() {
        val uuid = Uuid.parse(id)
        val parsedDate = try { LocalDate.parse(startDate) } catch (_: Exception) {
            toastData = ToastData(currentStrings.validationDateTimeFormat, ToastType.Error); return
        }
        val timeParts = startTime.split(":")
        if (timeParts.size != 2) { toastData = ToastData(currentStrings.validationDateTimeFormat, ToastType.Error); return }
        val startDt = LocalDateTime(parsedDate, LocalTime(timeParts[0].toInt(), timeParts[1].toInt()))
        val tz = TimeZone.currentSystemDefault()
        val endDt = (startDt.toInstant(tz) + durationHours.hours + durationMinutes.minutes).toLocalDateTime(tz)
        val allowedPayments = buildList {
            if (allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
            if (allowOnSite) add(PaymentInfo.Type.ON_SITE)
        }
        val resolvedDeadline: Duration? = if (deadlineEnabled) {
            if (deadlineTypeIsHours) {
                deadlineHours.hours
            } else {
                try {
                    val pragueTz = TimeZone.of("Europe/Prague")
                    val deadlineDate = startDt.date.minus(deadlineDaysBefore, DateTimeUnit.DAY)
                    val deadlineDateTime = LocalDateTime(deadlineDate, LocalTime.parse(deadlineTimeStr))
                    startDt.toInstant(pragueTz) - deadlineDateTime.toInstant(pragueTz)
                } catch (_: Exception) { null }
            }
        } else null
        val request = UpdateEventInstanceRequest(
            title = title, description = description,
            startDateTime = startDt, endDateTime = endDt,
            price = price?.toDouble() ?: 0.0, capacity = capacity, waitlistCapacity = waitlistCapacity,
            allowedPaymentTypes = allowedPayments, customFields = customFields,
            isDropIn = isDropIn,
            ownerEmails = ownerEmails.filter { it.isNotBlank() },
            showAttendeeCount = showAttendeeCount,
            reservationDeadline = resolvedDeadline,
            reservationDeadlineMessage = deadlineMessage.takeIf { it.isNotBlank() },
        )
        isSubmitting = true
        scope.launch {
            adminService.updateEventInstance(uuid, request)
                .onRight {
                    isSubmitting = false
                    toastData = ToastData(currentStrings.toastEventUpdated, ToastType.Success)
                    kotlinx.coroutines.delay(500)
                    history.back()
                }
                .onLeft {
                    isSubmitting = false
                    toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error)
                }
        }
    }

    when (val state = uiState) {
        is EditInstanceUiState.Loading -> Loading()
        is EditInstanceUiState.Error -> div(className = "alert alert-error max-w-lg mx-auto mt-10") { +state.message }
        is EditInstanceUiState.Loaded -> {
            div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {
                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5"); onClick { history.back() }
                    }
                    div(className = "flex-1") {
                        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.editInstanceTitle }
                        p(className = "text-base-content/60") { +state.instance.title }
                    }
                    if (state.instance.isPublished) {
                        span(className = "badge badge-primary badge-sm") { +currentStrings.statusPublished }
                    } else {
                        span(className = "badge badge-ghost badge-sm") { +currentStrings.statusHidden }
                    }
                    val isPublished = state.instance.isPublished
                    button(className = if (isPublished) "btn btn-sm btn-outline" else "btn btn-sm btn-primary") {
                        onClick {
                            if (isPublished && occupiedSpots > 0) {
                                showHideConfirm = true
                            } else {
                                val uuid = try { Uuid.parse(id) } catch (_: IllegalArgumentException) { return@onClick }
                                scope.launch {
                                    adminService.setInstancePublished(uuid, !isPublished)
                                        .onRight {
                                            val msg = if (isPublished) currentStrings.toastHidden else currentStrings.toastPublished
                                            toastData = ToastData(msg, ToastType.Success)
                                            uiState = EditInstanceUiState.Loaded(state.instance.copy(isPublished = !isPublished))
                                        }
                                        .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
                                }
                            }
                        }
                        +if (isPublished) currentStrings.hideButton else currentStrings.publishButton
                    }
                }
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.instanceTitleLabel } }
                                text(value = title, className = "input input-bordered w-full") { onInput { title = value ?: "" } }
                            }
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = description, className = "textarea textarea-bordered h-24 w-full") { onInput { description = value ?: "" } }
                            }
                            OwnerEmailsField(ownerEmails) { ownerEmails = it }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.dateLabelField } }
                                text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") { onInput { startDate = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.timeLabelField } }
                                text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") { onInput { startTime = value ?: "" } }
                            }

                            DurationField(
                                currentStrings.durationLabel,
                                durationHours, durationMinutes,
                                { durationHours = it }, { durationMinutes = it },
                            )

                            PriceCurrencyField(currentStrings.defaultPriceLabel, price) { price = it }

                            CapacityField(capacity) { capacity = it }

                            WaitlistCapacityField(waitlistCapacity) { waitlistCapacity = it }

                            AllowedPaymentsField(allowBankTransfer, allowOnSite, { allowBankTransfer = it }, { allowOnSite = it })

                            ShowAttendeeCountCheckbox(value = showAttendeeCount) { showAttendeeCount = it }
                        }
                    }
                }
                if (state.instance.seriesId != null) {
                    div(className = "card bg-base-100 shadow-sm") {
                        div(className = "card-body") {
                            label(className = "cursor-pointer flex items-center gap-3") {
                                checkBox(value = isDropIn, className = "checkbox checkbox-secondary") {
                                    onChange { isDropIn = value }
                                }
                                div {
                                    span(className = "font-medium") { +currentStrings.dropInLabel }
                                    p(className = "text-sm text-base-content/60 mt-1") { +currentStrings.dropInDescription }
                                }
                            }
                        }
                    }
                }
                // --- CUSTOM FIELDS BUILDER ---
                CustomFieldsBuilderSection(customFields) { customFields = it }

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
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") { onClick { history.back() }; +currentStrings.cancel }
                    button(className = "btn btn-primary") {
                        disabled(isSubmitting)
                        onClick {
                            if (title.isBlank()) { toastData = ToastData(currentStrings.validationNameRequired, ToastType.Error); return@onClick }
                            val validOwnerEmails = ownerEmails.filter { it.isNotBlank() }
                            if (validOwnerEmails.isEmpty()) { toastData = ToastData(currentStrings.validationOwnerEmailRequired, ToastType.Error); return@onClick }
                            if (capacity < occupiedSpots) { showCapacityWarning = true; return@onClick }
                            doSave()
                        }
                        if (isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--check] size-5")
                        +currentStrings.saveChanges
                    }
                }
            }
        }
    }

    if (showCapacityWarning) {
        div(className = "modal modal-open") {
            div(className = "modal-box") {
                h3(className = "font-bold text-lg") { +currentStrings.capacityWarningTitle }
                p(className = "py-4") { +currentStrings.capacityWarningBody }
                div(className = "modal-action") {
                    button(className = "btn") { onClick { showCapacityWarning = false }; +currentStrings.cancel }
                    button(className = "btn btn-warning") { onClick { showCapacityWarning = false; doSave() }; +currentStrings.saveChanges }
                }
            }
        }
    }

    if (showHideConfirm) {
        val loadedState = uiState as? EditInstanceUiState.Loaded
        if (loadedState != null) {
            val inst = loadedState.instance
            div(className = "modal modal-open") {
                div(className = "modal-box") {
                    h3(className = "font-bold text-lg") { +currentStrings.hideButton }
                    p(className = "py-4") { +currentStrings.hideWithReservationsConfirm }
                    div(className = "modal-action") {
                        button(className = "btn") { onClick { showHideConfirm = false }; +currentStrings.cancel }
                        button(className = "btn btn-warning") {
                            onClick {
                                showHideConfirm = false
                                val uuid = try { Uuid.parse(id) } catch (_: IllegalArgumentException) { return@onClick }
                                scope.launch {
                                    adminService.setInstancePublished(uuid, false)
                                        .onRight {
                                            toastData = ToastData(currentStrings.toastHidden, ToastType.Success)
                                            uiState = EditInstanceUiState.Loaded(inst.copy(isPublished = false))
                                        }
                                        .onLeft { toastData = ToastData(currentStrings.errorToast(it.localizedMessage(currentStrings)), ToastType.Error) }
                                }
                            }
                            +currentStrings.hideButton
                        }
                    }
                }
            }
        }
    }

    Toast(message = toastData?.message, type = toastData?.type ?: ToastType.Success, onDismiss = { toastData = null })
}
