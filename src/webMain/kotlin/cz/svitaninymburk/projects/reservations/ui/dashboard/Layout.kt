package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.CalendarView
import cz.svitaninymburk.projects.reservations.ui.DefinitionCard
import cz.svitaninymburk.projects.reservations.ui.Event
import cz.svitaninymburk.projects.reservations.ui.SeriesCard
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlin.uuid.Uuid

enum class DashboardTab { SCHEDULE, CATALOG }
enum class ViewMode { LIST, CALENDAR }

@Composable
fun IComponent.DashboardLayout(
    user: User?,
    events: List<EventInstance>,
    series: List<EventSeries>,
    definitions: List<EventDefinition>,
    initialFilterId: String? = null,
    onSubmitReservation: (ReservationTarget, ReservationFormData) -> Unit,
) {
    val currentStrings by strings

    var reservationTarget by remember { mutableStateOf<ReservationTarget?>(null) }
    var activeTab by remember { mutableStateOf(DashboardTab.SCHEDULE) }
    var selectedDefinitionId by remember { mutableStateOf(initialFilterId?.let { Uuid.parse(it) }) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    val filteredEvents = remember(events, selectedDefinitionId) {
        if (selectedDefinitionId == null) events
        else events.filter { it.definitionId == selectedDefinitionId }
    }

    val filteredSeries = remember(series, selectedDefinitionId) {
        if (selectedDefinitionId == null) series
        else series.filter { it.definitionId == selectedDefinitionId }
    }

    val activeFilterName = remember(selectedDefinitionId, definitions) {
        definitions.find { it.id == selectedDefinitionId }?.title
    }

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-5xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {

            div(className = "flex justify-center") {
                div(className = "tabs tabs-boxed bg-base-100 p-1 rounded-full shadow-sm w-full sm:w-auto") {

                    a(className = "tab rounded-full min-h-11 px-4 text-sm sm:text-base flex-1 sm:flex-none transition-all duration-300 ${if (activeTab == DashboardTab.SCHEDULE) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                        onClick { activeTab = DashboardTab.SCHEDULE }
                        span(className = "icon-[heroicons--calendar-days] size-5 mr-2")
                        +currentStrings.schedule
                    }

                    a(className = "tab rounded-full min-h-11 px-4 text-sm sm:text-base flex-1 sm:flex-none transition-all duration-300 ${if (activeTab == DashboardTab.CATALOG) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                        onClick {
                            activeTab = DashboardTab.CATALOG
                            selectedDefinitionId = null
                        }
                        span(className = "icon-[heroicons--swatch] size-5 mr-2")
                        +currentStrings.catalog
                    }
                }
            }

            if (activeTab == DashboardTab.CATALOG) {
                div(className = "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6 animate-fade-in") {
                    definitions.forEach { def ->
                        DefinitionCard(def) {
                            selectedDefinitionId = def.id
                            activeTab = DashboardTab.SCHEDULE
                        }
                    }
                }
            } else {
                div(className = "flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3 sm:gap-4 mb-2") {
                    if (activeFilterName != null) {
                        div(className = "badge badge-primary gap-2 px-4 py-2 h-auto min-h-11 whitespace-normal text-left w-full sm:w-auto sm:max-w-md justify-start sm:justify-center cursor-pointer hover:badge-error hover:text-white transition-colors tooltip tooltip-bottom") {
                            attribute("data-tip", currentStrings.clearFilterTooltip)
                            onClick { selectedDefinitionId = null }
                            span(className = "icon-[heroicons--funnel] size-4 shrink-0")
                            span(className = "flex-1") { +currentStrings.filterIsActive(activeFilterName) }
                            span(className = "icon-[heroicons--x-mark] size-4 shrink-0 ml-1")
                        }
                    } else {
                        div(className = "text-xl font-bold text-base-content") {
                            +currentStrings.allEvents
                        }
                    }

                    div(className = "join bg-base-100 shadow-sm border border-base-300 rounded-lg w-full sm:w-auto") {
                        button(className = "join-item btn flex-1 sm:flex-none min-h-11 ${if(viewMode == ViewMode.LIST) "btn-active" else "btn-ghost"}") {
                            onClick { viewMode = ViewMode.LIST }
                            span(className = "icon-[heroicons--list-bullet] size-5")
                        }
                        button(className = "join-item btn flex-1 sm:flex-none min-h-11 ${if(viewMode == ViewMode.CALENDAR) "btn-active" else "btn-ghost"}") {
                            onClick { viewMode = ViewMode.CALENDAR }
                            span(className = "icon-[heroicons--calendar] size-5")
                        }
                    }
                }

                // Samotný seznam (List View)
                if (viewMode == ViewMode.LIST) {
                    div(className = "flex flex-col gap-6 animate-fade-in") {

                        if (filteredEvents.isEmpty() && filteredSeries.isEmpty()) {
                            div(className = "alert bg-base-100 shadow-sm") {
                                span(className = "icon-[heroicons--information-circle] size-6 text-info")
                                +currentStrings.noEventsFoundForFilter
                            }
                        } else {
                            if (filteredSeries.isNotEmpty()) {
                                div(className = "flex flex-col gap-3 sm:gap-4") {
                                    div(className = "text-sm font-semibold text-base-content/50 uppercase tracking-wider px-1") {
                                        +currentStrings.openCourses
                                    }
                                    div(className = "grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6") {
                                        filteredSeries.forEach { seriesItem ->
                                            SeriesCard(seriesItem) { reservationTarget = ReservationTarget.Series(seriesItem) }
                                        }
                                    }
                                }
                            }

                            if (filteredEvents.isNotEmpty()) {
                                div(className = "flex flex-col gap-3 sm:gap-4") {
                                    if (filteredSeries.isNotEmpty()) {
                                        div(className = "text-sm font-semibold text-base-content/50 uppercase tracking-wider px-1") {
                                            +currentStrings.individualEvents
                                        }
                                    }
                                    filteredEvents.forEach { eventItem ->
                                        Event(eventItem) { reservationTarget = ReservationTarget.Instance(eventItem) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    CalendarView(filteredEvents) { reservationTarget = ReservationTarget.Instance(it) }
                }
            }
        }

        footer(className = "footer footer-center p-8 text-base-content/50") {
            aside {
                a(href="#", className = "link link-hover") { +currentStrings.contact }
                p { +currentStrings.copyright }
            }
        }

        ReservationModal(
            target = reservationTarget,
            user = user,
            onClose = { reservationTarget = null },
            onSubmit = { target, data ->
                onSubmitReservation(target, data)
                reservationTarget = null
            }
        )
    }
}