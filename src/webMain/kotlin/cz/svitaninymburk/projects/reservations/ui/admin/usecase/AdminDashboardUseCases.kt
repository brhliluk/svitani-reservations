package cz.svitaninymburk.projects.reservations.ui.admin.usecase

import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Podíl obsazenosti, od kterého se ukazatel u lekce zbarví do varovné. */
private const val NEARLY_FULL_RATIO = 0.8

enum class CapacityLevel { OK, NEARLY_FULL, FULL }

/**
 * Jak se má obsazenost lekce v přehledu tvářit.
 *
 * Pořadí větví je podstatné: [FULL] vyhrává, takže přeplněná lekce (obsazeno
 * víc než kapacita, což se stane, když do lekce kurzu spadnou účastníci kurzu)
 * se ukáže jako plná, ne jako "skoro plná". Prahu [NEARLY_FULL_RATIO] se musí
 * překročit, ne dosáhnout — 8 z 10 je pořád v pohodě.
 */
fun capacityLevel(occupied: Int, capacity: Int): CapacityLevel = when {
    occupied >= capacity -> CapacityLevel.FULL
    occupied.toDouble() / capacity > NEARLY_FULL_RATIO -> CapacityLevel.NEARLY_FULL
    else -> CapacityLevel.OK
}

// --- UseCase třídy (tenké, vrací Either) ---

class AdminDashboardQueries(private val admin: AdminServiceInterface) {
    suspend fun summary() = admin.getDashboardSummary()
}

class AdminDashboardMutations(private val admin: AdminServiceInterface) {
    suspend fun markAsPaid(reservationId: Uuid) = admin.markReservationAsPaid(reservationId)
}
