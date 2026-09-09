package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.SeriesLessonItem
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid

/**
 * Sestavení termínů kurzu i s omluvenkami dané rezervace. Čte to jak veřejná
 * [ReservationService], tak [AuthenticatedReservationService] — každá s vlastní
 * autorizací, ale nad stejným seznamem, aby se obě nerozešly.
 */
class SeriesLessonsReader(
    private val eventInstanceRepository: EventInstanceRepository,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
) {

    suspend fun lessonsFor(reservationId: Uuid, seriesId: Uuid): List<SeriesLessonItem> {
        val optOutMap = seriesLessonOptOutRepository.findByReservation(reservationId)
            .associateBy { it.instanceId }

        // findBySeries už řadí podle startDateTime ASC.
        return eventInstanceRepository.findBySeries(seriesId).map { instance ->
            val optOut = optOutMap[instance.id]
            SeriesLessonItem(
                instanceId = instance.id,
                startDateTime = instance.startDateTime,
                endDateTime = instance.endDateTime,
                isCancelled = instance.isCancelled,
                isOptedOut = optOut != null,
                isLateCancellation = optOut?.isLateCancellation ?: false,
                startsAt = instance.startDateTime.toInstant(OPT_OUT_TIMEZONE),
                optOutDeadline = refundDeadlineFor(instance.startDateTime),
            )
        }
    }
}
