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
import cz.svitaninymburk.projects.reservations.ui.events.Event
import cz.svitaninymburk.projects.reservations.ui.events.SeriesCard
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationFormData
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationModal
import cz.svitaninymburk.projects.reservations.user.User
import dev.kilua.core.IComponent
import dev.kilua.form.text.text
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
    initialInstanceId: String? = null,
    isSubmitting: Boolean = false,
    onSubmitReservation: (ReservationTarget, ReservationFormData) -> Unit,
    onFilterChange: (Uuid?) -> Unit = {},
    onSeriesFilterChange: (Uuid?) -> Unit = {},
    onInstanceFilterChange: (Uuid?) -> Unit = {},
) {
    val model = remember {
        DashboardLayoutModel(
            initialFilterId, initialSeriesId, initialInstanceId,
            onFilterChange, onSeriesFilterChange, onInstanceFilterChange,
        )
    }

    div(className = "min-h-screen bg-base-200 flex flex-col font-sans") {
        main(className = "flex-1 w-full max-w-5xl mx-auto px-3 py-4 sm:px-4 sm:py-8 flex flex-col gap-4 sm:gap-6") {

            SearchBar(model)
            ScheduleToolbar(model, model.filterName(events, series, definitions))

            val (shownEvents, shownSeries) = model.shown(events, series)
            when {
                shownEvents.isEmpty() && shownSeries.isEmpty() && model.isSearching -> NoSearchResults(model)
                model.viewMode == ViewMode.LIST -> ScheduleList(shownEvents, shownSeries, model)
                else -> CalendarView(shownEvents) { model.openReservation(ReservationTarget.Instance(it)) }
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

/** Hledání nad rozpisem — data jsou načtená celá, filtruje se rovnou při psaní. */
@Composable
private fun IComponent.SearchBar(model: DashboardLayoutModel) {
    val currentStrings by strings
    div(className = "relative w-full") {
        span(className = "absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/50") {
            span(className = "icon-[heroicons--magnifying-glass] size-5")
        }
        text(value = model.searchQuery, className = "input input-bordered w-full min-h-11 pl-10 pr-12 bg-base-100 shadow-sm") {
            placeholder(currentStrings.dashboardSearchPlaceholder)
            attribute("aria-label", currentStrings.dashboardSearchPlaceholder)
            onInput { model.search(value ?: "") }
            onKeyup { event -> if (event.key == "Escape") model.clearSearch() }
        }
        if (model.isSearching) {
            button(className = "btn btn-ghost btn-circle min-h-11 w-11 absolute inset-y-0 right-0") {
                attribute("aria-label", currentStrings.clearSearch)
                onClick { model.clearSearch() }
                span(className = "icon-[heroicons--x-mark] size-5")
            }
        }
    }
}

@Composable
private fun IComponent.NoSearchResults(model: DashboardLayoutModel) {
    val currentStrings by strings
    div(className = "alert bg-base-100 shadow-sm animate-fade-in") {
        span(className = "icon-[heroicons--magnifying-glass] size-6 text-info")
        span(className = "flex-1") { +currentStrings.noEventsMatchSearch(model.searchQuery.trim()) }
        button(className = "btn btn-ghost min-h-11") {
            onClick { model.clearSearch() }
            +currentStrings.clearSearch
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

        // Popisky u ikon: samotná ikona kalendáře se přehlížela a nebylo poznat, že jde o přepínač.
        div(className = "join bg-base-100 shadow-sm border border-base-300 rounded-lg w-full sm:w-auto") {
            attribute("role", "group")
            ViewModeButton(model, ViewMode.LIST, "icon-[heroicons--list-bullet]", currentStrings.listView)
            ViewModeButton(model, ViewMode.CALENDAR, "icon-[heroicons--calendar-days]", currentStrings.calendarView)
        }
    }
}

@Composable
private fun IComponent.ViewModeButton(model: DashboardLayoutModel, mode: ViewMode, icon: String, label: String) {
    val isActive = model.viewMode == mode
    button(className = "join-item btn flex-1 sm:flex-none min-h-11 gap-2 ${if (isActive) "btn-primary" else "btn-ghost"}") {
        attribute("aria-pressed", isActive.toString())
        onClick { model.setViewMode(mode) }
        span(className = "$icon size-5")
        +label
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
