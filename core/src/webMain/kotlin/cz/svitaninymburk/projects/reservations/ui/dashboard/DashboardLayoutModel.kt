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
import cz.svitaninymburk.projects.reservations.ui.dashboard.usecase.searchDashboard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class ViewMode { LIST, CALENDAR }

/**
 * Stav samotného rozcestníku — hledání, filtry, přepínač seznam/kalendář a otevřený
 * rezervační formulář. Žádné RPC, proto to není [cz.svitaninymburk.projects.reservations.ui.util.ScreenModel]
 * (stejně jako `CalendarModel`); data i odesílání rezervace zůstávají na [DashboardModel].
 *
 * Filtr se drží dvakrát — v modelu i v adrese — aby šel odkaz na filtrovaný výpis poslat.
 * Hledání v adrese není: filtruje se živě při psaní a do historie prohlížeče by naplnilo stovky kroků.
 */
class DashboardLayoutModel(
    initialFilterId: String?,
    initialSeriesId: String?,
    initialInstanceId: String?,
    private val onFilterChange: (Uuid?) -> Unit,
    private val onSeriesFilterChange: (Uuid?) -> Unit,
    private val onInstanceFilterChange: (Uuid?) -> Unit,
    /** Pro "dnes" a "zítra" v hledání. */
    private val today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
) {
    var viewMode by mutableStateOf(ViewMode.LIST); private set
    var selectedDefinitionId by mutableStateOf(parseUuidOrNull(initialFilterId)); private set
    var selectedSeriesId by mutableStateOf(parseUuidOrNull(initialSeriesId)); private set
    var selectedInstanceId by mutableStateOf(parseUuidOrNull(initialInstanceId)); private set
    var reservationTarget by mutableStateOf<ReservationTarget?>(null); private set
    var isWaitlistSignup by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set

    val isSearching: Boolean get() = searchQuery.isNotBlank()

    /** Akce a kurzy po filtru z adresy a hledání — hledá se v obou naráz, viz [searchDashboard]. */
    fun shown(events: List<EventInstance>, series: List<EventSeries>) = searchDashboard(
        filterDashboardEvents(events, selectedSeriesId, selectedDefinitionId, selectedInstanceId),
        filterDashboardSeries(series, selectedSeriesId, selectedDefinitionId, selectedInstanceId),
        searchQuery,
        today,
    )

    fun search(query: String) { searchQuery = query }

    fun clearSearch() { searchQuery = "" }

    fun filterName(events: List<EventInstance>, series: List<EventSeries>, definitions: List<EventDefinition>) =
        activeFilterName(series, definitions, selectedSeriesId, selectedDefinitionId, events, selectedInstanceId)

    fun setViewMode(mode: ViewMode) { viewMode = mode }

    fun clearFilters() {
        selectedDefinitionId = null
        selectedSeriesId = null
        selectedInstanceId = null
        onFilterChange(null)
        onSeriesFilterChange(null)
        onInstanceFilterChange(null)
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
