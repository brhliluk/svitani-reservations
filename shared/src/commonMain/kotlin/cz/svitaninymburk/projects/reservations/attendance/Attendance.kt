package cz.svitaninymburk.projects.reservations.attendance

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AttendanceEntry(
    val reservationId: Uuid,
    val contactName: String,
    val seatCount: Int,
    val checkedIn: Boolean,
)

@Serializable
data class AttendanceList(
    val eventInstanceId: Uuid,
    val entries: List<AttendanceEntry>,
)

@Serializable
data class SetAttendanceRequest(
    val reservationId: Uuid,
    val checkedIn: Boolean,
)
