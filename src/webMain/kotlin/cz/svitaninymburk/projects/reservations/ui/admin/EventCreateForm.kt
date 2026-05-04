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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cz.svitaninymburk.projects.reservations.util.humanReadable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import cz.svitaninymburk.projects.reservations.event.LessonConfig
import web.history.history
import web.html.HTMLSelectElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private enum class EventCreateType { SINGLE, RECURRING, COURSE }

@Composable
fun IComponent.AdminCreateEventScreen() {
    val router = Router.current
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    // Type selector
    var eventType by remember { mutableStateOf(EventCreateType.SINGLE) }

    // Definition fields
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf<Number?>(0) }
    var capacity by remember { mutableIntStateOf(10) }
    var durationHours by remember { mutableIntStateOf(1) }
    var durationMinutes by remember { mutableIntStateOf(0) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }

    // Single / Recurring fields
    var startDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var startTime by remember { mutableStateOf("") }

    // Recurring-only fields
    var recurrenceType by remember { mutableStateOf(RecurrenceType.WEEKLY) }
    var recurrenceEndDateStr by remember { mutableStateOf("") }

    // Course fields
    var courseStartDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var lessonCount by remember { mutableIntStateOf(1) }
    var courseLessonDayOrdinal by remember { mutableStateOf<Int?>(null) }
    var courseLessonStartTimeStr by remember { mutableStateOf("") }
    var lessonDateOverrides by remember { mutableStateOf(mapOf<Int, String>()) }
    var lessonDropIn by remember { mutableStateOf(mapOf<Int, Boolean>()) }

    // Custom fields
    var customFields by remember { mutableStateOf(listOf<CustomFieldDefinition>()) }
    var showCustomFields by remember { mutableStateOf(false) }

    fun updateCustomField(index: Int, newField: CustomFieldDefinition) {
        customFields = customFields.toMutableList().apply { set(index, newField) }
    }

    // Recurring preview
    val previewDates: List<LocalDateTime> = remember(startDate, startTime, recurrenceType, recurrenceEndDateStr, eventType) {
        if (eventType != EventCreateType.RECURRING) return@remember emptyList()
        if (startDate.isBlank() || startTime.isBlank() || recurrenceEndDateStr.isBlank()) return@remember emptyList()
        val d = try { LocalDate.parse(startDate) } catch (_: Exception) { return@remember emptyList() }
        val t = try { LocalTime.parse(startTime) } catch (_: Exception) { return@remember emptyList() }
        val endInstant = try {
            LocalDate.parse(recurrenceEndDateStr).atStartOfDayIn(TimeZone.currentSystemDefault())
        } catch (_: Exception) { return@remember emptyList() }
        generateRecurrenceDates(d, t, recurrenceType, endInstant)
    }

    // Compute lesson dates from startDate + dayOfWeek + count
    val computedCourseDates: List<LocalDate> = remember(courseStartDate, courseLessonDayOrdinal, lessonCount) {
        val dayOrdinal = courseLessonDayOrdinal ?: return@remember emptyList()
        val startD = try { LocalDate.parse(courseStartDate) } catch (_: Exception) { return@remember emptyList() }
        if (lessonCount <= 0) return@remember emptyList()
        val targetDow = DayOfWeek(dayOrdinal)
        var date = startD
        while (date.dayOfWeek != targetDow) date = date.plus(1, DateTimeUnit.DAY)
        (0 until lessonCount).map { i -> date.plus(i, DateTimeUnit.WEEK) }
    }

    div(className = "flex flex-col gap-6 animate-fade-in max-w-4xl mx-auto pb-20") {

        // Header
        div(className = "flex items-center gap-4") {
            button(className = "btn btn-circle btn-ghost btn-sm") {
                span(className = "icon-[heroicons--arrow-left] size-5")
                onClick { history.back() }
            }
            div {
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newEventTitle }
                p(className = "text-base-content/60") { +currentStrings.newEventSubtitle }
            }
        }

        // Type selector
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                h2(className = "card-title text-lg mb-4") { +currentStrings.eventTypeHeading }
                div(className = "grid grid-cols-3 gap-3") {
                    listOf(
                        Triple(EventCreateType.SINGLE, currentStrings.eventTypeSingle, "icon-[heroicons--calendar] size-6"),
                        Triple(EventCreateType.RECURRING, currentStrings.eventTypeRecurring, "icon-[heroicons--calendar-days] size-6"),
                        Triple(EventCreateType.COURSE, currentStrings.eventTypeCourse, "icon-[heroicons--academic-cap] size-6"),
                    ).forEach { (type, label, icon) ->
                        val isSelected = eventType == type
                        val borderColor = when (type) {
                            EventCreateType.SINGLE -> if (isSelected) "border-primary bg-primary/5" else "border-base-300"
                            EventCreateType.RECURRING -> if (isSelected) "border-primary bg-primary/5" else "border-base-300"
                            EventCreateType.COURSE -> if (isSelected) "border-secondary bg-secondary/5" else "border-base-300"
                        }
                        div(className = "card border-2 $borderColor cursor-pointer transition-all hover:shadow-sm") {
                            onClick { eventType = type }
                            div(className = "card-body items-center text-center py-5 px-3 gap-2") {
                                span(className = icon)
                                span(className = "font-semibold text-sm") { +label }
                            }
                        }
                    }
                }
            }
        }

        // Basic info
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
                div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                    div(className = "form-control w-full md:col-span-2") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.eventNameLabel } }
                        text(value = title, className = "input input-bordered w-full") {
                            onInput { title = value ?: "" }
                        }
                    }

                    div(className = "form-control w-full md:col-span-2") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                        textArea(value = description, className = "textarea textarea-bordered h-24 w-full") {
                            onInput { description = value ?: "" }
                        }
                    }

                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.priceLabel } }
                        div(className = "relative flex items-center") {
                            numeric(value = price, min = 0, className = "input input-bordered w-full pr-12") {
                                onInput { price = value }
                            }
                            span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
                        }
                    }

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

                    div(className = "form-control w-full") {
                        label(className = "label") { span(className = "label-text font-medium") { +currentStrings.durationLabel } }
                        div(className = "flex gap-2") {
                            div(className = "relative flex-1 items-center flex") {
                                numeric(value = durationHours, min = 0, decimals = 0, className = "input input-bordered w-full pr-8") {
                                    attribute("step", "1")
                                    onInput { durationHours = value?.toInt() ?: 0 }
                                }
                                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.hours }
                            }
                            div(className = "relative flex-1 items-center flex") {
                                numeric(value = durationMinutes, min = 0, max = 59, decimals = 0, className = "input input-bordered w-full pr-12") {
                                    attribute("step", "1")
                                    onInput { durationMinutes = value?.toInt() ?: 0 }
                                }
                                span(className = "absolute right-3 text-base-content/50 text-sm") { +currentStrings.minutes }
                            }
                        }
                    }

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

        // Date / schedule section — varies by type
        when (eventType) {
            EventCreateType.SINGLE -> {
                div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
                    div(className = "card-body") {
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.dateLabelField } }
                                text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput { startDate = value ?: "" }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.timeLabelField } }
                                text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") {
                                    onInput { startTime = value ?: "" }
                                }
                            }
                        }
                    }
                }
            }

            EventCreateType.RECURRING -> {
                div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
                    div(className = "card-body") {
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.dateLabelField } }
                                text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput { startDate = value ?: "" }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.timeLabelField } }
                                text(value = startTime, type = InputType.Time, className = "input input-bordered w-full") {
                                    onInput { startTime = value ?: "" }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceTypeLabel } }
                                select(className = "select select-bordered w-full") {
                                    option(value = "DAILY", label = currentStrings.recurrenceDaily) { if (recurrenceType == RecurrenceType.DAILY) selected(true) }
                                    option(value = "WEEKLY", label = currentStrings.recurrenceWeekly) { if (recurrenceType == RecurrenceType.WEEKLY) selected(true) }
                                    option(value = "MONTHLY", label = currentStrings.recurrenceMonthly) { if (recurrenceType == RecurrenceType.MONTHLY) selected(true) }
                                    onChange { event ->
                                        val v = (event.target as? HTMLSelectElement)?.value ?: "WEEKLY"
                                        recurrenceType = RecurrenceType.valueOf(v)
                                    }
                                }
                            }
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceEndLabel } }
                                text(value = recurrenceEndDateStr, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput { recurrenceEndDateStr = value ?: "" }
                                }
                            }
                        }

                        if (startDate.isNotBlank() && startTime.isNotBlank() && recurrenceEndDateStr.isNotBlank()) {
                            div(className = "mt-4 p-4 bg-base-200/50 rounded-lg border border-primary/20") {
                                div(className = "flex items-center gap-2 mb-3") {
                                    span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
                                    span(className = "font-medium text-sm") {
                                        +currentStrings.recurrencePreviewHeading(previewDates.size)
                                    }
                                }
                                if (previewDates.isEmpty()) {
                                    p(className = "text-warning text-sm") { +currentStrings.recurrencePreviewError }
                                } else {
                                    div(className = "flex flex-wrap gap-2") {
                                        previewDates.forEach { dt ->
                                            span(className = "badge badge-outline badge-primary badge-sm") {
                                                +dt.humanReadable
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            EventCreateType.COURSE -> {
                div(className = "card bg-base-100 shadow-sm border-t-4 border-secondary") {
                    div(className = "card-body") {
                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                            // Datum začátku
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.startDateLabel } }
                                text(value = courseStartDate, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput {
                                        courseStartDate = value ?: ""
                                        lessonDateOverrides = emptyMap()
                                    }
                                }
                            }
                            // Počet lekcí
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonCountLabel } }
                                div(className = "relative flex items-center") {
                                    numeric(value = lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full pr-16") {
                                        attribute("step", "1")
                                        onInput {
                                            lessonCount = value?.toInt() ?: 1
                                            lessonDateOverrides = emptyMap()
                                        }
                                    }
                                    span(className = "absolute right-4 text-base-content/50 text-sm") { +currentStrings.courseLessons }
                                }
                            }
                            // Den lekce
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonDayLabel } }
                                select(className = "select select-bordered w-full") {
                                    option(value = "", label = currentStrings.lessonDayPlaceholder) {
                                        if (courseLessonDayOrdinal == null) attribute("selected", "true")
                                    }
                                    DayOfWeek.entries.forEach { day ->
                                        option(value = day.isoDayNumber.toString(), label = currentStrings.dayName(day.isoDayNumber - 1)) {
                                            if (courseLessonDayOrdinal == day.isoDayNumber) attribute("selected", "true")
                                        }
                                    }
                                    onChange { event ->
                                        val v = (event.target as? HTMLSelectElement)?.value
                                        courseLessonDayOrdinal = v?.toIntOrNull()
                                        lessonDateOverrides = emptyMap()
                                    }
                                }
                            }
                            // Čas lekce
                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonTimeLabel } }
                                text(value = courseLessonStartTimeStr, type = InputType.Time, className = "input input-bordered w-full") {
                                    onInput { courseLessonStartTimeStr = value ?: "" }
                                }
                            }
                        }

                        // Live lesson preview — only when day is selected
                        if (computedCourseDates.isNotEmpty()) {
                            val lessonStartT = if (courseLessonStartTimeStr.isNotBlank()) try { LocalTime.parse(courseLessonStartTimeStr) } catch (_: Exception) { null } else null
                            val endTimeStr = if (lessonStartT != null) {
                                val endMinutes = (lessonStartT.hour * 60 + lessonStartT.minute + durationHours * 60 + durationMinutes) % (24 * 60)
                                val endT = LocalTime(endMinutes / 60, endMinutes % 60)
                                "${endT.hour}:${endT.minute.toString().padStart(2, '0')}"
                            } else "?"
                            val startTimeStr = lessonStartT?.let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: "?"

                            div(className = "mt-4") {
                                div(className = "flex items-center gap-2 mb-2") {
                                    span(className = "icon-[heroicons--calendar-days] size-5 text-secondary")
                                    span(className = "font-medium text-sm") { +currentStrings.lessonPreviewHeading(computedCourseDates.size) }
                                    span(className = "text-xs text-base-content/50") { +currentStrings.lessonDateEditHint }
                                }
                                div(className = "overflow-x-auto") {
                                    table(className = "table table-xs w-full") {
                                        val allDropIn = computedCourseDates.indices.all { lessonDropIn[it] == true }
                                        thead {
                                            tr {
                                                th(className = "w-8") { +"#" }
                                                th { +currentStrings.tableHeaderDate }
                                                th { +currentStrings.tableHeaderTime }
                                                th {
                                                    div(className = "flex items-center gap-2") {
                                                        label(className = "cursor-pointer tooltip tooltip-left") {
                                                            attribute("data-tip", currentStrings.lessonIndividualBulkTooltip)
                                                            checkBox(value = allDropIn, className = "checkbox checkbox-secondary checkbox-xs") {
                                                                onChange {
                                                                    lessonDropIn = if (value) computedCourseDates.indices.associateWith { true } else emptyMap()
                                                                }
                                                            }
                                                        }
                                                        span(className = "tooltip tooltip-left cursor-help whitespace-nowrap") {
                                                            attribute("data-tip", currentStrings.lessonIndividualTooltip)
                                                            +currentStrings.lessonIndividualLabel
                                                            span(className = "icon-[heroicons--question-mark-circle] size-3 text-base-content/40 ml-1")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        tbody {
                                            computedCourseDates.forEachIndexed { i, defaultDate ->
                                                val effectiveDateStr = lessonDateOverrides[i] ?: defaultDate.toString()
                                                tr {
                                                    td(className = "text-base-content/50") { +"${i + 1}" }
                                                    td {
                                                        text(value = effectiveDateStr, type = InputType.Date, className = "input input-xs input-bordered w-36") {
                                                            onInput {
                                                                val v = value ?: ""
                                                                lessonDateOverrides = if (v == defaultDate.toString()) lessonDateOverrides - i
                                                                else lessonDateOverrides + (i to v)
                                                            }
                                                        }
                                                    }
                                                    td(className = "text-sm text-base-content/70") { +"$startTimeStr – $endTimeStr" }
                                                    td {
                                                        label(className = "cursor-pointer") {
                                                            checkBox(value = lessonDropIn[i] ?: false, className = "checkbox checkbox-secondary checkbox-xs") {
                                                                onChange { lessonDropIn = if (value) lessonDropIn + (i to true) else lessonDropIn - i }
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
                    }
                }
            }
        }

        // Custom fields (expandable)
        div(className = "card bg-base-100 shadow-sm") {
            div(className = "card-body") {
                div(className = "flex justify-between items-center") {
                    button(className = "btn btn-ghost btn-sm gap-2") {
                        onClick { showCustomFields = !showCustomFields }
                        span(className = if (showCustomFields) "icon-[heroicons--chevron-up] size-4" else "icon-[heroicons--chevron-down] size-4")
                        +currentStrings.customFieldsHeading
                        if (customFields.isNotEmpty()) {
                            div(className = "badge badge-neutral badge-sm") { +"${customFields.size}" }
                        }
                    }
                    if (showCustomFields) {
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
                }

                if (showCustomFields) {
                    if (customFields.isEmpty()) {
                        p(className = "text-base-content/50 italic text-sm mt-3") { +currentStrings.noCustomFieldsMessage }
                    } else {
                        div(className = "flex flex-col gap-4 mt-3") {
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
                                                    updateCustomField(index, when (field) {
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
                                                    updateCustomField(index, when (field) {
                                                        is TextFieldDefinition -> field.copy(label = newLabel)
                                                        is NumberFieldDefinition -> field.copy(label = newLabel)
                                                        is BooleanFieldDefinition -> field.copy(label = newLabel)
                                                        is TimeRangeFieldDefinition -> field.copy(label = newLabel)
                                                    })
                                                }
                                            }
                                        }
                                        div(className = "form-control md:col-span-2") {
                                            label(className = "cursor-pointer label justify-start gap-2 py-1") {
                                                checkBox(value = field.isRequired, className = "checkbox checkbox-xs") {
                                                    onChange {
                                                        val req = value
                                                        updateCustomField(index, when (field) {
                                                            is TextFieldDefinition -> field.copy(isRequired = req)
                                                            is NumberFieldDefinition -> field.copy(isRequired = req)
                                                            is BooleanFieldDefinition -> field.copy(isRequired = req)
                                                            is TimeRangeFieldDefinition -> field.copy(isRequired = req)
                                                        })
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
        }

        // Submit
        div(className = "flex justify-end gap-2 mt-4") {
            button(className = "btn") {
                onClick { history.back() }
                +currentStrings.cancel
            }

            val btnClass = if (eventType == EventCreateType.COURSE) "btn btn-secondary" else "btn btn-primary"
            button(className = btnClass) {
                onClick {
                    if (title.isBlank()) {
                        toastData = ToastData(currentStrings.validationTitleRequired, ToastType.Error)
                        return@onClick
                    }

                    val allowedPayments = buildList {
                        if (allowBankTransfer) add(PaymentInfo.Type.BANK_TRANSFER)
                        if (allowOnSite) add(PaymentInfo.Type.ON_SITE)
                    }
                    val finalDuration = durationHours.hours + durationMinutes.minutes

                    scope.launch {
                        when (eventType) {
                            EventCreateType.SINGLE -> {
                                if (startDate.isBlank() || startTime.isBlank()) {
                                    toastData = ToastData(currentStrings.validationDatesOrTimeRequired, ToastType.Error)
                                    return@launch
                                }
                                val dt = try {
                                    LocalDateTime.parse("${startDate}T${startTime}")
                                } catch (_: Exception) {
                                    toastData = ToastData(currentStrings.validationDateTimeFormat, ToastType.Error)
                                    return@launch
                                }
                                adminService.createEventAndInstances(
                                    CreateEventAndInstancesRequest(
                                        title = title,
                                        description = description,
                                        defaultPrice = price?.toDouble() ?: 0.0,
                                        defaultCapacity = capacity,
                                        defaultDuration = finalDuration,
                                        allowedPaymentTypes = allowedPayments,
                                        customFields = customFields,
                                        dateTimes = listOf(dt),
                                    )
                                ).onRight {
                                    toastData = ToastData(currentStrings.toastEventCreated, ToastType.Success)
                                    delay(500)
                                    router.navigate("/admin/events")
                                }.onLeft { error ->
                                    toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                                }
                            }

                            EventCreateType.RECURRING -> {
                                if (previewDates.isEmpty()) {
                                    toastData = ToastData(currentStrings.validationNoDates, ToastType.Error)
                                    return@launch
                                }
                                adminService.createEventAndInstances(
                                    CreateEventAndInstancesRequest(
                                        title = title,
                                        description = description,
                                        defaultPrice = price?.toDouble() ?: 0.0,
                                        defaultCapacity = capacity,
                                        defaultDuration = finalDuration,
                                        allowedPaymentTypes = allowedPayments,
                                        customFields = customFields,
                                        dateTimes = previewDates,
                                    )
                                ).onRight {
                                    toastData = ToastData(currentStrings.toastEventsCreated(previewDates.size), ToastType.Success)
                                    delay(500)
                                    router.navigate("/admin/events")
                                }.onLeft { error ->
                                    toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                                }
                            }

                            EventCreateType.COURSE -> {
                                if (courseStartDate.isBlank()) {
                                    toastData = ToastData(currentStrings.validationCourseDatesRequired, ToastType.Error)
                                    return@launch
                                }
                                val parsedStart = try { LocalDate.parse(courseStartDate) } catch (_: Exception) {
                                    toastData = ToastData(currentStrings.validationStartDateFormat, ToastType.Error)
                                    return@launch
                                }

                                // Build customLessons if day+time are specified
                                val lessonStartT = if (courseLessonStartTimeStr.isNotBlank()) try { LocalTime.parse(courseLessonStartTimeStr) } catch (_: Exception) { null } else null
                                val finalCustomLessons: List<LessonConfig>? = if (lessonStartT != null && computedCourseDates.isNotEmpty()) {
                                    val endMinutes = (lessonStartT.hour * 60 + lessonStartT.minute + durationHours * 60 + durationMinutes) % (24 * 60)
                                    val lessonEndT = LocalTime(endMinutes / 60, endMinutes % 60)
                                    computedCourseDates.indices.map { i ->
                                        val dateStr = lessonDateOverrides[i] ?: computedCourseDates[i].toString()
                                        val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { computedCourseDates[i] }
                                        LessonConfig(
                                            startDateTime = LocalDateTime(date, lessonStartT),
                                            endDateTime = LocalDateTime(date, lessonEndT),
                                            isDropIn = lessonDropIn[i] ?: false,
                                        )
                                    }
                                } else null

                                // Compute end date: last lesson date or fallback to startDate
                                val computedEndDate = computedCourseDates.lastOrNull()?.let {
                                    val overrideStr = lessonDateOverrides[computedCourseDates.size - 1]
                                    if (overrideStr != null) try { LocalDate.parse(overrideStr) } catch (_: Exception) { it } else it
                                } ?: parsedStart

                                adminService.createEventAndSeries(
                                    CreateEventAndSeriesRequest(
                                        title = title,
                                        description = description,
                                        defaultPrice = price?.toDouble() ?: 0.0,
                                        defaultCapacity = capacity,
                                        defaultDuration = finalDuration,
                                        allowedPaymentTypes = allowedPayments,
                                        customFields = customFields,
                                        startDate = parsedStart,
                                        endDate = computedEndDate,
                                        lessonCount = lessonCount,
                                        customLessons = finalCustomLessons,
                                    )
                                ).onRight {
                                    toastData = ToastData(currentStrings.toastCourseCreated, ToastType.Success)
                                    delay(500)
                                    router.navigate("/admin/events")
                                }.onLeft { error ->
                                    toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                                }
                            }
                        }
                    }
                }
                span(className = "icon-[heroicons--check] size-5")
                +currentStrings.createEventButton
            }
        }
    }

    Toast(
        message = toastData?.message,
        type = toastData?.type ?: ToastType.Success,
        onDismiss = { toastData = null }
    )
}
