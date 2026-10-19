package cz.svitaninymburk.projects.reservations.ui.admin.events.usecase

import cz.svitaninymburk.projects.reservations.admin.AdminEventListItem
import cz.svitaninymburk.projects.reservations.admin.EventsPage
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlin.uuid.Uuid

const val DEFINITIONS_PAGE_SIZE = 20
const val CHILDREN_PAGE_SIZE = 10

// --- Pure helpery (testovatelné bez RPC) ---

fun definitionRows(data: EventsPage): List<AdminEventListItem> =
    data.items.filter { it.isDefinitionOnly }.sortedBy { it.title }

fun childrenByDefinition(data: EventsPage): Map<Uuid?, List<AdminEventListItem>> =
    data.items.filter { !it.isDefinitionOnly }
        .groupBy { it.definitionId }
        .mapValues { (_, children) -> children.sortedBy { it.dateInfo } }

// --- UseCase třídy (tenké, vrací Either) ---

class AdminEventsQueries(private val admin: AdminServiceInterface) {
    suspend fun events(page: Int, pageSize: Int, includePast: Boolean) =
        admin.getAllEvents(page, pageSize, includePast)
}

class AdminEventsMutations(private val admin: AdminServiceInterface) {
    suspend fun setSeriesPublished(id: Uuid, published: Boolean) = admin.setSeriesPublished(id, published)
    suspend fun setInstancePublished(id: Uuid, published: Boolean) = admin.setInstancePublished(id, published)
    suspend fun deleteDefinition(id: Uuid) = admin.deleteEventDefinition(id)
    suspend fun deleteSeries(id: Uuid, refund: Boolean) = admin.deleteEventSeries(id, refund)
    suspend fun deleteInstance(id: Uuid, refund: Boolean) = admin.deleteEventInstance(id, refund)
}
