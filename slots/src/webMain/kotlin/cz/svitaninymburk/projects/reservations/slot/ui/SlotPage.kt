package cz.svitaninymburk.projects.reservations.slot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.PageContext
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.DayAvailability
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotStrings
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.dayHasAvailability
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isStepSelectable
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.maxSeatsFor
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionEnd
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionHours
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionPrice
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionStart
import cz.svitaninymburk.projects.reservations.ui.reservation.DuplicateReservationModal
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.util.Loading
import cz.svitaninymburk.projects.reservations.ui.util.Toast
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import cz.svitaninymburk.projects.reservations.ui.util.formatAmount
import cz.svitaninymburk.projects.reservations.ui.util.totalPriceLabel
import cz.svitaninymburk.projects.reservations.util.hourMinute
import dev.kilua.core.IComponent
import dev.kilua.html.button
import dev.kilua.html.div
import dev.kilua.html.h1
import dev.kilua.html.h2
import dev.kilua.html.main
import dev.kilua.html.p
import dev.kilua.html.span
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

@Composable
fun IComponent.SlotPage(context: PageContext) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    val model = remember { buildSlotPageModel(scope, router) }
    val s = slotStrings

    LaunchedEffect(Unit) { model.load() }

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-3xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {
            when (val state = model.servicesState) {
                SlotServicesState.Loading -> Loading()
                SlotServicesState.Error -> ErrorAlert(s.loadError) { model.load() }
                is SlotServicesState.Loaded -> {
                    val service = model.service
                    when {
                        state.services.isEmpty() -> InfoAlert(s.noServices)
                        service == null -> ServicePicker(state.services) { model.selectService(it) }
                        else -> ServiceBooking(model, service)
                    }
                }
            }
        }

        model.service?.let { SelectionSummary(model, it) }
    }

    ReservationModal(
        target = model.reservationTarget,
        user = context.user,
        initialWalletCode = context.walletCode,
        isSubmitting = model.isSubmitting,
        initialSeats = model.seats,
        seatsLabel = { slotStrings.children(it) },
        onClose = { model.closeReservation() },
        onSubmit = { target, data -> model.submitReservation(target, data, context.user?.id) },
    )

    model.duplicatePrompt.pending?.let { pending ->
        DuplicateReservationModal(
            pending = pending,
            isLoading = model.isSubmitting,
            onConfirm = { model.confirmDuplicate() },
            onDismiss = { model.dismissDuplicate() },
        )
    }

    Toast(
        message = model.toast?.message,
        type = model.toast?.type ?: ToastType.Error,
        onDismiss = { model.dismissToast() },
    )
}

private const val CARD = "bg-base-100 rounded-xl shadow-sm border border-base-200"

@Composable
private fun IComponent.ErrorAlert(message: String, onRetry: () -> Unit) {
    div(className = "alert alert-error") {
        span(className = "icon-[heroicons--exclamation-circle] size-6")
        span { +message }
        button(className = "btn btn-sm min-h-11") {
            onClick { onRetry() }
            +slotStrings.retry
        }
    }
}

@Composable
private fun IComponent.InfoAlert(message: String) {
    div(className = "alert bg-base-100 shadow-sm") {
        span(className = "icon-[heroicons--information-circle] size-6 text-info")
        span { +message }
    }
}

@Composable
private fun IComponent.ServicePicker(services: List<SlotService>, onSelect: (SlotService) -> Unit) {
    h1(className = "text-2xl sm:text-3xl font-bold text-base-content") { +slotStrings.chooseService }
    div(className = "flex flex-col gap-3") {
        services.forEach { service ->
            button(className = "$CARD p-4 sm:p-5 text-left hover:shadow-md transition-shadow flex items-center gap-3 min-h-11") {
                onClick { onSelect(service) }
                span(className = "icon-[heroicons--clock] size-6 text-primary shrink-0")
                div(className = "flex flex-col gap-1 min-w-0 flex-1") {
                    span(className = "font-bold text-base sm:text-lg") { +service.title }
                    if (service.description.isNotBlank()) {
                        span(className = "text-sm text-base-content/60 line-clamp-2") { +service.description }
                    }
                }
                span(className = "icon-[heroicons--chevron-right] size-5 text-base-content/40 shrink-0")
            }
        }
    }
}

