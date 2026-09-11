package cz.svitaninymburk.projects.reservations.ui.admin.events.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowMultipleSeatsCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.AllowedPaymentsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.CapacityField
import cz.svitaninymburk.projects.reservations.ui.admin.events.DurationField
import cz.svitaninymburk.projects.reservations.ui.admin.events.OwnerEmailsField
import cz.svitaninymburk.projects.reservations.ui.admin.events.PriceCurrencyField
import cz.svitaninymburk.projects.reservations.ui.admin.events.ShowAttendeeCountCheckbox
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateType
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.select.select
import dev.kilua.form.text.text
import dev.kilua.form.text.textArea
import dev.kilua.html.*
import web.history.history
import web.html.HTMLSelectElement

@Composable
fun IComponent.EventCreateHeader() {
    val currentStrings by strings
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
}

@Composable
fun IComponent.EventTypeSelectorCard(selected: EventCreateType, onSelect: (EventCreateType) -> Unit) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            h2(className = "card-title text-lg mb-4") { +currentStrings.eventTypeHeading }
            div(className = "grid grid-cols-3 gap-3") {
                listOf(
                    Triple(EventCreateType.SINGLE, currentStrings.eventTypeSingle, "icon-[heroicons--calendar] size-6"),
                    Triple(EventCreateType.RECURRING, currentStrings.eventTypeRecurring, "icon-[heroicons--calendar-days] size-6"),
                    Triple(EventCreateType.COURSE, currentStrings.eventTypeCourse, "icon-[heroicons--academic-cap] size-6"),
                ).forEach { (type, label, icon) ->
                    val isSelected = selected == type
                    // Třídy musí být napsané celé — Tailwind si je vytahuje ze zdrojáků,
                    // poskládané z kousků (`border-$accent`) by se do CSS nedostaly.
                    val borderColor = when {
                        !isSelected -> "border-base-300"
                        type == EventCreateType.COURSE -> "border-secondary bg-secondary/5"
                        else -> "border-primary bg-primary/5"
                    }
                    div(className = "card border-2 $borderColor cursor-pointer transition-shadow hover:shadow-sm") {
                        onClick { onSelect(type) }
                        div(className = "card-body items-center text-center py-5 px-3 gap-2") {
                            span(className = icon)
                            span(className = "font-semibold text-sm") { +label }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IComponent.EventBasicInfoCard(model: AdminCreateEventModel) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm") {
        div(className = "card-body") {
            h2(className = "card-title text-lg mb-4") { +currentStrings.basicInfoHeading }
            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {

                div(className = "form-control w-full md:col-span-2") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.eventNameLabel } }
                    text(value = model.title, className = "input input-bordered w-full") {
                        onInput { model.title = value ?: "" }
                    }
                }

                div(className = "form-control w-full md:col-span-2") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.descriptionLabel } }
                    textArea(value = model.description, className = "textarea textarea-bordered h-24 w-full") {
                        onInput { model.description = value ?: "" }
                    }
                }

                OwnerEmailsField(model.ownerEmails) { model.ownerEmails = it }

                PriceCurrencyField(label = currentStrings.priceLabel, value = model.price) { model.price = it }

                CapacityField(model.capacity) { model.capacity = it }

                DurationField(
                    label = currentStrings.durationLabel,
                    hours = model.durationHours,
                    minutes = model.durationMinutes,
                    onHoursChange = { model.durationHours = it },
                    onMinutesChange = { model.durationMinutes = it },
                )

                AllowedPaymentsField(
                    bankTransfer = model.allowBankTransfer,
                    onSite = model.allowOnSite,
                    onBankTransferChange = { model.allowBankTransfer = it },
                    onOnSiteChange = { model.allowOnSite = it },
                )

                ShowAttendeeCountCheckbox(model.showAttendeeCount) { model.showAttendeeCount = it }
                AllowMultipleSeatsCheckbox(model.allowMultipleSeats) { model.allowMultipleSeats = it }
            }
        }
    }
}

