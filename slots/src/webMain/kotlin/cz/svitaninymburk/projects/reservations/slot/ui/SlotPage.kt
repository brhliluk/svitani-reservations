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
import cz.svitaninymburk.projects.reservations.slot.SlotSegment
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.ui.i18n.slotStrings
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.dayHasAvailability
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.endLimit
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isStartAvailable
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isStepSelectable
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.maxSeatsFor
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotPick
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelection
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.possibleLasts
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionEnd
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionDuration
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionHours
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionPrice
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionStart
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.windows
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
import kotlin.time.Duration

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
            SeatsPicker(model, service)
            DayStrip(model, service, state.days)
            if (model.segments.isEmpty()) {
                InfoAlert(s.noStepsThatDay)
            } else {
                ArrivalStep(model, service)
                DepartureStep(model, service)
            }
        }
    }
}

@Composable
private fun IComponent.StepHeading(number: Int, title: String, trailing: @Composable IComponent.() -> Unit = {}) {
    div(className = "flex items-center justify-between gap-2 min-h-8") {
        h2(className = "text-sm font-bold uppercase tracking-wider text-base-content/60 flex items-center gap-2") {
            span(className = "badge badge-primary badge-sm") { +"$number" }
            +title
        }
        trailing()
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

/**
 * Počet dětí jako jeden řádek nad dny: určuje, které dny a časy jsou volné. Do čtyř dětí
 * na rezervaci volba čísel, nad to stepper, aby se řádek vešel na telefon.
 */
@Composable
private fun IComponent.SeatsPicker(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val max = maxSeatsFor(service)
    div(className = "flex items-center justify-between gap-3") {
        span(className = "font-bold") { +s.seatsStep }
        if (max <= 4) {
            div(className = "join bg-base-100 shadow-sm border border-base-300 rounded-lg") {
                attribute("role", "group")
                attribute("aria-label", s.seatsStep)
                (1..max).forEach { count ->
                    val isSelected = count == model.seats
                    button(className = "join-item btn min-h-11 min-w-11 tabular-nums ${if (isSelected) "btn-primary" else "btn-ghost"}") {
                        attribute("aria-pressed", isSelected.toString())
                        attribute("aria-label", s.children(count))
                        onClick { model.setSeats(count) }
                        +"$count"
                    }
                }
            }
        } else {
            div(className = "flex items-center gap-1 bg-base-100 shadow-sm border border-base-300 rounded-lg") {
                button(className = "btn btn-ghost btn-square min-h-11 min-w-11") {
                    attribute("aria-label", s.decreaseSeats)
                    disabled(model.seats <= 1)
                    onClick { model.setSeats(model.seats - 1) }
                    span(className = "icon-[heroicons--minus] size-5")
                }
                span(className = "font-semibold tabular-nums min-w-16 text-center") {
                    attribute("aria-live", "polite")
                    +s.children(model.seats)
                }
                button(className = "btn btn-ghost btn-square min-h-11 min-w-11") {
                    attribute("aria-label", s.increaseSeats)
                    disabled(model.seats >= max)
                    onClick { model.setSeats(model.seats + 1) }
                    span(className = "icon-[heroicons--plus] size-5")
                }
            }
        }
    }
}

/**
 * Příchod: pás dne s obsazeností a pod ním čipy začátků po oknech. Nabízí se jen začátky,
 * ze kterých jde rezervovat aspoň nejkratší úsek; plné kroky jsou přeškrtnuté.
 */
@Composable
private fun IComponent.ArrivalStep(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val segments = model.segments
    val pick = model.pick
    div(className = "flex flex-col gap-2") {
        StepHeading(2, s.arrivalStep) {
            if (pick != null) {
                button(className = "btn btn-ghost btn-sm text-primary gap-1 min-h-11") {
                    onClick { model.clearSelection() }
                    span(className = "icon-[heroicons--x-mark] size-4")
                    +s.clearSelection
                }
            }
        }
        val selection = model.selection
        DayRibbon(segments, model.seats, highlighted = selection?.let { it.first..it.last } ?: pick?.let { it.start..it.start })
        windows(segments).forEach { window ->
            span(className = "text-xs font-semibold text-base-content/60 tabular-nums") {
                +"${segments[window.first].start.time.hourMinute}\u2013${segments[window.last].end.time.hourMinute}"
            }
            div(className = "grid grid-cols-4 sm:grid-cols-6 gap-2") {
                window.forEach { index ->
                    val segment = segments[index]
                    val isSelected = pick?.start == index
                    val available = isStartAvailable(index, segments, model.seats, service)
                    val isFull = !isStepSelectable(segment, model.seats)
                    val tone = when {
                        isSelected -> "bg-primary text-primary-content border-primary"
                        available -> "bg-base-100 border-base-300 hover:border-primary/50"
                        else -> "bg-base-100 border-base-200 opacity-40 cursor-not-allowed"
                    }
                    button(className = "min-h-11 rounded-lg border px-1 py-1 flex flex-col items-center justify-center leading-tight font-bold tabular-nums transition-colors $tone") {
                        attribute("aria-pressed", isSelected.toString())
                        disabled(!available && !isSelected)
                        onClick { model.tapStart(index) }
                        span(className = if (isFull) "line-through" else "") { +segment.start.time.hourMinute }
                        if (isFull) span(className = "text-xs font-normal") { +s.stepFull }
                    }
                }
            }
        }
        // Bez minima (0) je dost dlouhý každý výběr a nápověda „nejkratší je 0 min“ nedává smysl.
        if (service.minDuration > Duration.ZERO) {
            p(className = "text-sm text-base-content/60") { +s.minDurationHint(service.minDuration) }
        }
    }
}

/** Odchod: jen časy, které z vybraného příchodu jdou, s délkou a větou, proč ne později. */
@Composable
private fun IComponent.DepartureStep(model: SlotPageModel, service: SlotService) {
    val s = slotStrings
    val segments = model.segments
    val pick = model.pick
    div(className = "flex flex-col gap-2") {
        StepHeading(3, s.departureStep)
        if (pick == null) {
            div(className = "border-2 border-dashed border-base-300 rounded-xl p-4 text-center text-sm text-base-content/60") {
                +s.pickArrivalFirst
            }
        } else {
            DepartureChips(model, service, pick)
        }
        // Úsek je zarovnaný na kroky; skutečné časy napíše rodič do poznámky ve formuláři.
        p(className = "text-sm text-base-content/60 flex items-start gap-2") {
            span(className = "icon-[heroicons--pencil-square] size-5 shrink-0")
            span { +s.exactTimeHint }
        }
    }
}

@Composable
private fun IComponent.DepartureChips(model: SlotPageModel, service: SlotService, pick: SlotPick) {
    val s = slotStrings
    val segments = model.segments
    div(className = "grid grid-cols-4 sm:grid-cols-6 gap-2") {
        possibleLasts(pick.start, segments, model.seats, service).forEach { index ->
            val end = segments[index].end
            val isSelected = pick.last == index
            val tone = if (isSelected) {
                "bg-primary text-primary-content border-primary"
            } else {
                "bg-base-100 border-base-300 hover:border-primary/50"
            }
            button(className = "min-h-11 rounded-lg border px-1 py-1 flex flex-col items-center justify-center leading-tight font-bold tabular-nums transition-colors $tone") {
                attribute("aria-pressed", isSelected.toString())
                onClick { model.tapEnd(index) }
                span { +end.time.hourMinute }
                span(className = "text-xs font-normal opacity-80") {
                    +s.duration(selectionDuration(SlotSelection(pick.start, index), segments))
                }
            }
        }
    }
    endLimit(pick.start, segments, model.seats, service)?.let { limit ->
        p(className = "text-sm text-base-content/60 flex items-start gap-2") {
            span(className = "icon-[heroicons--information-circle] size-5 shrink-0")
            span { +s.endLimit(limit) }
        }
    }
}

/**
 * Pás dne: krok = políčko, okna vedle sebe v poměru délky. Zelená = volno pro zvolený
 * počet dětí, šedá = plno, primární barva = vybraný čas. Jen přehled, klikají se čipy pod ním.
 */
@Composable
private fun IComponent.DayRibbon(segments: List<SlotSegment>, seats: Int, highlighted: IntRange?) {
    val s = slotStrings
    div(className = "flex items-start gap-3") {
        attribute("aria-hidden", "true")
        windows(segments).forEach { window ->
            div(className = "flex flex-col gap-1 min-w-0") {
                attribute("style", "flex: ${window.count()} 1 0")
                div(className = "flex gap-0.5") {
                    window.forEach { index ->
                        val tone = when {
                            highlighted != null && index in highlighted -> "bg-primary"
                            isStepSelectable(segments[index], seats) -> "bg-success/40"
                            else -> "bg-base-content/20"
                        }
                        div(className = "flex-1 h-3 rounded-sm $tone")
                    }
                }
                div(className = "flex justify-between text-xs text-base-content/60 tabular-nums") {
                    span { +segments[window.first].start.time.hourMinute }
                    span { +segments[window.last].end.time.hourMinute }
                }
            }
        }
    }
    div(className = "flex flex-wrap gap-x-4 gap-y-1 text-xs text-base-content/60") {
        attribute("aria-hidden", "true")
        LegendItem("bg-success/40", s.legendFree)
        LegendItem("bg-base-content/20", s.legendFull(s.children(seats)))
        LegendItem("bg-primary", s.legendSelected)
    }
}

@Composable
private fun IComponent.LegendItem(swatch: String, label: String) {
    span(className = "flex items-center gap-1.5") {
        span(className = "w-3 h-2.5 rounded-sm $swatch")
        +label
    }
}

/**
 * Souhrn výběru přilepený dole a tlačítko do rezervačního formuláře. `sticky`, ne `fixed`:
 * drží se spodku okna jen v rámci stránky, takže nepřekryje patičku a obsah pod ním nezmizí.
 */
@Composable
private fun IComponent.SelectionSummary(model: SlotPageModel, service: SlotService) {
    val pick = model.pick ?: return
    val segments = model.segments
    if (segments.isEmpty()) return
    val s = slotStrings
    val currentStrings by strings
    val selection = model.selection

    div(className = "sticky bottom-0 z-40 bg-base-100 border-t border-base-200 shadow-lg") {
        div(className = "max-w-3xl mx-auto px-3 sm:px-4 py-3 flex items-center gap-3") {
            div(className = "flex-1 min-w-0 text-sm") {
                if (selection == null) {
                    div(className = "font-semibold truncate") {
                        +s.arrivalSummary(segments[pick.start].start.time.hourMinute)
                    }
                    div(className = "text-base-content/60 truncate") { +s.pickDepartureHint }
                } else {
                    val hours = selectionHours(selection, segments)
                    val price = selectionPrice(service, model.seats, hours)
                    div(className = "font-semibold truncate") {
                        +currentStrings.slotInterval(selectionStart(selection, segments), selectionEnd(selection, segments))
                    }
                    div(className = "text-base-content/60 truncate") {
                        +"${s.children(model.seats)} · ${s.hours(hours)} · "
                        span(className = "font-semibold text-primary") { +totalPriceLabel(price, currentStrings) }
                    }
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
