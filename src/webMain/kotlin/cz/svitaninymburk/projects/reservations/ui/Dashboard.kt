package cz.svitaninymburk.projects.reservations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*

enum class DashboardTab { SCHEDULE, CATALOG }
enum class ViewMode { LIST, CALENDAR }

@Composable
fun IComponent.DashboardScreen(
    user: User?,
    events: List<EventInstance>,
    series: List<EventSeries>,
    definitions: List<EventDefinition>,
    initialFilterId: String? = null,
    onSubmitReservation: (ReservationTarget, ReservationFormData) -> Unit,
) {
    val currentStrings by strings

    var reservationTarget by remember { mutableStateOf<ReservationTarget?>(null) }
    var activeTab by remember { mutableStateOf(if (initialFilterId != null) DashboardTab.SCHEDULE else DashboardTab.SCHEDULE) }
    var selectedDefinitionId by remember { mutableStateOf(initialFilterId) }
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
        AppHeader(user)

        main(className = "flex-1 w-full max-w-5xl mx-auto py-8 px-4 flex flex-col gap-6") {

            div(className = "flex justify-center") {
                div(className = "tabs tabs-boxed bg-base-100 p-1 rounded-full shadow-sm") {

                    a(className = "tab rounded-full transition-all duration-300 ${if (activeTab == DashboardTab.SCHEDULE) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                        onClick { activeTab = DashboardTab.SCHEDULE }
                        span(className = "icon-[heroicons--calendar-days] size-5 mr-2")
                        +"Program" // currentStrings.schedule
                    }

                    a(className = "tab rounded-full transition-all duration-300 ${if (activeTab == DashboardTab.CATALOG) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                        onClick {
                            activeTab = DashboardTab.CATALOG
                            selectedDefinitionId = null
                        }
                        span(className = "icon-[heroicons--swatch] size-5 mr-2")
                        +"Nabídka"
                    }
                }
            }

            if (activeTab == DashboardTab.CATALOG) {
                div(className = "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-fade-in") {
                    definitions.forEach { def ->
                        DefinitionCard(def) {
                            selectedDefinitionId = def.id
                            activeTab = DashboardTab.SCHEDULE
                        }
                    }
                }
            } else {
                div(className = "flex flex-col sm:flex-row justify-between items-center gap-4 mb-2") {
                    if (activeFilterName != null) {
                        div(className = "badge badge-lg badge-primary gap-2 p-4 cursor-pointer hover:badge-error hover:text-white transition-colors tooltip tooltip-bottom") {
                            attribute("data-tip", "Click to clear filter")
                            onClick { selectedDefinitionId = null }
                            span(className = "icon-[heroicons--funnel] size-4")
                            +"Filter: $activeFilterName"
                            span(className = "icon-[heroicons--x-mark] size-4 ml-1")
                        }
                    } else {
                        div(className = "text-xl font-bold text-base-content") {
                            +"Všechny akce"
                        }
                    }

                    div(className = "join bg-base-100 shadow-sm border border-base-300 rounded-lg") {
                        button(className = "join-item btn btn-sm ${if(viewMode == ViewMode.LIST) "btn-active" else "btn-ghost"}") {
                            onClick { viewMode = ViewMode.LIST }
                            span(className = "icon-[heroicons--list-bullet] size-5")
                        }
                        button(className = "join-item btn btn-sm ${if(viewMode == ViewMode.CALENDAR) "btn-active" else "btn-ghost"}") {
                            onClick { viewMode = ViewMode.CALENDAR }
                            span(className = "icon-[heroicons--calendar] size-5")
                        }
                    }
                }

                // Samotný seznam (List View)
                if (viewMode == ViewMode.LIST) {
                    div(className = "flex flex-col gap-6 animate-fade-in") {

                        if (filteredSeries.isNotEmpty()) {
                            div(className = "grid grid-cols-1 md:grid-cols-2 gap-6") {
                                filteredSeries.forEach { seriesItem ->
                                    SeriesCard(seriesItem) { reservationTarget = ReservationTarget.Series(seriesItem) }
                                }
                            }
                            if (filteredEvents.isNotEmpty()) {
                                div(className = "divider text-base-content/50 text-sm") { +currentStrings.showDates }
                            }
                        }

                        if (filteredEvents.isEmpty() && filteredSeries.isEmpty()) {
                            div(className = "alert bg-base-100 shadow-sm") {
                                span(className = "icon-[heroicons--information-circle] size-6 text-info")
                                +"Pro tento výběr nejsou vypsané žádné termíny."
                            }
                        } else {
                            filteredEvents.forEach { eventItem ->
                                Event(eventItem) { reservationTarget = ReservationTarget.Instance(eventItem) }
                            }
                        }
                    }
                } else {
                    CalendarView(filteredEvents, { reservationTarget = ReservationTarget.Instance(it) })
                }
            }
        }

        footer(className = "footer footer-center p-8 text-base-content/50") {
            aside {
                a(href="#", className = "link link-hover") { +currentStrings.contact }
                p { +"© 2024 Reservation System" }
            }
        }

        ReservationModal(
            target = reservationTarget,
            onClose = { reservationTarget = null },
            onSubmit = { target, data ->
                onSubmitReservation(target, data)
                reservationTarget = null
            }
        )
    }
}