package cz.svitaninymburk.projects.reservations.service

import cz.svitaninymburk.projects.reservations.error.DuplicateScope
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.repository.event.EventInstanceRepository
import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import kotlin.uuid.Uuid

/**
 * Hlídá, že se člověk nepřihlásí na tutéž akci dvakrát omylem.
 *
 * Rezervace nejsou vázané na účet — host vyplní jen jméno, e-mail a telefon — takže
 * jediné, podle čeho se dá totožnost poznat, je [contactEmail][cz.svitaninymburk.projects.reservations.reservation.Reservation.contactEmail].
 *
 * Kurz a jeho lekce se kontrolují proti sobě: kdo je přihlášený na celý kurz,
 * je tím pádem přihlášený i na každou jeho lekci, i když na ni samostatnou
 * rezervaci nemá (obsazenost lekcí se u kurzů dopočítává, nezapisuje).
 *
 * Výsledek je vždycky jen varování — volající ho po potvrzení uživatelem ignoruje.
 */
class DuplicateReservationDetector(
    private val reservationRepository: ReservationRepository,
    private val eventInstanceRepository: EventInstanceRepository,
) {

    /** Rezervace na jednotlivou lekci nebo samostatnou akci. */
    suspend fun forInstance(instance: EventInstance, email: String): DuplicateScope? {
        val normalized = normalize(email) ?: return null
        val seriesId = instance.seriesId
        // Lekce kurzu se tluče i s přihláškou na celý kurz, proto se ptáme na obojí naráz.
        val existing = reservationRepository.findActiveByReferenceIdsAndEmail(
            referenceIds = listOfNotNull(instance.id, seriesId),
            email = normalized,
        )
        return when {
            existing.any { it.reference == Reference.Instance(instance.id) } -> DuplicateScope.SAME_EVENT
            seriesId != null &&
                existing.any { it.reference == Reference.Series(seriesId) } -> DuplicateScope.PARENT_SERIES
            else -> null
        }
    }

    /** Rezervace na celý kurz. */
    suspend fun forSeries(seriesId: Uuid, email: String): DuplicateScope? {
        val normalized = normalize(email) ?: return null
        val lessonIds = eventInstanceRepository.findBySeries(seriesId).map { it.id }
        val existing = reservationRepository.findActiveByReferenceIdsAndEmail(
            referenceIds = listOf(seriesId) + lessonIds,
            email = normalized,
        )
        return when {
            existing.any { it.reference == Reference.Series(seriesId) } -> DuplicateScope.SAME_EVENT
            existing.any { it.reference is Reference.Instance } -> DuplicateScope.SERIES_LESSON
            else -> null
        }
    }

    /** Prázdný e-mail nemá s čím kolidovat — formulář ho stejně nepustí dál. */
    private fun normalize(email: String): String? = email.trim().lowercase().ifBlank { null }
}
