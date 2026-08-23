package cz.svitaninymburk.projects.reservations.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cz.svitaninymburk.projects.reservations.attendance.AttendanceEntry
import cz.svitaninymburk.projects.reservations.attendance.AttendanceList
import cz.svitaninymburk.projects.reservations.error.AttendanceError
import cz.svitaninymburk.projects.reservations.repository.attendance.AttendanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import kotlin.uuid.Uuid

class AttendanceService(
    private val reservationRepository: ReservationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
) {
    suspend fun getAttendance(eventInstanceId: Uuid): Either<AttendanceError.Get, AttendanceList> {
        val direct = reservationRepository.findByReference(Reference.Instance(eventInstanceId))
            .filter { it.status !in INACTIVE_RESERVATION_STATUSES }

        // Přihláška na kurz je jedna rezervace na sérii; do prezenčky jednotlivé
        // lekce patří všichni, kdo se z ní zrovna neomluvili.
        val instance = eventInstanceRepository.get(eventInstanceId)
        val seriesId = instance?.seriesId
        val enrolled = if (seriesId == null) emptyList() else {
            val optedOut = seriesLessonOptOutRepository.findByInstance(eventInstanceId)
                .map { it.reservationId }
                .toSet()
            reservationRepository.findByReference(Reference.Series(seriesId))
                .filter { it.status !in INACTIVE_RESERVATION_STATUSES }
                .filterNot { it.id in optedOut }
        }

        val flags = attendanceRepository.checkedInFlags(
            eventInstanceId,
            (direct + enrolled).map { it.id },
        )
        val entries = direct.map {
            AttendanceEntry(it.id, it.contactName, it.seatCount, flags[it.id] == true, isCourseEnrollee = false)
        } + enrolled.map {
            AttendanceEntry(it.id, it.contactName, it.seatCount, flags[it.id] == true, isCourseEnrollee = true)
        }
        return AttendanceList(eventInstanceId, entries).right()
    }

    suspend fun setAttendance(
        reservationId: Uuid,
        instanceId: Uuid,
        checkedIn: Boolean,
    ): Either<AttendanceError.Set, Unit> {
        val exists = reservationRepository.findById(reservationId) != null
        if (!exists) return AttendanceError.ReservationNotFound.left()
        attendanceRepository.setCheckedIn(reservationId, instanceId, checkedIn)
        return Unit.right()
    }
}
