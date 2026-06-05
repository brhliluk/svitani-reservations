package cz.svitaninymburk.projects.reservations.repository.attendance

import kotlin.uuid.Uuid

interface AttendanceRepository {
    suspend fun isCheckedIn(reservationId: Uuid): Boolean
    suspend fun setCheckedIn(reservationId: Uuid, checkedIn: Boolean)
    suspend fun checkedInFlags(reservationIds: List<Uuid>): Map<Uuid, Boolean>
}
