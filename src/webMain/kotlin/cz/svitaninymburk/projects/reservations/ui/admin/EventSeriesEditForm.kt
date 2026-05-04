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
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import dev.kilua.rpc.getService
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import web.history.history
import web.html.HTMLSelectElement
import kotlin.uuid.Uuid

private sealed interface EditSeriesUiState {
    data object Loading : EditSeriesUiState
    data class Loaded(val series: EventSeries) : EditSeriesUiState
    data class Error(val message: String) : EditSeriesUiState
}

@Composable
fun IComponent.AdminEditEventSeriesScreen(id: String) {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    var uiState by remember { mutableStateOf<EditSeriesUiState>(EditSeriesUiState.Loading) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }
    var occupiedSpots by remember { mutableIntStateOf(0) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var lessonCount by remember { mutableIntStateOf(1) }
    var lessonDayOfWeekOrdinal by remember { mutableStateOf<Int?>(null) }
    var lessonStartTimeStr by remember { mutableStateOf("") }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }
    var showCapacityWarning by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val uuid = try { Uuid.parse(id) } catch (_: IllegalArgumentException) { null }
        if (uuid == null) { uiState = EditSeriesUiState.Error(currentStrings.invalidEventId); return@LaunchedEffect }
        adminService.getEventSeriesForEdit(uuid)
            .onRight { s ->
                title = s.title
                description = s.description
                price = s.price
                capacity = s.capacity
                occupiedSpots = s.occupiedSpots
                startDate = s.startDate.toString()
                endDate = s.endDate.toString()
                lessonCount = s.lessonCount
                lessonDayOfWeekOrdinal = s.lessonDayOfWeek?.isoDayNumber
                lessonStartTimeStr = s.lessonStartTime?.toString() ?: ""
                allowBankTransfer = s.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
                allowOnSite = s.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
                uiState = EditSeriesUiState.Loaded(s)
            }
            .onLeft { uiState = EditSeriesUiState.Error(it.localizedMessage(currentStrings)) }
    }

    fun doSave() {
        val uuid = Uuid.parse(id)
        val parsedStart = try { LocalDate.parse(startDate) } catch (_: Exception) {
            toastData = ToastData(currentStrings.validationStartDateFormat, ToastType.Error); return
        }
        val parsedEnd = try { LocalDate.parse(endDate) } catch (_: Exception) {
            toastData = ToastData(currentStrings.validationEndDateFormat, ToastType.Error); return
        }
        if (parsedEnd < parsedStart) { toastData = ToastData(currentStrings.validationEndBeforeStart, ToastType.Error); return }
        val allowedPayments = buildList {
            if (allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
            if (allowOnSite) add(PaymentInfo.Type.ON_SITE)
        }
        val parsedDay = lessonDayOfWeekOrdinal?.let { DayOfWeek(it) }
        val parsedStartTime = if (lessonStartTimeStr.isNotBlank()) {
            try { LocalTime.parse(lessonStartTimeStr) } catch (_: Exception) { null }
        } else null
        val loadedStartTime = (uiState as? EditSeriesUiState.Loaded)?.series?.lessonStartTime
        val loadedEndTime = (uiState as? EditSeriesUiState.Loaded)?.series?.lessonEndTime
        val parsedEndTime = when {
            parsedStartTime == null -> null
            parsedStartTime == loadedStartTime -> loadedEndTime
            else -> null
        }

        val request = UpdateEventSeriesRequest(
            title = title, description = description,
            price = price?.toDouble() ?: 0.0, capacity = capacity,
            startDate = parsedStart, endDate = parsedEnd,
            lessonCount = lessonCount, allowedPaymentTypes = allowedPayments,
            customFields = emptyList(),
            lessonDayOfWeek = parsedDay,
            lessonStartTime = parsedStartTime,
            lessonEndTime = parsedEndTime,
        )
        scope.launch {
            adminService.updateEventSeries(uuid, request)
                .onRight {
                    toastData = ToastData(currentStrings.toastSeriesUpdated, ToastType.Success)
                    kotlinx.coroutines.delay(500)
                    history.back()
                }
                .onLeft { toastData = ToastData(currentStrings.errorToast(it.toString()), ToastType.Error) }
        }
    }

    when (val state = uiState) {
        is EditSeriesUiState.Loading -> Loading()
        is EditSeriesUiState.Error -> div(className = "alert alert-error max-w-lg mx-auto mt-10") { +state.message }
        is EditSeriesUiState.Loaded -> {
            div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {
                div(className = "flex items-center gap-4") {
                    button(className = "btn btn-circle btn-ghost btn-sm") {
                        span(className = "icon-[heroicons--arrow-left] size-5"); onClick { history.back() }
                    }
                    div {
                        h1(className = "text-3xl font-bold text-base-content") { +currentStrings.editSeriesTitle }
                        p(className = "text-base-content/60") { +state.series.title }
                    }
                }
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.seriesTitleLabel } }
                                text(value = title, className = "input input-bordered w-full") { onInput { title = value ?: "" } }
                            }
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = description, className = "textarea textarea-bordered h-24 w-full") { onInput { description = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.startDateLabel } }
                                text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") { onInput { startDate = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.endDateLabel } }
                                text(value = endDate, type = InputType.Date, className = "input input-bordered w-full") { onInput { endDate = value ?: "" } }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.lessonCountLabel } }
                                numeric(value = lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full") {
                                    attribute("step", "1"); onInput { lessonCount = value?.toInt() ?: 1 }
                                }
                            }
                            // Den lekce
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.lessonDayLabel } }
                                select(className = "select select-bordered w-full") {
                                    option(value = "", label = currentStrings.lessonDayPlaceholder) {
                                        if (lessonDayOfWeekOrdinal == null) attribute("selected", "true")
                                    }
                                    DayOfWeek.entries.forEach { day ->
                                        option(value = day.isoDayNumber.toString(), label = currentStrings.dayName(day.isoDayNumber - 1)) {
                                            if (lessonDayOfWeekOrdinal == day.isoDayNumber) attribute("selected", "true")
                                        }
                                    }
                                    onChange { event ->
                                        val v = (event.target as? HTMLSelectElement)?.value
                                        lessonDayOfWeekOrdinal = v?.toIntOrNull()
                                    }
                                }
                            }
                            // Čas lekce
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.lessonTimeLabel } }
                                text(value = lessonStartTimeStr, type = InputType.Time, className = "input input-bordered w-full") {
                                    onInput { lessonStartTimeStr = value ?: "" }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.fullCoursePriceLabel } }
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
                            if (title.isBlank()) { toastData = ToastData(currentStrings.validationSeriesTitleRequired, ToastType.Error); return@onClick }
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
