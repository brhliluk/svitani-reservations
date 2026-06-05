package cz.svitaninymburk.projects.reservations.repository.attendance

import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class InMemoryAttendanceRepository : AttendanceRepository {
    private val checked = ConcurrentHashMap<Uuid, Boolean>()

    override suspend fun isCheckedIn(reservationId: Uuid) = checked[reservationId] == true

    override suspend fun setCheckedIn(reservationId: Uuid, checkedIn: Boolean) {
        checked[reservationId] = checkedIn
    }

    override suspend fun checkedInFlags(reservationIds: List<Uuid>) =
        reservationIds.associateWith { checked[it] == true }
}
