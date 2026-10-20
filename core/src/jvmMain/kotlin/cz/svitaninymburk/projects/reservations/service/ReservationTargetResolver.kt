package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.event.EventSeriesRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget

/**
 * Cíl rezervace časového úseku. Úseky nabízí volitelný modul (hlídání), o kterém
 * jádro neví; modul tohle rozhraní zaregistruje do Koinu. Bez něj se rezervace
 * úseku chová jako rezervace na smazanou akci.
 */
interface SlotBookingProvider {
    /** null = služba už neexistuje. */
    suspend fun targetFor(reference: Reference.Slot): ReservationTarget.Slot?
}

/**
 * Jediné místo, kde se z reference rezervace dohledává její cíl (akce, kurz, úsek).
 * null = cíl byl smazán.
 */
class ReservationTargetResolver(
    private val eventInstanceRepository: EventInstanceRepository,
    private val eventSeriesRepository: EventSeriesRepository,
    private val slotBookingProvider: SlotBookingProvider? = null,
) {
    /**
     * [activeLessonCount] přepíše u kurzu uložený počet lekcí počtem nezrušených —
     * pro e-maily a kalendář, kde se lekce vypisují.
     */
    suspend fun resolve(reference: Reference, activeLessonCount: Boolean = false): ReservationTarget? = when (reference) {
        is Reference.Instance -> eventInstanceRepository.get(reference.id)?.let { ReservationTarget.Instance(it) }
        is Reference.Series -> eventSeriesRepository.get(reference.id)?.let { series ->
            ReservationTarget.Series(
                if (activeLessonCount) series.copy(lessonCount = eventInstanceRepository.countActiveBySeries(series.id).toInt())
                else series
            )
        }
        is Reference.Slot -> slotBookingProvider?.targetFor(reference)
    }

    /** Hromadně pro seznamy: akce a kurzy jedním dotazem, úseky po jednom. */
    suspend fun resolveAll(references: Collection<Reference>): Map<Reference, ReservationTarget> {
        val instanceIds = references.filterIsInstance<Reference.Instance>().map { it.id }.distinct()
        val seriesIds = references.filterIsInstance<Reference.Series>().map { it.id }.distinct()
        val instances = if (instanceIds.isEmpty()) emptyMap() else eventInstanceRepository.getAll(instanceIds).associateBy { it.id }
        val series = if (seriesIds.isEmpty()) emptyMap() else eventSeriesRepository.getAll(seriesIds).associateBy { it.id }
        return references.distinct().mapNotNull { reference ->
            val target = when (reference) {
                is Reference.Instance -> instances[reference.id]?.let { ReservationTarget.Instance(it) }
                is Reference.Series -> series[reference.id]?.let { ReservationTarget.Series(it) }
                is Reference.Slot -> slotBookingProvider?.targetFor(reference)
            }
            target?.let { reference to it }
        }.toMap()
    }
}
