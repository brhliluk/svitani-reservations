package cz.svitaninymburk.projects.reservations.ui.admin.events.usecase

import cz.svitaninymburk.projects.reservations.service.EventServiceInterface

// --- UseCase třídy (tenké, vrací Either) ---

class EventDefinitionTitleQuery(private val event: EventServiceInterface) {
    /** Název šablony pro podtitulek rozcestníku "co chcete založit". */
    suspend fun titleOf(definitionId: String): String? =
        event.getAllDefinitions().getOrNull()?.find { it.id.toString() == definitionId }?.title
}
