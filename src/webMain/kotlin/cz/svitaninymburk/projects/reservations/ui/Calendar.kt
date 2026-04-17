package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.*
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.i18n.strings
import dev.kilua.core.IComponent
import kotlin.time.Clock
import dev.kilua.html.*
import kotlinx.datetime.*

@Composable
fun IComponent.CalendarView(
    events: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit
) {
    val currentStrings by strings

    var viewDate by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.let {
            LocalDate(it.year, it.month, 1)
        })
    }

    // Pomocné výpočty
    val daysInMonth = remember(viewDate) {
        YearMonth(viewDate.year, viewDate.month).numberOfDays
    }
    val startDayOffset = viewDate.dayOfWeek.isoDayNumber - 1

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    div(className = "flex flex-col gap-4 animate-fade-in") {

        div(className = "flex justify-between items-center bg-base-100 p-3 sm:p-4 rounded-xl shadow-sm border border-base-200") {

            button(className = "btn btn-circle btn-ghost min-h-11 w-11") {
                onClick { viewDate = viewDate.minus(1, DateTimeUnit.MONTH) }
                span(className = "icon-[heroicons--chevron-left] size-5")
            }

            div(className = "text-lg sm:text-xl font-bold text-base-content flex items-center gap-2") {
                span(className = "icon-[heroicons--calendar] size-5 sm:size-6 text-primary")
                +"${currentStrings.monthName(viewDate.month.number - 1)} ${viewDate.year}"
            }

            button(className = "btn btn-circle btn-ghost min-h-11 w-11") {
                onClick {
                    viewDate = viewDate.plus(1, DateTimeUnit.MONTH)
                }
                span(className = "icon-[heroicons--chevron-right] size-5")
            }
        }

        // Mobile: agenda list view (only days with events)
        div(className = "sm:hidden flex flex-col gap-3") {
            val monthEvents = (1..daysInMonth).mapNotNull { day ->
                val currentDate = LocalDate(viewDate.year, viewDate.month, day)
                val dailyEvents = events.filter { it.startDateTime.date == currentDate }.sortedBy { it.startDateTime }
                if (dailyEvents.isEmpty()) null else currentDate to dailyEvents
            }

            if (monthEvents.isEmpty()) {
                div(className = "bg-base-100 rounded-xl shadow-sm border border-base-200 p-4 text-center text-sm text-base-content/60") {
                    +currentStrings.noEventsFoundForFilter
                }
            } else {
                monthEvents.forEach { (date, dailyEvents) ->
                    val isToday = date == today
                    val weekdayIdx = date.dayOfWeek.isoDayNumber - 1

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
                                +currentStrings.shortDayName(weekdayIdx)
                            }
                        }

                        div(className = "flex flex-col gap-2") {
                            dailyEvents.forEach { event ->
                                div(
                                    className = "text-sm px-3 py-2 rounded-lg min-h-11 flex items-center gap-2 cursor-pointer transition-all active:scale-[0.98] " +
                                            if (event.seriesId != null) "bg-secondary/10 text-secondary border border-secondary/20"
                                            else "bg-primary/10 text-primary border border-primary/20"
                                ) {
                                    onClick { onEventClick(event) }
                                    span(className = "font-bold shrink-0") {
                                        +"${event.startDateTime.hour}:${event.startDateTime.minute.toString().padStart(2, '0')}"
                                    }
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

                repeat(startDayOffset) {
                    div(className = "min-h-[100px] border-b border-r border-base-200 bg-base-200/10") {}
                }

                (1..daysInMonth).forEach { day ->
                    val currentDate = LocalDate(viewDate.year, viewDate.month, day)
                    val isToday = currentDate == today

                    val dailyEvents = events.filter {
                        it.startDateTime.date == currentDate
                    }.sortedBy { it.startDateTime }

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
                            dailyEvents.take(3).forEach { event ->
                                div(
                                    className = "text-[10px] sm:text-xs truncate px-1.5 py-0.5 rounded cursor-pointer transition-all hover:scale-105 " +
                                            if (event.seriesId != null) "bg-secondary/10 text-secondary border border-secondary/20"
                                            else "bg-primary/10 text-primary border border-primary/20"
                                ) {
                                    onClick { onEventClick(event) }
                                    span(className = "font-bold mr-1") {
                                        +"${event.startDateTime.hour}:${event.startDateTime.minute.toString().padStart(2, '0')}"
                                    }
                                    +event.title
                                }
                            }

                            if (dailyEvents.size > 3) {
                                div(className = "text-[10px] text-base-content/40 text-center font-medium") {
                                    +currentStrings.more(dailyEvents.size - 3)
                                }
                            }
                        }
                    }
                }

                val totalCellsUsed = startDayOffset + daysInMonth
                val remainingCells = (7 - (totalCellsUsed % 7)) % 7
                repeat(remainingCells) {
                    div(className = "min-h-[100px] border-b border-r border-base-200 bg-base-200/10") {}
                }
            }
        }
    }
}
