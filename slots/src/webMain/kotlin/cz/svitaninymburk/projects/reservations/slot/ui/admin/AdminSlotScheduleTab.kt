package cz.svitaninymburk.projects.reservations.slot.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.PeriodDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.WindowDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.reservationTimeLabel
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotStrings
import cz.svitaninymburk.projects.reservations.ui.util.ConfirmModal
import cz.svitaninymburk.projects.reservations.util.humanReadable
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.check.checkBox
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h2
import dev.kilua.html.label
import dev.kilua.html.li
import dev.kilua.html.p
import dev.kilua.html.span
import dev.kilua.html.ul
import kotlinx.datetime.DayOfWeek

@Composable
internal fun IComponent.ScheduleTab(model: SlotScheduleModel) {
    val s = slotAdminStrings
    val currentStrings by strings
    val draft = model.draft

    div(className = "flex flex-col gap-6") {
        // --- Období ---
        div(className = "flex flex-col gap-3") {
            h2(className = "text-lg font-bold") { +s.periodsHeading }
            p(className = "text-sm text-base-content/60") { +s.periodsHint }
            if (draft.periods.isEmpty()) {
                div(className = "text-base-content/60") { +s.noPeriods }
            }
            draft.periods.forEachIndexed { index, period ->
                PeriodCard(period, onEdit = { changed -> model.edit { it.withPeriod(index, changed) } }) {
                    model.edit { it.withoutPeriod(index) }
                }
            }
            button(className = "btn btn-outline btn-sm gap-2 self-start") {
                onClick { model.edit { it.withAddedPeriod(browserToday()) } }
                span(className = "icon-[heroicons--plus] size-4")
                +s.addPeriod
            }
        }

        // --- Výjimky ---
        div(className = "flex flex-col gap-3") {
            h2(className = "text-lg font-bold") { +s.exceptionsHeading }
            p(className = "text-sm text-base-content/60") { +s.exceptionsHint }
            div(className = "$ADMIN_CARD p-4 flex flex-col gap-3") {
                if (draft.exceptions.isEmpty()) {
                    div(className = "text-base-content/60") { +s.noExceptions }
                }
                draft.exceptions.forEachIndexed { index, exception ->
                    div(className = "flex flex-wrap items-center gap-3") {
                        text(value = exception.date, type = InputType.Date, className = "input input-bordered input-sm w-40") {
                            attribute("aria-label", s.exceptionDate)
                            onChange { model.edit { it.withException(index, exception.copy(date = value.orEmpty())) } }
                        }
                        label(className = "flex items-center gap-2 cursor-pointer") {
                            checkBox(value = exception.closed, className = "checkbox checkbox-sm") {
                                onChange { model.edit { it.withException(index, exception.copy(closed = value)) } }
                            }
                            span(className = "label-text") { +s.exceptionClosed }
                        }
                        if (!exception.closed) {
                            TimeInputs(WindowDraft(exception.from, exception.to)) { window ->
                                model.edit { it.withException(index, exception.copy(from = window.from, to = window.to)) }
                            }
                        }
                        button(className = "btn btn-ghost btn-sm btn-circle text-error ml-auto") {
                            attribute("aria-label", s.removeException)
                            onClick { model.edit { it.withoutException(index) } }
                            span(className = "icon-[heroicons--x-mark] size-4")
                        }
                    }
                }
                button(className = "btn btn-outline btn-sm gap-2 self-start") {
                    onClick { model.edit { it.withAddedException(browserToday()) } }
                    span(className = "icon-[heroicons--plus] size-4")
                    +s.addException
                }
            }
        }

        if (model.problems.isNotEmpty()) {
            div(className = "alert alert-error") {
                attribute("role", "alert")
                span(className = "icon-[heroicons--exclamation-circle] size-6")
                div { model.problems.forEach { p { +s.scheduleProblem(it) } } }
            }
        }

        button(className = "btn btn-primary self-start gap-2") {
            disabled(model.isSaving)
            onClick { model.save() }
            if (model.isSaving) span(className = "loading loading-spinner loading-sm")
            +s.saveSchedule
        }
    }

    if (model.affected.isNotEmpty()) {
        ConfirmModal(
            title = s.affectedTitle,
            confirmLabel = s.saveAnyway,
            dismissLabel = currentStrings.cancel,
            isLoading = model.isSaving,
            confirmClassName = "btn-warning",
            onConfirm = { model.confirmAffected() },
            onDismiss = { model.dismissAffected() },
        ) {
            p(className = "py-2") { +s.affectedText(model.affected.size) }
            ul(className = "list-disc pl-5 text-sm max-h-64 overflow-y-auto") {
                model.affected.forEach { row ->
                    li { +"${row.start.date.humanReadable} ${reservationTimeLabel(row)} · ${row.contactName} · ${slotStrings.children(row.seatCount)}" }
                }
            }
        }
    }
}

