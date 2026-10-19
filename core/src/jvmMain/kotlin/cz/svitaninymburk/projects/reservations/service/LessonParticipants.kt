package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.INACTIVE_RESERVATION_STATUSES
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.SeriesLessonOptOutRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.uuid.Uuid

/**
 * Kdo drží místo na konkrétní akci nebo lekci. Přihláška na kurz je jedna rezervace
 * na sérii, takže k přímým rezervacím lekce patří i zapsaní na kurz — kromě těch,
 * kdo se z ní omluvili. Stejně to odečítá SeriesLessonLoad nad čítačem obsazenosti;
 * dashboard, detail akce i prezenčka musí vidět totéž číslo.
 */
internal class LessonParticipants(
    private val reservationRepository: ReservationRepository,
    private val seriesLessonOptOutRepository: SeriesLessonOptOutRepository,
) {
    data class Enrollees(val attending: List<Reservation>, val optedOut: List<Reservation>)

    /** Přímé rezervace akce nebo lekce, které drží místo. */
    suspend fun direct(instanceId: Uuid): List<Reservation> =
        reservationRepository.findByReference(Reference.Instance(instanceId)).filter { it.holdsSeat }

    /** Zapsaní na kurz, do kterého lekce patří; u samostatné akce nikdo. */
    suspend fun courseEnrollees(instance: EventInstance): Enrollees {
        val seriesId = instance.seriesId ?: return Enrollees(emptyList(), emptyList())
        val optedOutIds = seriesLessonOptOutRepository.findByInstance(instance.id).map { it.reservationId }.toSet()
        val (optedOut, attending) = reservationRepository.findByReference(Reference.Series(seriesId))
            .filter { it.holdsSeat }
            .partition { it.id in optedOutIds }
        return Enrollees(attending, optedOut)
    }

    /** Kolik míst na lekci drží přímé rezervace i zapsaní na kurz dohromady. */
    suspend fun seatCount(instance: EventInstance): Int =
        direct(instance.id).sumOf { it.seatCount } + courseEnrollees(instance).attending.sumOf { it.seatCount }
}

internal val Reservation.holdsSeat: Boolean get() = status !in INACTIVE_RESERVATION_STATUSES
