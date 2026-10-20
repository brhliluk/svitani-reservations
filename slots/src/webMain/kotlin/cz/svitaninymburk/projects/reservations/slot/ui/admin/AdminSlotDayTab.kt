package cz.svitaninymburk.projects.reservations.slot.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.SlotReservationRow
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.adminReservationLink
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.dayChildrenTotal
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.occupiedSeats
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.reservationTimeLabel
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotAdminStrings
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotStrings
import cz.svitaninymburk.projects.reservations.ui.reservation.CustomFieldsDisplay
import cz.svitaninymburk.projects.reservations.ui.util.StatusBadge
import cz.svitaninymburk.projects.reservations.ui.util.reservationStatusBadge
import cz.svitaninymburk.projects.reservations.util.hourMinute
import dev.kilua.core.IComponent
import dev.kilua.form.InputType
import dev.kilua.form.text.text
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h2
import dev.kilua.html.span
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import web.window.window

/**
 * Denní přehled: osa kroků s obsazeností a pod ní rezervace podle začátku. Verze pro tisk
 * je tatáž stránka — admin layout skrývá menu (`print:hidden`) a tady se skryje ovládání.
 */
@Composable
internal fun IComponent.DayTab(model: SlotDayModel, service: SlotService) {
    val s = slotAdminStrings
    val currentStrings by strings

    div(className = "flex flex-col gap-4") {
        div(className = "flex flex-wrap items-center gap-2 print:hidden") {
            button(className = "btn btn-square btn-ghost") {
                attribute("aria-label", s.previousDay)
                onClick { model.shift(-1) }
                span(className = "icon-[heroicons--chevron-left] size-5")
            }
            text(value = model.date.toString(), type = InputType.Date, className = "input input-bordered w-44") {
                onChange {
                    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { model.goTo(it) }
                }
            }
            button(className = "btn btn-square btn-ghost") {
                attribute("aria-label", s.nextDay)
                onClick { model.shift(1) }
                span(className = "icon-[heroicons--chevron-right] size-5")
            }
            button(className = "btn btn-ghost btn-sm") {
                onClick { model.goToToday() }
                +s.today
            }
            button(className = "btn btn-outline btn-sm gap-2 ml-auto") {
                onClick { window.print() }
                span(className = "icon-[heroicons--printer] size-4")
                +s.print
            }
        }

        // V tisku chybí hlavička stránky s výběrem dne, proto datum zvlášť.
        h2(className = "text-xl font-bold") {
            +"${currentStrings.dayName(model.date.dayOfWeek.isoDayNumber - 1)} ${model.date.day}. ${model.date.month.number}. ${model.date.year}"
        }

        when (val state = model.state) {
            LoadState.Loading -> Spinner()
            is LoadState.Failed -> LoadFailed(state.message) { model.load() }
            is LoadState.Loaded -> {
                val overview = state.value
                if (overview.segments.isEmpty()) {
                    div(className = "alert bg-base-100 shadow-sm") {
                        span(className = "icon-[heroicons--moon] size-6 text-base-content/60")
                        span { +s.closedThatDay }
                    }
                } else {
                    div(className = "$ADMIN_CARD p-3 flex flex-col gap-1") {
                        overview.segments.forEachIndexed { index, segment ->
                            if (index > 0 && overview.segments[index - 1].end != segment.start) {
                                div(className = "border-t border-dashed border-base-300 my-1")
                            }
                            val occupied = occupiedSeats(segment)
                            val percent = if (segment.capacity > 0) occupied * 100 / segment.capacity else 0
                            div(className = "flex items-center gap-3 min-h-9 px-2") {
                                span(className = "w-12 font-medium tabular-nums") { +segment.start.time.hourMinute }
                                div(className = "flex-1 h-2 rounded-full bg-base-200 overflow-hidden") {
                                    attribute("aria-hidden", "true")
                                    div(className = "h-full bg-primary/60") { attribute("style", "width: $percent%") }
                                }
                                span(className = "w-14 text-right text-sm tabular-nums") { +s.occupancy(occupied, segment.capacity) }
                            }
                        }
                    }
                }

                div(className = "flex items-baseline justify-between gap-2 mt-2") {
                    h2(className = "text-lg font-bold") { +s.reservationsHeading }
                    if (overview.reservations.isNotEmpty()) {
                        span(className = "text-sm text-base-content/60") {
                            +s.dayTotal(overview.reservations.size, slotStrings.children(dayChildrenTotal(overview.reservations)))
                        }
                    }
                }
                if (overview.reservations.isEmpty()) {
                    div(className = "text-base-content/60") { +s.noReservationsThatDay }
                } else {
                    div(className = "flex flex-col gap-2") {
                        overview.reservations.forEach { row ->
                            ReservationRow(row, service)
                        }
                    }
                }
            }
        }
    }
}

/** „9:00–11:30 · Nováková · 2 děti · zaplaceno“; proklik do admin přehledu rezervací. */
@Composable
private fun IComponent.ReservationRow(row: SlotReservationRow, service: SlotService) {
    val router = Router.current
    div(className = "$ADMIN_CARD p-4 flex flex-col gap-2 break-inside-avoid") {
        div(className = "flex flex-wrap items-center gap-x-3 gap-y-1") {
            span(className = "font-bold tabular-nums") { +reservationTimeLabel(row) }
            button(className = "link link-hover font-semibold print:no-underline") {
                onClick { router.navigate(adminReservationLink(row)) }
                +row.contactName
            }
            span(className = "text-base-content/70") { +slotStrings.children(row.seatCount) }
            StatusBadge(reservationStatusBadge(row.status, row.paymentType, row.totalPrice))
            row.contactPhone?.let { span(className = "text-sm text-base-content/60 ml-auto") { +it } }
        }
        CustomFieldsDisplay(service.customFields, row.customValues)
    }
}
