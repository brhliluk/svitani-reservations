package cz.svitaninymburk.projects.reservations.ui.admin.events.usecase

import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import kotlin.uuid.Uuid

// --- UseCase třídy (tenké, vrací Either) ---

class EventPreviewQueries(private val event: EventServiceInterface) {
    suspend fun instance(id: Uuid) = event.getInstance(id)
    suspend fun seriesDetail(id: Uuid) = event.getSeriesDetail(id)
}
