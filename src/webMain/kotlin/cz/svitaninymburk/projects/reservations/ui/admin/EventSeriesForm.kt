package cz.svitaninymburk.projects.reservations.ui.admin

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.event.*
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import web.history.history
import web.html.HTMLSelectElement
import kotlin.time.Clock
import kotlin.uuid.Uuid


@Composable
fun IComponent.AdminCreateEventSeriesScreen(preselectedDefinitionId: String? = null) {
    val router = Router.current
    val eventService = getService<EventServiceInterface>(RpcSerializersModules)
    val adminService = getService<AdminServiceInterface>(RpcSerializersModules)

    val scope = rememberCoroutineScope()
    val currentStrings by strings
    var toastData by remember { mutableStateOf<ToastData?>(null) }

    var definitions by remember { mutableStateOf<List<EventDefinition>>(emptyList()) }
    var isLoadingDefinitions by remember { mutableStateOf(true) }

    var selectedDefinitionId by remember { mutableStateOf(preselectedDefinitionId) }

    var startDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var endDate by remember { mutableStateOf("") }
    var lessonCount by remember { mutableIntStateOf(1) }
    var lessonDayOfWeekOrdinal by remember { mutableStateOf<Int?>(null) }  // 1=Mon…7=Sun (ISO)
    var lessonStartTimeStr by remember { mutableStateOf("") }               // "HH:MM"

    var titleOverride by remember { mutableStateOf("") }
    var descriptionOverride by remember { mutableStateOf("") }
    var priceOverride by remember { mutableStateOf<Number?>(0) }
    var capacityOverride by remember { mutableIntStateOf(10) }
    var allowBankTransfer by remember { mutableStateOf(true) }
    var allowOnSite by remember { mutableStateOf(true) }

    fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity
        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentInfo.Type.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentInfo.Type.ON_SITE)
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
                toastData = ToastData(currentStrings.toastTemplatesLoadError(error.toString()), ToastType.Error)
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
                h1(className = "text-3xl font-bold text-base-content") { +currentStrings.newSeriesTitle }
                p(className = "text-base-content/60") { +currentStrings.newSeriesSubtitle }
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
                    div(className = "text-sm") { +currentStrings.noTemplatesSeriesMessage }
                }
                button(className = "btn btn-sm btn-primary") {
                    onClick { router.navigate("/admin/events/new") }
                    +currentStrings.createTemplateButton
                }
            }
        } else {
            // --- VÝBĚR ŠABLONY A OBDOBÍ ---
            div(className = "card bg-base-100 shadow-sm border-t-4 border-secondary") {
                div(className = "card-body") {
                    div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                        // Výběr šablony (skryto pokud je předvybrána)
                        if (preselectedDefinitionId == null) {
                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-bold") { +currentStrings.templateSelectionLabel } }
                                select(className = "select select-bordered w-full text-base") {
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

                        // Datum začátku
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.startDateLabel } }
                            text(value = startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { startDate = value ?: "" }
                            }
                        }

                        // Datum konce
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.endDateLabel } }
                            text(value = endDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { endDate = value ?: "" }
                            }
                        }

                        // Počet lekcí
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonCountLabel } }
                            div(className = "relative flex items-center") {
                                numeric(value = lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full pr-16") {
                                    attribute("step", "1")
                                    onInput { lessonCount = value?.toInt() ?: 1 }
                                }
                                span(className = "absolute right-4 text-base-content/50 text-sm") { +currentStrings.courseLessons }
                            }
                        }

                        // Den lekce
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonDayLabel } }
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
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonTimeLabel } }
                            text(value = lessonStartTimeStr, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { lessonStartTimeStr = value ?: "" }
                            }
                        }

                    }
                }
            }

            // --- ÚPRAVY PRO TENTO KURZ ---
            if (selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +currentStrings.seriesOverrideHeading }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +currentStrings.seriesOverrideDescription
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.seriesTitleLabel } }
                                text(value = titleOverride, className = "input input-bordered w-full") {
                                    onInput { titleOverride = value ?: "" }
                                }
                            }

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { descriptionOverride = value ?: "" }
                                }
                            }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.fullCoursePriceLabel } }
                                div(className = "relative flex items-center") {
                                    numeric(value = priceOverride, min = 0, className = "input input-bordered w-full pr-12") {
                                        onInput { priceOverride = value }
                                    }
                                    span(className = "absolute right-4 text-base-content/50 font-medium") { +currentStrings.currency }
                                }
                            }

                            div(className = "form-control w-full") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.capacityPersonLabel } }
                                div(className = "relative flex items-center") {
                                    numeric(value = capacityOverride, min = 1, decimals = 0, className = "input input-bordered w-full pr-12") {
                                        attribute("step", "1")
                                        onInput { capacityOverride = value?.toInt() ?: 1 }
                                    }
                                    span(className = "absolute right-4 text-base-content/50") {
                                        span(className = "icon-[heroicons--users] size-5")
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

                // --- ULOŽIT ---
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { history.back() }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-secondary") {
                        onClick {
                            if (startDate.isBlank() || endDate.isBlank()) {
                                toastData = ToastData(currentStrings.validationDatesRequired, ToastType.Error)
                                return@onClick
                            }
                            if (titleOverride.isBlank()) {
                                toastData = ToastData(currentStrings.validationSeriesTitleRequired, ToastType.Error)
                                return@onClick
                            }

                            val parsedStartDate = try { LocalDate.parse(startDate) } catch (e: Exception) {
                                toastData = ToastData(currentStrings.validationStartDateFormat, ToastType.Error)
                                return@onClick
                            }
                            val parsedEndDate = try { LocalDate.parse(endDate) } catch (e: Exception) {
                                toastData = ToastData(currentStrings.validationEndDateFormat, ToastType.Error)
                                return@onClick
                            }
                            if (parsedEndDate < parsedStartDate) {
                                toastData = ToastData(currentStrings.validationEndBeforeStart, ToastType.Error)
                                return@onClick
                            }

                            val allowedPayments = mutableListOf<PaymentInfo.Type>()
                            if (allowBankTransfer) allowedPayments.add(PaymentInfo.Type.BANK_TRANSFER)
                            if (allowOnSite) allowedPayments.add(PaymentInfo.Type.ON_SITE)

                            val parsedDay = lessonDayOfWeekOrdinal?.let { DayOfWeek(it) }
                            val parsedStartTime = if (lessonStartTimeStr.isNotBlank()) {
                                try { LocalTime.parse(lessonStartTimeStr) } catch (_: Exception) { null }
                            } else null
                            val selectedDef = definitions.find { it.id.toString() == selectedDefinitionId }
                            val parsedEndTime = if (parsedStartTime != null && selectedDef != null) {
                                val startMinutes = parsedStartTime.hour * 60 + parsedStartTime.minute
                                val durationMinutes = selectedDef.defaultDuration.inWholeMinutes.toInt()
                                val endMinutes = (startMinutes + durationMinutes) % (24 * 60)
                                LocalTime(endMinutes / 60, endMinutes % 60)
                            } else null

                            val request = CreateEventSeriesRequest(
                                definitionId = Uuid.parse(selectedDefinitionId!!),
                                title = titleOverride,
                                description = descriptionOverride,
                                price = priceOverride?.toDouble() ?: 0.0,
                                capacity = capacityOverride,
                                startDate = parsedStartDate,
                                endDate = parsedEndDate,
                                lessonCount = lessonCount,
                                allowedPaymentTypes = allowedPayments,
                                lessonDayOfWeek = parsedDay,
                                lessonStartTime = parsedStartTime,
                                lessonEndTime = parsedEndTime,
                            )

                            scope.launch {
                                adminService.createEventSeries(request)
                                    .onRight {
                                        toastData = ToastData(currentStrings.toastSeriesCreated, ToastType.Success)
                                        delay(500)
                                        router.navigate("/admin/events")
                                    }
                                    .onLeft { error ->
                                        toastData = ToastData(currentStrings.errorToast(error.toString()), ToastType.Error)
                                    }
                            }
                        }
                        span(className = "icon-[heroicons--check] size-5")
                        +currentStrings.createSeriesButton
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