@Composable
private fun IComponent.ServiceBooking(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val currentStrings by strings

    div(className = "flex flex-col gap-2") {
        if (model.canChangeService) {
            button(className = "btn btn-ghost btn-sm self-start gap-2 min-h-11") {
                onClick { model.backToServices() }
                span(className = "icon-[heroicons--arrow-left] size-5")
                +s.chooseService
            }
        }
        div(className = "flex items-center gap-3") {
            span(className = "icon-[heroicons--clock] size-7 text-primary")
            h1(className = "text-2xl sm:text-3xl font-bold text-base-content") { +service.title }
        }
        if (service.description.isNotBlank()) {
            p(className = "text-base-content/70 whitespace-pre-line") { +service.description }
        }
        p(className = "text-sm text-base-content/60") {
            +s.pricePerHour(formatAmount(service.pricePerHour, currentStrings))
        }
    }

    when (val state = model.daysState) {
        SlotDaysState.Loading -> div(className = "flex justify-center py-8") {
            span(className = "loading loading-spinner loading-lg text-primary")
        }
        SlotDaysState.Error -> ErrorAlert(s.loadError) { model.retryDays() }
        is SlotDaysState.Loaded -> if (state.days.isEmpty()) {
            InfoAlert(s.noDays)
        } else {
            DayStrip(model, service, state.days)
            SeatsStepper(model, service)
            TimeAxis(model, service)
        }
    }
}

@Composable
private fun IComponent.StepHeading(number: Int, title: String) {
    h2(className = "text-sm font-bold uppercase tracking-wider text-base-content/60 flex items-center gap-2") {
        span(className = "badge badge-primary badge-sm") { +"$number" }
        +title
    }
}

