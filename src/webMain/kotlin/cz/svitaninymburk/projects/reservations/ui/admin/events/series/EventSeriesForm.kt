package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CustomFieldsBuilderSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ReservationDeadlineSection
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowMultipleSeatsCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.WaitlistCapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeLessonEndTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.parseTimeOrNull
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.number.numeric
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import web.history.history
import web.html.HTMLSelectElement

@Composable
fun IComponent.AdminCreateEventSeriesScreen(currentUser: User, preselectedDefinitionId: String? = null) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val currentStrings by strings
    val model = remember(preselectedDefinitionId) {
        buildAdminCreateEventSeriesModel(scope, router, currentUser.email, preselectedDefinitionId)
    }

    LaunchedEffect(preselectedDefinitionId) { model.load() }

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

        if (model.isLoadingDefinitions) {
            div(className = "flex justify-center p-10") {
                span(className = "loading loading-spinner loading-lg text-primary")
            }
        } else if (model.definitions.isEmpty()) {
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
                                        if (model.selectedDefinitionId == null) attribute("selected", "true")
                                        attribute("disabled", "true")
                                    }
                                    model.definitions.forEach { def ->
                                        option(value = def.id.toString(), label = def.title)
                                    }
                                    onChange { event ->
                                        model.onDefinitionSelected((event.target as? HTMLSelectElement)?.value)
                                    }
                                }
                            }
                        }

                        // Datum začátku
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.startDateLabel } }
                            text(value = model.startDate, type = InputType.Date, className = "input input-bordered w-full") {
                                onInput { model.onStartDateChange(value ?: "") }
                            }
                        }

                        // Datum konce — editable only when no day is set, otherwise auto-computed
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.endDateLabel } }
                            if (model.lessonDayOfWeekOrdinal != null && model.effectiveLessonDates.isNotEmpty()) {
                                // Auto-computed: show as read-only
                                div(className = "input input-bordered w-full flex items-center bg-base-200/50 text-base-content/70 text-sm px-4") {
                                    +model.effectiveLessonDates.last().toString()
                                }
                            } else {
                                text(value = model.endDate, type = InputType.Date, className = "input input-bordered w-full") {
                                    onInput { model.endDate = value ?: "" }
                                }
                            }
                        }

                        // Počet lekcí
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonCountLabel } }
                            div(className = "relative flex items-center") {
                                numeric(value = model.lessonCount, min = 1, decimals = 0, className = "input input-bordered w-full pr-16") {
                                    attribute("step", "1")
                                    onInput { model.onLessonCountChange(value?.toInt() ?: 1) }
                                    onChange { model.onLessonCountChange(value?.toInt() ?: 1) }
                                }
                                span(className = "absolute right-4 text-base-content/50 text-sm") { +currentStrings.courseLessons }
                            }
                        }

                        // Den lekce
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonDayLabel } }
                            select(className = "select select-bordered w-full") {
                                option(value = "", label = currentStrings.lessonDayPlaceholder) {
                                    if (model.lessonDayOfWeekOrdinal == null) attribute("selected", "true")
                                }
                                DayOfWeek.entries.forEach { day ->
                                    option(value = day.isoDayNumber.toString(), label = currentStrings.dayName(day.isoDayNumber - 1)) {
                                        if (model.lessonDayOfWeekOrdinal == day.isoDayNumber) attribute("selected", "true")
                                    }
                                }
                                onChange { event ->
                                    model.onLessonDayChange((event.target as? HTMLSelectElement)?.value?.toIntOrNull())
                                }
                            }
                        }

                        // Čas lekce
                        div(className = "form-control w-full") {
                            label(className = "label") { span(className = "label-text font-bold") { +currentStrings.lessonTimeLabel } }
                            text(value = model.lessonStartTimeStr, type = InputType.Time, className = "input input-bordered w-full") {
                                onInput { model.lessonStartTimeStr = value ?: "" }
                            }
                        }

                    }

                    // Live lesson preview
                    if (model.computedSeriesDates.isNotEmpty()) {
                        val lessonStartT = parseTimeOrNull(model.lessonStartTimeStr)
                        val endTimeStr = if (lessonStartT != null && model.selectedDefinition != null) {
                            val endT = computeLessonEndTime(lessonStartT, model.selectedDefinition!!.defaultDuration.inWholeMinutes.toInt())
                            "${endT.hour}:${endT.minute.toString().padStart(2, '0')}"
                        } else "?"
                        val startTimeDisplayStr = lessonStartT?.let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: "?"

                        val excludedCount = model.computedSeriesDates.size - model.effectiveLessonDates.size

                        div(className = "mt-4 md:col-span-2") {
                            div(className = "flex items-center gap-2 mb-2") {
                                span(className = "icon-[heroicons--calendar-days] size-5 text-secondary")
                                span(className = "font-medium text-sm") { +currentStrings.lessonPreviewHeading(model.effectiveLessonDates.size) }
                                if (excludedCount > 0) {
                                    span(className = "text-xs text-base-content/50") { +currentStrings.lessonExcludedSummary(excludedCount) }
                                }
                            }
                            div(className = "overflow-x-auto") {
                                val keptIndices = model.computedSeriesDates.indices.filterNot { model.isLessonExcluded(it) }
                                val allDropInSeries = keptIndices.isNotEmpty() && keptIndices.all { model.lessonDropIn[it] == true }
                                table(className = "table table-xs w-full") {
                                    thead {
                                        tr {
                                            th { +"#" }
                                            th { +currentStrings.tableHeaderDate }
                                            th { +currentStrings.tableHeaderTime }
                                            th {
                                                div(className = "flex items-center gap-2") {
                                                    label(className = "cursor-pointer tooltip tooltip-left") {
                                                        attribute("data-tip", currentStrings.lessonIndividualBulkTooltip)
                                                        checkBox(value = allDropInSeries, className = "checkbox checkbox-secondary checkbox-xs") {
                                                            onChange { model.setAllDropIn(value) }
                                                        }
                                                    }
                                                    span(className = "tooltip tooltip-left cursor-help whitespace-nowrap") {
                                                        attribute("data-tip", currentStrings.lessonIndividualTooltip)
                                                        +currentStrings.lessonIndividualLabel
                                                        span(className = "icon-[heroicons--question-mark-circle] size-3 text-base-content/40 ml-1")
                                                    }
                                                }
                                            }
                                            th(className = "text-right") { +currentStrings.tableHeaderActions }
                                        }
                                    }
                                    tbody {
                                        var lessonNumber = 0
                                        model.computedSeriesDates.forEachIndexed { i, defaultDate ->
                                            val effectiveDateStr = model.lessonDateOverrides[i] ?: defaultDate.toString()
                                            val isExcluded = model.isLessonExcluded(i)
                                            if (!isExcluded) lessonNumber++
                                            val displayNumber = if (isExcluded) "–" else lessonNumber.toString()
                                            tr(className = if (isExcluded) "opacity-50" else null) {
                                                td(className = "text-base-content/50") { +displayNumber }
                                                td {
                                                    if (isExcluded) {
                                                        div(className = "flex items-center gap-2") {
                                                            span(className = "line-through text-base-content/60") { +effectiveDateStr }
                                                            span(className = "badge badge-ghost badge-xs") { +currentStrings.lessonExcludedBadge }
                                                        }
                                                    } else {
                                                        text(value = effectiveDateStr, type = InputType.Date, className = "input input-xs input-bordered w-36") {
                                                            onInput {
                                                                model.setLessonDateOverride(i, value ?: "", defaultDate.toString())
                                                            }
                                                        }
                                                    }
                                                }
                                                td(className = "text-sm text-base-content/70") {
                                                    if (isExcluded) {
                                                        span(className = "line-through") { +"$startTimeDisplayStr – $endTimeStr" }
                                                    } else {
                                                        +"$startTimeDisplayStr – $endTimeStr"
                                                    }
                                                }
                                                td {
                                                    if (!isExcluded) {
                                                        label(className = "cursor-pointer") {
                                                            checkBox(value = model.lessonDropIn[i] ?: false, className = "checkbox checkbox-secondary checkbox-xs") {
                                                                onChange { model.setLessonDropIn(i, value) }
                                                            }
                                                        }
                                                    }
                                                }
                                                td(className = "text-right") {
                                                    button(className = "btn btn-ghost btn-xs tooltip tooltip-left ${if (isExcluded) "" else "text-error"}") {
                                                        attribute(
                                                            "data-tip",
                                                            if (isExcluded) currentStrings.lessonRestoreTooltip else currentStrings.lessonExcludeTooltip,
                                                        )
                                                        span(
                                                            className = if (isExcluded) {
                                                                "icon-[heroicons--arrow-uturn-left] size-4"
                                                            } else {
                                                                "icon-[heroicons--x-mark] size-4"
                                                            },
                                                        )
                                                        onClick { model.toggleLessonExcluded(i) }
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

            // --- ÚPRAVY PRO TENTO KURZ ---
            if (model.selectedDefinitionId != null) {
                div(className = "card bg-base-100 shadow-sm") {
                    div(className = "card-body") {
                        h2(className = "card-title text-lg mb-2") { +currentStrings.seriesOverrideHeading }
                        p(className = "text-sm text-base-content/60 mb-4") {
                            +currentStrings.seriesOverrideDescription
                        }

                        div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.seriesTitleLabel } }
                                text(value = model.titleOverride, className = "input input-bordered w-full") {
                                    onInput { model.titleOverride = value ?: "" }
                                }
                            }

                            div(className = "form-control w-full md:col-span-2") {
                                label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                                textArea(value = model.descriptionOverride, className = "textarea textarea-bordered h-24 w-full") {
                                    onInput { model.descriptionOverride = value ?: "" }
                                }
                            }

                            OwnerEmailsField(model.ownerEmails) { model.ownerEmails = it }

                            PriceCurrencyField(currentStrings.fullCoursePriceLabel, model.priceOverride) { model.priceOverride = it }

                            PriceCurrencyField(
                                label = currentStrings.lessonPriceLabel,
                                value = model.lessonPriceOverride,
                                hint = currentStrings.lessonPriceHint,
                            ) { model.lessonPriceOverride = it }

                            CapacityField(model.capacityOverride) { model.capacityOverride = it }

                            WaitlistCapacityField(model.waitlistCapacityOverride) { model.waitlistCapacityOverride = it }

                            AllowedPaymentsField(model.allowBankTransfer, model.allowOnSite, { model.allowBankTransfer = it }, { model.allowOnSite = it })

                            ShowAttendeeCountCheckbox(value = model.showAttendeeCount) { model.showAttendeeCount = it }
                            AllowMultipleSeatsCheckbox(value = model.allowMultipleSeats) { model.allowMultipleSeats = it }

                        }
                    }
                }

                // --- CUSTOM FIELDS BUILDER ---
                CustomFieldsBuilderSection(model.customFields) { model.customFields = it }

                // --- UZÁVĚRKA REZERVACÍ ---
                ReservationDeadlineSection(
                    enabled = model.deadlineEnabled,
                    typeIsHours = model.deadlineTypeIsHours,
                    hours = model.deadlineHours,
                    daysBefore = model.deadlineDaysBefore,
                    timeStr = model.deadlineTimeStr,
                    message = model.deadlineMessage,
                    onEnabledChange = { model.deadlineEnabled = it },
                    onTypeChange = { model.deadlineTypeIsHours = it },
                    onHoursChange = { model.deadlineHours = it },
                    onDaysBeforeChange = { model.deadlineDaysBefore = it },
                    onTimeStrChange = { model.deadlineTimeStr = it },
                    onMessageChange = { model.deadlineMessage = it },
                )

                // --- ULOŽIT ---
                div(className = "flex justify-end gap-2 mt-4") {
                    button(className = "btn") {
                        onClick { history.back() }
                        +currentStrings.cancel
                    }
                    button(className = "btn btn-outline") {
                        disabled(model.isSubmitting)
                        onClick { model.submit(false) }
                        +currentStrings.saveDraftButton
                    }
                    button(className = "btn btn-secondary") {
                        disabled(model.isSubmitting)
                        onClick { model.submit(true) }
                        if (model.isSubmitting) span(className = "loading loading-spinner loading-sm")
                        span(className = "icon-[heroicons--check] size-5")
                        +currentStrings.createSeriesButton
                    }
                }
            }
        }
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Success,
        onDismiss = { model.dismissToast() },
    )
}
