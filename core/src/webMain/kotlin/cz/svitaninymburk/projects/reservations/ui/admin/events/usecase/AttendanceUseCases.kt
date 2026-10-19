package cz.svitaninymburk.projects.reservations.ui.admin.events.usecase

import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

/** Prázdné řádky na dopsání lidí, co přijdou bez rezervace — víc než stránka se stejně nevytiskne. */
const val ATTENDANCE_MIN_EXTRA_ROWS = 0
const val ATTENDANCE_MAX_EXTRA_ROWS = 20
const val ATTENDANCE_DEFAULT_EXTRA_ROWS = 5

fun clampExtraRows(rows: Int): Int = rows.coerceIn(ATTENDANCE_MIN_EXTRA_ROWS, ATTENDANCE_MAX_EXTRA_ROWS)

// --- UseCase třídy (tenké, vrací Either) ---

class AttendanceQueries(private val admin: AdminServiceInterface) {
    suspend fun eventDetail(id: Uuid, isSeries: Boolean) = admin.getEventDetail(id, isSeries)
}