/** Pás dní v horizontu. Server posílá jen dny, ve kterých jde ještě něco rezervovat. */
@Composable
private fun IComponent.DayStrip(model: SlotPageModel, service: SlotService, days: List<DayAvailability>) {
    val s = slotStrings
    val currentStrings by strings
    div(className = "flex flex-col gap-2") {
        StepHeading(1, s.dayStep)
        div(className = "flex gap-2 overflow-x-auto pb-2 -mx-3 px-3 sm:mx-0 sm:px-0") {
            days.forEach { day ->
                val isSelected = day.date == model.selectedDate
                val hasRoom = dayHasAvailability(day, model.seats, service.minDuration)
                val tone = when {
                    isSelected -> "bg-primary text-primary-content border-primary"
                    else -> "bg-base-100 border-base-200 hover:border-primary/40"
                }
                button(className = "shrink-0 w-20 min-h-11 rounded-xl border shadow-sm px-2 py-2 flex flex-col items-center gap-0.5 $tone") {
                    attribute("aria-pressed", isSelected.toString())
                    onClick { model.selectDay(day.date) }
                    span(className = "text-xs uppercase font-semibold opacity-80") {
                        +currentStrings.shortDayName(day.date.dayOfWeek.isoDayNumber - 1)
                    }
                    span(className = "font-bold") { +"${day.date.day}. ${day.date.month.number}." }
                    span(className = "flex items-center gap-1 text-xs") {
                        if (hasRoom) {
                            span(className = "size-2 rounded-full ${if (isSelected) "bg-primary-content" else "bg-success"}")
                            +s.dayFree
                        } else {
                            span(className = "size-2 rounded-full ${if (isSelected) "bg-primary-content/60" else "bg-error"}")
                            +s.dayFull
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IComponent.SeatsStepper(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val max = maxSeatsFor(service)
    div(className = "flex flex-col gap-2") {
        StepHeading(2, s.seatsStep)
        div(className = "$CARD p-3 flex items-center justify-between gap-3") {
            button(className = "btn btn-circle btn-ghost min-h-11 min-w-11") {
                attribute("aria-label", s.decreaseSeats)
                disabled(model.seats <= 1)
                onClick { model.setSeats(model.seats - 1) }
                span(className = "icon-[heroicons--minus] size-5")
            }
            span(className = "text-lg font-semibold") {
                attribute("aria-live", "polite")
                +s.children(model.seats)
            }
            button(className = "btn btn-circle btn-ghost min-h-11 min-w-11") {
                attribute("aria-label", s.increaseSeats)
                disabled(model.seats >= max)
                onClick { model.setSeats(model.seats + 1) }
                span(className = "icon-[heroicons--plus] size-5")
            }
        }
    }
}

/**
 * Svislá osa kroků vybraného dne. Řádek = krok; pruh ukazuje obsazenost, text volná
 * místa. Mezi okny (mezera v čase) je oddělovač, přes který výběr nejde.
 */
@Composable
private fun IComponent.TimeAxis(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val segments = model.segments
    val selection = model.selection
    div(className = "flex flex-col gap-2") {
        StepHeading(3, s.timeStep)
        if (segments.isEmpty()) {
            InfoAlert(s.noStepsThatDay)
        } else {
            p(className = "text-sm text-base-content/60") { +s.timeHint }
        }
        if (segments.isNotEmpty()) div(className = "$CARD p-2 flex flex-col gap-1") {
            segments.forEachIndexed { index, segment ->
                if (index > 0 && segments[index - 1].end != segment.start) {
                    div(className = "border-t border-dashed border-base-300 my-1")
                }
                val isSelected = selection != null && index in selection.first..selection.last
                val selectable = isStepSelectable(segment, model.seats)
                val tone = when {
                    isSelected -> "bg-primary/10 text-primary"
                    selectable -> "hover:bg-base-200"
                    else -> "opacity-50"
                }
                val occupied = segment.capacity - segment.free
                val occupiedPercent = if (segment.capacity > 0) occupied * 100 / segment.capacity else 100
                button(className = "w-full min-h-11 px-3 rounded-lg flex items-center gap-3 text-left transition-colors $tone") {
                    attribute("aria-pressed", isSelected.toString())
                    onClick { model.tapStep(index) }
                    span(className = "w-12 font-medium tabular-nums") { +segment.start.time.hourMinute }
                    div(className = "flex-1 h-2 rounded-full bg-base-200 overflow-hidden") {
                        attribute("aria-hidden", "true")
                        div(className = "h-full bg-primary/50") {
                            attribute("style", "width: $occupiedPercent%")
                        }
                    }
                    span(className = "w-20 text-right text-sm tabular-nums") {
                        +if (segment.free > 0) s.freeSeats(segment.free) else s.stepFull
                    }
                }
            }
        }
        model.selectionError?.let { error ->
            div(className = "alert alert-warning py-2 text-sm") {
                attribute("role", "alert")
                span(className = "icon-[heroicons--exclamation-triangle] size-5")
                span { +s.selectionError(error, service.maxDuration) }
            }
        }
        if (selection != null && !model.canContinue) {
            p(className = "text-sm text-base-content/60") { +s.minDurationHint(service.minDuration) }
        }
    }
}

/**
 * Souhrn výběru přilepený dole a tlačítko do rezervačního formuláře. `sticky`, ne `fixed`:
 * drží se spodku okna jen v rámci stránky, takže nepřekryje patičku a obsah pod ním nezmizí.
 */
@Composable
private fun IComponent.SelectionSummary(model: SlotPageModel, service: SlotService) {
    val selection = model.selection ?: return
    val segments = model.segments
    if (segments.isEmpty()) return
    val s = slotStrings
    val currentStrings by strings
    val hours = selectionHours(selection, segments)
    val price = selectionPrice(service, model.seats, hours)

    div(className = "sticky bottom-0 z-40 bg-base-100 border-t border-base-200 shadow-lg") {
        div(className = "max-w-3xl mx-auto px-3 sm:px-4 py-3 flex items-center gap-3") {
            div(className = "flex-1 min-w-0 text-sm") {
                div(className = "font-semibold truncate") {
                    +currentStrings.slotInterval(selectionStart(selection, segments), selectionEnd(selection, segments))
                }
                div(className = "text-base-content/60 truncate") {
                    +"${s.children(model.seats)} · ${s.hours(hours)} · "
                    span(className = "font-semibold text-primary") { +totalPriceLabel(price, currentStrings) }
                }
            }
            button(className = "btn btn-primary min-h-11 px-6") {
                disabled(!model.canContinue)
                onClick { model.openReservation() }
                +s.continueButton
            }
        }
    }
}
