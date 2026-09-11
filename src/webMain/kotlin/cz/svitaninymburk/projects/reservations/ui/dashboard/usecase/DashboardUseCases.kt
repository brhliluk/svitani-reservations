package cz.svitaninymburk.projects.reservations.ui.dashboard.usecase

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Id z adresního řádku (`/?filter=…`) — nečitelnou hodnotu bereme jako "bez filtru".
 * `Uuid.parse` na nesmysl vyhodí výjimku, a ta by při skládání obrazovky shodila
 * celý rozcestník.
 */
fun parseUuidOrNull(value: String?): Uuid? =
    value?.let { try { Uuid.parse(it) } catch (_: IllegalArgumentException) { null } }

/** Filtr kurzu je užší než filtr šablony, proto má přednost. */
fun filterDashboardEvents(
    events: List<EventInstance>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
): List<EventInstance> = when {
    selectedSeriesId != null -> events.filter { it.seriesId == selectedSeriesId }
    selectedDefinitionId != null -> events.filter { it.definitionId == selectedDefinitionId }
    else -> events
}

fun filterDashboardSeries(
    series: List<EventSeries>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
): List<EventSeries> = when {
    selectedSeriesId != null -> series.filter { it.id == selectedSeriesId }
    selectedDefinitionId != null -> series.filter { it.definitionId == selectedDefinitionId }
    else -> series
}

/** Popisek aktivního filtru — název kurzu, jinak název šablony, jinak nic. */
fun activeFilterName(
    series: List<EventSeries>,
    definitions: List<EventDefinition>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
): String? = selectedSeriesId?.let { id -> series.find { it.id == id }?.title }
    ?: definitions.find { it.id == selectedDefinitionId }?.title

// --- UseCase třídy (tenké, vrací Either) ---

class DashboardQueries(private val event: EventServiceInterface) {
    suspend fun dashboardData() = event.getDashboardData()
}
