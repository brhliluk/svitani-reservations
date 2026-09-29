package cz.svitaninymburk.projects.reservations.ui.dashboard.usecase

import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.util.humanReadable
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Id z adresního řádku (`/?filter=…`) — nečitelnou hodnotu bereme jako "bez filtru".
 * `Uuid.parse` na nesmysl vyhodí výjimku, a ta by při skládání obrazovky shodila
 * celý rozcestník.
 */
fun parseUuidOrNull(value: String?): Uuid? =
    value?.let { try { Uuid.parse(it) } catch (_: IllegalArgumentException) { null } }

/** Nejužší filtr vyhrává: konkrétní termín, pak kurz, pak šablona. */
fun filterDashboardEvents(
    events: List<EventInstance>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
    selectedInstanceId: Uuid? = null,
): List<EventInstance> = when {
    selectedInstanceId != null -> events.filter { it.id == selectedInstanceId }
    selectedSeriesId != null -> events.filter { it.seriesId == selectedSeriesId }
    selectedDefinitionId != null -> events.filter { it.definitionId == selectedDefinitionId }
    else -> events
}

fun filterDashboardSeries(
    series: List<EventSeries>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
    selectedInstanceId: Uuid? = null,
): List<EventSeries> = when {
    // Odkaz na jednu lekci ukazuje jen ji — karta celého kurzu by vedle ní mátla.
    selectedInstanceId != null -> emptyList()
    selectedSeriesId != null -> series.filter { it.id == selectedSeriesId }
    selectedDefinitionId != null -> series.filter { it.definitionId == selectedDefinitionId }
    else -> series
}

/** Popisek aktivního filtru — termín (název a začátek), jinak kurz, jinak šablona, jinak nic. */
fun activeFilterName(
    series: List<EventSeries>,
    definitions: List<EventDefinition>,
    selectedSeriesId: Uuid?,
    selectedDefinitionId: Uuid?,
    events: List<EventInstance> = emptyList(),
    selectedInstanceId: Uuid? = null,
): String? = selectedInstanceId?.let { id ->
    events.find { it.id == id }?.let { "${it.title} • ${it.startDateTime.humanReadable}" }
}
    ?: selectedSeriesId?.let { id -> series.find { it.id == id }?.title }
    ?: definitions.find { it.id == selectedDefinitionId }?.title

// --- UseCase třídy (tenké, vrací Either) ---

class DashboardQueries(private val event: EventServiceInterface) {
    suspend fun dashboardData() = event.getDashboardData()
}
