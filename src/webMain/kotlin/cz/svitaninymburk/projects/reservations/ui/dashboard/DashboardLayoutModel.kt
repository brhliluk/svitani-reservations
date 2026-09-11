package cz.svitaninymburk.projects.reservations.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.activeFilterName
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardEvents
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.filterDashboardSeries
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.parseUuidOrNull
import kotlin.uuid.Uuid

enum class DashboardTab { SCHEDULE, CATALOG }
enum class ViewMode { LIST, CALENDAR }

/**
 * Stav samotného rozcestníku — záložka, filtry, přepínač seznam/kalendář a otevřený
 * rezervační formulář. Žádné RPC, proto to není [cz.svitaninymburk.projects.reservations.ui.util.ScreenModel]
 * (stejně jako `CalendarModel`); data i odesílání rezervace zůstávají na [DashboardModel].
 *
 * Filtr se drží dvakrát — v modelu i v adrese — aby šel odkaz na filtrovaný výpis poslat.
 */
class DashboardLayoutModel(
    initialFilterId: String?,
    initialSeriesId: String?,
    private val onFilterChange: (Uuid?) -> Unit,
    private val onSeriesFilterChange: (Uuid?) -> Unit,
) {
    var activeTab by mutableStateOf(DashboardTab.SCHEDULE); private set
    var viewMode by mutableStateOf(ViewMode.LIST); private set
    var selectedDefinitionId by mutableStateOf(parseUuidOrNull(initialFilterId)); private set
    var selectedSeriesId by mutableStateOf(parseUuidOrNull(initialSeriesId)); private set
    var reservationTarget by mutableStateOf<ReservationTarget?>(null); private set
    var isWaitlistSignup by mutableStateOf(false); private set

    fun events(all: List<EventInstance>) = filterDashboardEvents(all, selectedSeriesId, selectedDefinitionId)

    fun series(all: List<EventSeries>) = filterDashboardSeries(all, selectedSeriesId, selectedDefinitionId)

    fun filterName(series: List<EventSeries>, definitions: List<EventDefinition>) =
        activeFilterName(series, definitions, selectedSeriesId, selectedDefinitionId)

    fun showSchedule() { activeTab = DashboardTab.SCHEDULE }

    /** Katalog ukazuje všechny šablony, filtr z rozpisu by v něm mátl. */
    fun showCatalog() {
        activeTab = DashboardTab.CATALOG
        clearFilters()
    }

    fun setViewMode(mode: ViewMode) { viewMode = mode }

    fun filterByDefinition(definitionId: Uuid) {
        selectedSeriesId = null
        selectedDefinitionId = definitionId
        activeTab = DashboardTab.SCHEDULE
        onSeriesFilterChange(null)
        onFilterChange(definitionId)
    }

    fun clearFilters() {
        selectedDefinitionId = null
        selectedSeriesId = null
        onFilterChange(null)
        onSeriesFilterChange(null)
    }

    fun openReservation(target: ReservationTarget, asWaitlist: Boolean = false) {
        isWaitlistSignup = asWaitlist
        reservationTarget = target
    }

    fun closeReservation() {
        reservationTarget = null
        isWaitlistSignup = false
    }
}
