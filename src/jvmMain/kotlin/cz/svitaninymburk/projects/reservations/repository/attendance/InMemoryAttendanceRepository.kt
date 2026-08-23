package cz.svitaninymburk.projects.reservations.repository.attendance

import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class InMemoryAttendanceRepository : AttendanceRepository {
    private val checked = ConcurrentHashMap<Pair<Uuid, Uuid>, Boolean>()

    override suspend fun isCheckedIn(reservationId: Uuid, instanceId: Uuid) =
        checked[reservationId to instanceId] == true

    override suspend fun setCheckedIn(reservationId: Uuid, instanceId: Uuid, checkedIn: Boolean) {
        checked[reservationId to instanceId] = checkedIn
    }

    override suspend fun checkedInFlags(instanceId: Uuid, reservationIds: List<Uuid>) =
        reservationIds.associateWith { checked[it to instanceId] == true }
}
