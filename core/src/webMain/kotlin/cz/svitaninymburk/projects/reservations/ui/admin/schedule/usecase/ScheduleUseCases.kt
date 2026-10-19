package cz.svitaninymburk.projects.reservations.ui.admin.schedule.usecase

import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface

const val SCHEDULE_PAGE_SIZE = 20

// --- Pure helpery (testovatelné bez RPC) ---

/**
 * Stránka, na které začínají nadcházející termíny. Používá se při zapnutí "zobrazit
 * i minulé", aby přepnutí neodhodilo uživatele do dávné historie.
 */
fun firstUpcomingPage(pastCount: Long, pageSize: Int): Int =
    if (pageSize <= 0) 0 else (pastCount / pageSize).toInt()

// --- UseCase třídy (tenké, vrací Either) ---

class AdminScheduleQueries(private val admin: AdminServiceInterface) {
    suspend fun schedule(page: Int, pageSize: Int, includePast: Boolean) =
        admin.getSchedule(page, pageSize, includePast)
}
