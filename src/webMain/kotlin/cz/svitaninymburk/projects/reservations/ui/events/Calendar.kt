package cz.svitaninymburk.projects.reservations.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.events.usecase.CalendarEventKind
import cz.svitaninymburk.projects.reservations.ui.events.usecase.MAX_EVENTS_PER_DAY
import cz.svitaninymburk.projects.reservations.ui.events.usecase.calendarEventKind
import cz.svitaninymburk.projects.reservations.ui.events.usecase.calendarGrid
import cz.svitaninymburk.projects.reservations.ui.events.usecase.daysWithEvents
import cz.svitaninymburk.projects.reservations.ui.events.usecase.eventTimeLabel
import cz.svitaninymburk.projects.reservations.ui.events.usecase.eventsOn
import cz.svitaninymburk.projects.reservations.ui.events.usecase.hiddenEventCount
import cz.svitaninymburk.projects.reservations.ui.events.usecase.monthNameIndex
import cz.svitaninymburk.projects.reservations.ui.events.usecase.weekdayIndex
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Barva akce v kalendáři — lekce kurzu se odlišuje od jednorázové. */
private fun eventChipColors(event: EventInstance): String = when (calendarEventKind(event)) {
    CalendarEventKind.SERIES_LESSON -> "bg-secondary/10 text-secondary border border-secondary/20"
    CalendarEventKind.ONE_OFF -> "bg-primary/10 text-primary border border-primary/20"
}

@Composable
fun IComponent.CalendarView(
    events: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit
) {
    val currentStrings by strings
    val model = remember { buildCalendarModel() }

    val month = model.month
    val grid = remember(month) { calendarGrid(month) }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    div(className = "flex flex-col gap-4 animate-fade-in") {

        div(className = "flex justify-between items-center bg-base-100 p-3 sm:p-4 rounded-xl shadow-sm border border-base-200") {

            button(className = "btn btn-circle btn-ghost min-h-11 w-11") {
                onClick { model.previousMonth() }
                span(className = "icon-[heroicons--chevron-left] size-5")
            }

            div(className = "text-lg sm:text-xl font-bold text-base-content flex items-center gap-2") {
                span(className = "icon-[heroicons--calendar] size-5 sm:size-6 text-primary")
                +"${currentStrings.monthName(monthNameIndex(month))} ${month.year}"
            }

            button(className = "btn btn-circle btn-ghost min-h-11 w-11") {
                onClick { model.nextMonth() }
                span(className = "icon-[heroicons--chevron-right] size-5")
            }
        }

        // Mobile: agenda list view (only days with events)
        div(className = "sm:hidden flex flex-col gap-3") {
            val monthEvents = daysWithEvents(events, month)

            if (monthEvents.isEmpty()) {
                div(className = "bg-base-100 rounded-xl shadow-sm border border-base-200 p-4 text-center text-sm text-base-content/60") {
                    +currentStrings.noEventsFoundForFilter
                }
            } else {
                monthEvents.forEach { (date, dailyEvents) ->
                    val isToday = date == today

                    div(className = "bg-base-100 rounded-xl shadow-sm border border-base-200 p-3 flex flex-col gap-2") {
                        div(className = "flex items-center gap-2 px-1") {
                            span(
                                className = if (isToday) {
                                    "w-8 h-8 flex items-center justify-center bg-primary text-primary-content rounded-full font-bold text-sm shadow-sm"
                                } else {
                                    "w-8 h-8 flex items-center justify-center text-sm font-bold text-base-content/70"
                                }
                            ) {
                                +"${date.day}"
                            }
                            span(className = "text-sm font-semibold text-base-content/70 uppercase tracking-wide") {
                                +currentStrings.shortDayName(weekdayIndex(date))
                            }
                        }

                        div(className = "flex flex-col gap-2") {
                            dailyEvents.forEach { event ->
                                div(
                                    className = "text-sm px-3 py-2 rounded-lg min-h-11 flex items-center gap-2 cursor-pointer transition-transform active:scale-[0.98] " +
                                            eventChipColors(event)
                                ) {
                                    onClick { onEventClick(event) }
                                    span(className = "font-bold shrink-0") { +eventTimeLabel(event.startDateTime) }
                                    span(className = "truncate") { +event.title }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tablet + desktop: month grid view
        div(className = "hidden sm:block bg-base-100 rounded-xl shadow-sm border border-base-200 overflow-hidden") {

            div(className = "grid grid-cols-7 border-b border-r border-base-200 bg-base-200/30") {
                (0..6).forEach { dayIndex ->
                    div(className = "py-3 text-center text-xs font-bold uppercase tracking-wider text-base-content/50") {
                        +currentStrings.shortDayName(dayIndex)
                    }
                }
            }

            div(className = "grid grid-cols-7 auto-rows-fr") {

                repeat(grid.leadingBlanks) {
                    div(className = "min-h-[100px] border-b border-r border-base-200 bg-base-200/10") {}
                }

                (1..grid.daysInMonth).forEach { day ->
                    val currentDate = LocalDate(month.year, month.month, day)
                    val isToday = currentDate == today
                    val dailyEvents = eventsOn(events, currentDate)

                    div(className = "relative min-h-[100px] p-1 sm:p-2 border-b border-r border-base-200 hover:bg-base-200/20 transition-colors group") {

                        div(className = "flex justify-between items-start mb-1") {
                            span(
                                className = if (isToday) {
                                    "w-7 h-7 flex items-center justify-center bg-primary text-primary-content rounded-full font-bold text-sm shadow-sm"
                                } else {
                                    "text-sm font-medium text-base-content/70 px-1"
                                }
                            ) {
                                +"$day"
                            }
                        }

                        div(className = "flex flex-col gap-1") {
                            dailyEvents.take(MAX_EVENTS_PER_DAY).forEach { event ->
                                div(
                                    className = "text-[10px] sm:text-xs truncate px-1.5 py-0.5 rounded cursor-pointer transition-transform hover:scale-105 " +
                                            eventChipColors(event)
                                ) {
                                    onClick { onEventClick(event) }
                                    span(className = "font-bold mr-1") { +eventTimeLabel(event.startDateTime) }
                                    +event.title
                                }
                            }

                            val hidden = hiddenEventCount(dailyEvents.size)
                            if (hidden > 0) {
                                div(className = "text-[10px] text-base-content/40 text-center font-medium") {
                                    +currentStrings.more(hidden)
                                }
                            }
                        }
                    }
                }

                repeat(grid.trailingBlanks) {
                    div(className = "min-h-[100px] border-b border-r border-base-200 bg-base-200/10") {}
                }
            }
        }
    }
}
