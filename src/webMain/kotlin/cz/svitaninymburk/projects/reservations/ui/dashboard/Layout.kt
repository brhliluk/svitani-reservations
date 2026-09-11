package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.events.CalendarView
import cz.svitaninymburk.projects.reservations.ui.events.DefinitionCard
import cz.svitaninymburk.projects.reservations.ui.events.Event
import cz.svitaninymburk.projects.reservations.ui.events.SeriesCard
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.html.*
import kotlin.uuid.Uuid

@Composable
fun IComponent.DashboardLayout(
    user: User?,
    walletCode: String? = null,
    events: List<EventInstance>,
    series: List<EventSeries>,
    definitions: List<EventDefinition>,
    initialFilterId: String? = null,
    initialSeriesId: String? = null,
    isSubmitting: Boolean = false,
    onSubmitReservation: (ReservationTarget, ReservationFormData) -> Unit,
    onFilterChange: (Uuid?) -> Unit = {},
    onSeriesFilterChange: (Uuid?) -> Unit = {},
) {
    val model = remember {
        DashboardLayoutModel(initialFilterId, initialSeriesId, onFilterChange, onSeriesFilterChange)
    }

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-5xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {

            DashboardTabs(model)

            if (model.activeTab == DashboardTab.CATALOG) {
                CatalogGrid(definitions) { model.filterByDefinition(it.id) }
            } else {
                ScheduleToolbar(model, model.filterName(series, definitions))
                if (model.viewMode == ViewMode.LIST) {
                    ScheduleList(model.events(events), model.series(series), model)
                } else {
                    CalendarView(model.events(events)) { model.openReservation(ReservationTarget.Instance(it)) }
                }
            }
        }

        ReservationModal(
            target = model.reservationTarget,
            user = user,
            initialWalletCode = walletCode,
            isSubmitting = isSubmitting,
            asWaitlist = model.isWaitlistSignup,
            onClose = { model.closeReservation() },
            onSubmit = { target, data -> onSubmitReservation(target, data) },
        )
    }
}

@Composable
private fun IComponent.DashboardTabs(model: DashboardLayoutModel) {
    val currentStrings by strings
    div(className = "flex justify-center") {
        div(className = "tabs tabs-boxed bg-base-100 p-1 rounded-full shadow-sm w-full sm:w-auto") {

            a(className = "tab rounded-full min-h-11 px-4 text-sm sm:text-base flex-1 sm:flex-none transition-colors duration-200 ${if (model.activeTab == DashboardTab.SCHEDULE) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                onClick { model.showSchedule() }
                span(className = "icon-[heroicons--calendar-days] size-5 mr-2")
                +currentStrings.schedule
            }

            a(className = "tab rounded-full min-h-11 px-4 text-sm sm:text-base flex-1 sm:flex-none transition-colors duration-200 ${if (model.activeTab == DashboardTab.CATALOG) "tab-active bg-primary text-primary-content font-bold shadow-sm" else ""}") {
                onClick { model.showCatalog() }
                span(className = "icon-[heroicons--swatch] size-5 mr-2")
                +currentStrings.catalog
            }
        }
    }
}

@Composable
private fun IComponent.CatalogGrid(definitions: List<EventDefinition>, onSelect: (EventDefinition) -> Unit) {
    div(className = "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6 animate-fade-in") {
        definitions.forEach { def ->
            DefinitionCard(def) { onSelect(def) }
        }
    }
}

/** Řádek nad výpisem: co je zrovna vyfiltrované a přepínač seznam / kalendář. */
@Composable
private fun IComponent.ScheduleToolbar(model: DashboardLayoutModel, activeFilterName: String?) {
    val currentStrings by strings
    div(className = "flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3 sm:gap-4 mb-2") {
        if (activeFilterName != null) {
            div(className = "badge badge-primary gap-2 px-4 py-2 h-auto min-h-11 whitespace-normal text-left w-full sm:w-auto sm:max-w-md justify-start sm:justify-center cursor-pointer hover:badge-error hover:text-white transition-colors tooltip tooltip-bottom") {
                attribute("data-tip", currentStrings.clearFilterTooltip)
                onClick { model.clearFilters() }
                span(className = "icon-[heroicons--funnel] size-4 shrink-0")
                span(className = "flex-1") { +currentStrings.filterIsActive(activeFilterName) }
                span(className = "icon-[heroicons--x-mark] size-4 shrink-0 ml-1")
            }
        } else {
            div(className = "text-2xl font-bold text-base-content") { +currentStrings.allEvents }
        }

        div(className = "join bg-base-100 shadow-sm border border-base-300 rounded-lg w-full sm:w-auto") {
            button(className = "join-item btn flex-1 sm:flex-none min-h-11 ${if (model.viewMode == ViewMode.LIST) "btn-primary" else "btn-ghost"}") {
                attribute("aria-label", currentStrings.listView)
                onClick { model.setViewMode(ViewMode.LIST) }
                span(className = "icon-[heroicons--list-bullet] size-5")
            }
            button(className = "join-item btn flex-1 sm:flex-none min-h-11 ${if (model.viewMode == ViewMode.CALENDAR) "btn-primary" else "btn-ghost"}") {
                attribute("aria-label", currentStrings.calendarView)
                onClick { model.setViewMode(ViewMode.CALENDAR) }
                span(className = "icon-[heroicons--calendar] size-5")
            }
        }
    }
}

@Composable
private fun IComponent.ScheduleList(
    events: List<EventInstance>,
    series: List<EventSeries>,
    model: DashboardLayoutModel,
) {
    val currentStrings by strings
    div(className = "flex flex-col gap-6 animate-fade-in") {
        if (events.isEmpty() && series.isEmpty()) {
            div(className = "alert bg-base-100 shadow-sm") {
                span(className = "icon-[heroicons--information-circle] size-6 text-info")
                +currentStrings.noEventsFoundForFilter
            }
        } else {
            if (series.isNotEmpty()) {
                div(className = "flex flex-col gap-3 sm:gap-4") {
                    div(className = "text-xs font-bold text-primary/60 uppercase tracking-wider px-1") {
                        +currentStrings.openCourses
                    }
                    div(className = "grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6") {
                        series.forEach { seriesItem ->
                            SeriesCard(seriesItem) { model.openReservation(ReservationTarget.Series(seriesItem)) }
                        }
                    }
                }
            }

            if (events.isNotEmpty()) {
                div(className = "flex flex-col gap-3 sm:gap-4") {
                    // Nadpis jen když nad seznamem stojí ještě kurzy — jinak je zbytečný.
                    if (series.isNotEmpty()) {
                        div(className = "text-xs font-bold text-primary/60 uppercase tracking-wider px-1") {
                            +currentStrings.individualEvents
                        }
                    }
                    events.forEach { eventItem ->
                        Event(
                            event = eventItem,
                            onClick = { model.openReservation(ReservationTarget.Instance(eventItem)) },
                            onWaitlistClick = { model.openReservation(ReservationTarget.Instance(eventItem), asWaitlist = true) },
                        )
                    }
                }
            }
        }
    }
}