@Composable
private fun IComponent.PeriodCard(period: PeriodDraft, onEdit: (PeriodDraft) -> Unit, onRemove: () -> Unit) {
    val s = slotAdminStrings
    val currentStrings by strings
    div(className = "$ADMIN_CARD p-4 flex flex-col gap-3") {
        // DaisyUI 5 nemá `form-control` a `.label` je inline — popisek nad pole dává až flex-col.
        // Na mobilu jsou pole pod sebou, od `sm` vedle sebe.
        div(className = "flex flex-col sm:flex-row sm:items-end gap-3") {
            div(className = "grid grid-cols-1 sm:grid-cols-2 gap-3 w-full sm:max-w-md") {
                label(className = "form-control w-full flex flex-col gap-1") {
                    div(className = "label") { span(className = "label-text font-medium") { +s.validFrom } }
                    text(value = period.validFrom, type = InputType.Date, className = "input input-bordered input-sm w-full") {
                        onChange { onEdit(period.copy(validFrom = value.orEmpty())) }
                    }
                }
                label(className = "form-control w-full flex flex-col gap-1") {
                    div(className = "label") { span(className = "label-text font-medium") { +s.validTo } }
                    text(value = period.validTo, type = InputType.Date, className = "input input-bordered input-sm w-full") {
                        onChange { onEdit(period.copy(validTo = value.orEmpty())) }
                    }
                }
            }
            button(className = "btn btn-ghost btn-sm text-error gap-1 self-start sm:self-auto sm:ml-auto") {
                onClick { onRemove() }
                span(className = "icon-[heroicons--trash] size-4")
                +s.removePeriod
            }
        }
        div(className = "flex flex-col divide-y divide-base-200") {
            DayOfWeek.entries.forEach { day ->
                div(className = "flex flex-wrap items-center gap-3 py-2") {
                    span(className = "w-24 font-medium") { +currentStrings.dayName(day.ordinal) }
                    val windows = period.windowsOf(day)
                    if (windows.isEmpty()) {
                        span(className = "text-sm text-base-content/50") { +s.closedDay }
                    }
                    windows.forEachIndexed { index, window ->
                        div(className = "flex items-center gap-1") {
                            TimeInputs(window) { onEdit(period.withWindow(day, index, it)) }
                            button(className = "btn btn-ghost btn-xs btn-circle text-error") {
                                attribute("aria-label", s.removeWindow)
                                onClick { onEdit(period.withoutWindow(day, index)) }
                                span(className = "icon-[heroicons--x-mark] size-4")
                            }
                        }
                    }
                    button(className = "btn btn-ghost btn-xs gap-1") {
                        attribute("aria-label", s.addWindow)
                        onClick { onEdit(period.withAddedWindow(day)) }
                        span(className = "icon-[heroicons--plus] size-4")
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.TimeInputs(window: WindowDraft, onEdit: (WindowDraft) -> Unit) {
    div(className = "flex items-center gap-1") {
        text(value = window.from, type = InputType.Time, className = "input input-bordered input-sm w-28") {
            onChange { onEdit(window.copy(from = value.orEmpty())) }
        }
        span { +"–" }
        text(value = window.to, type = InputType.Time, className = "input input-bordered input-sm w-28") {
            onChange { onEdit(window.copy(to = value.orEmpty())) }
        }
    }
}
