package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cz.svitaninymburk.projects.reservations.attendance.AttendanceEntry
import cz.svitaninymburk.projects.reservations.attendance.AttendanceList
import cz.svitaninymburk.projects.reservations.error.AttendanceError
import cz.svitaninymburk.projects.reservations.repository.attendance.AttendanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.uuid.Uuid

class AttendanceService(
    private val reservationRepository: ReservationRepository,
    private val attendanceRepository: AttendanceRepository,
) {
    suspend fun getAttendance(eventInstanceId: Uuid): Either<AttendanceError.Get, AttendanceList> {
        val reservations = reservationRepository.findByReference(Reference.Instance(eventInstanceId))
            .filter { it.status != Reservation.Status.CANCELLED && it.status != Reservation.Status.REJECTED }
        val flags = attendanceRepository.checkedInFlags(reservations.map { it.id })
        val entries = reservations.map {
            AttendanceEntry(it.id, it.contactName, it.seatCount, flags[it.id] == true)
        }
        return AttendanceList(eventInstanceId, entries).right()
    }

    suspend fun setAttendance(reservationId: Uuid, checkedIn: Boolean): Either<AttendanceError.Set, Unit> {
        val exists = reservationRepository.findById(reservationId) != null
        if (!exists) return AttendanceError.ReservationNotFound.left()
        attendanceRepository.setCheckedIn(reservationId, checkedIn)
        return Unit.right()
    }
}
