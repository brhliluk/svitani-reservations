package cz.svitaninymburk.projects.reservations.repository.attendance

import kotlin.uuid.Uuid

/**
 * Prezence se vede na dvojici (rezervace, lekce). Účastník kurzu má jedinou
 * rezervaci, ale objevuje se ve všech lekcích série, takže samotné
 * `reservationId` by jeho docházku slilo do jednoho příznaku.
 */
interface AttendanceRepository {
    suspend fun isCheckedIn(reservationId: Uuid, instanceId: Uuid): Boolean
    suspend fun setCheckedIn(reservationId: Uuid, instanceId: Uuid, checkedIn: Boolean)
    suspend fun checkedInFlags(instanceId: Uuid, reservationIds: List<Uuid>): Map<Uuid, Boolean>
}