/** Datum a čas začátku — sdílí je jednorázová i opakovaná akce. */
@Composable
private fun IComponent.StartDateTimeFields(model: AdminCreateEventModel) {
    val currentStrings by strings
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-bold") { +currentStrings.dateLabelField } }
        text(value = model.startDate, type = InputType.Date, className = "input input-bordered w-full") {
            onInput { model.startDate = value ?: "" }
        }
    }
    div(className = "form-control w-full") {
        label(className = "label") { span(className = "label-text font-bold") { +currentStrings.timeLabelField } }
        text(value = model.startTime, type = InputType.Time, className = "input input-bordered w-full") {
            onInput { model.startTime = value ?: "" }
        }
    }
}

@Composable
fun IComponent.SingleEventScheduleCard(model: AdminCreateEventModel) {
    div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
        div(className = "card-body") {
            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                StartDateTimeFields(model)
            }
        }
    }
}

@Composable
fun IComponent.RecurringEventScheduleCard(model: AdminCreateEventModel) {
    val currentStrings by strings
    div(className = "card bg-base-100 shadow-sm border-t-4 border-primary") {
        div(className = "card-body") {
            div(className = "grid grid-cols-1 md:grid-cols-2 gap-4") {
                StartDateTimeFields(model)
                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceTypeLabel } }
                    select(className = "select select-bordered w-full") {
                        option(value = "DAILY", label = currentStrings.recurrenceDaily) { if (model.recurrenceType == RecurrenceType.DAILY) selected(true) }
                        option(value = "WEEKLY", label = currentStrings.recurrenceWeekly) { if (model.recurrenceType == RecurrenceType.WEEKLY) selected(true) }
                        option(value = "MONTHLY", label = currentStrings.recurrenceMonthly) { if (model.recurrenceType == RecurrenceType.MONTHLY) selected(true) }
                        onChange { event ->
                            val v = (event.target as? HTMLSelectElement)?.value ?: "WEEKLY"
                            model.recurrenceType = RecurrenceType.valueOf(v)
                        }
                    }
                }
                div(className = "form-control w-full") {
                    label(className = "label") { span(className = "label-text font-medium") { +currentStrings.recurrenceEndLabel } }
                    text(value = model.recurrenceEndDateStr, type = InputType.Date, className = "input input-bordered w-full") {
                        onInput { model.recurrenceEndDateStr = value ?: "" }
                    }
                }
            }

            if (model.startDate.isNotBlank() && model.startTime.isNotBlank() && model.recurrenceEndDateStr.isNotBlank()) {
                RecurrencePreview(model)
            }
        }
    }
}

@Composable
private fun IComponent.RecurrencePreview(model: AdminCreateEventModel) {
    val currentStrings by strings
    val dates = model.previewDates
    div(className = "mt-4 p-4 bg-base-200/50 rounded-lg border border-primary/20") {
        div(className = "flex items-center gap-2 mb-3") {
            span(className = "icon-[heroicons--calendar-days] size-5 text-primary")
            span(className = "font-medium text-sm") { +currentStrings.recurrencePreviewHeading(dates.size) }
        }
        if (dates.isEmpty()) {
            p(className = "text-warning text-sm") { +currentStrings.recurrencePreviewError }
        } else {
            div(className = "flex flex-wrap gap-2") {
                dates.forEach { dt ->
                    span(className = "badge badge-outline badge-primary badge-sm") { +dt.humanReadable }
                }
            }
        }
    }
}

@Composable
fun IComponent.EventCreateActions(model: AdminCreateEventModel) {
    val currentStrings by strings
    div(className = "flex justify-end gap-2 mt-4") {
        button(className = "btn") {
            onClick { history.back() }
            +currentStrings.cancel
        }
        button(className = "btn btn-outline") {
            onClick { model.submit(isPublished = false) }
            +currentStrings.saveDraftButton
        }
        val btnClass = if (model.eventType == EventCreateType.COURSE) "btn btn-secondary" else "btn btn-primary"
        button(className = btnClass) {
            onClick { model.submit(isPublished = true) }
            span(className = "icon-[heroicons--check] size-5")
            +currentStrings.createEventButton
        }
    }
}
