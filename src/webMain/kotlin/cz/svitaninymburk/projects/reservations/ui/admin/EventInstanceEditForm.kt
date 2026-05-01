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
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import web.history.history
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

    var uiState by remember { mutableStateOf<EditInstanceUiState>(EditInstanceUiState.Loading) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }
    var occupiedSpots by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var showCapacityWarning by remember { mutableStateOf(false) }

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
                occupiedSpots = inst.occupiedSpots
                allowBankTransfer = inst.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
                allowOnSite = inst.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
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
        val request = UpdateEventInstanceRequest(
            title = title, description = description,
            startDateTime = startDt, endDateTime = endDt,
            price = price?.toDouble() ?: 0.0, capacity = capacity,
            allowedPaymentTypes = allowedPayments, customFields = emptyList(),
        )
        scope.launch {
            adminService.updateEventInstance(uuid, request)
                .onRight {
                    toastData = ToastData(currentStrings.toastEventUpdated, ToastType.Success)
                    kotlinx.coroutines.delay(500)
                    history.back()
                }
                .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
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
                    div {
                        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.editInstanceTitle }
                        p(className = "text-base-content/60") { +state.instance.title }
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
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.dateLabelField } }
                                text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") { onInput { startDate = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.timeLabelField } }
                                text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") { onInput { startTime = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.durationLabel } }
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
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") { onClick { history.back() }; +currentStrings.cancel }
                    button(className = "btn btn-primary") {
                        onClick {
                            if (title.isBlank()) { toastData = ToastData(currentStrings.validationNameRequired, ToastType.Error); return@onClick }
                            if (capacity < occupiedSpots) { showCapacityWarning = true; return@onClick }
                            doSave()
                        }
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

    Toast(message = toastData?.message, type = toastData?.type ?: ToastType.Success, onDismiss = { toastData = null })
}
