package cz.svitaninymburk.projects.reservations.admin

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AdminEventDetailData(
    val eventId: Uuid,
    val title: String,
    val subtitle: String,
    val capacity: Int,
    val occupiedSpots: Int,
    val totalCollected: Double,
    val customFields: List<CustomFieldDefinition>,
    val participants: List<AdminParticipantRow>,
    val waitlist: List<AdminParticipantRow> = emptyList(),
    val waitlistCapacity: Int = 0,
    val isCancelled: Boolean = false,
    /** U lekce kurzu id té série — kvůli prokliku na kurz u řádků s [AdminParticipantRow.fromSeries]. */
    val seriesId: Uuid? = null,
    /**
     * Účastníci kurzu, kteří se z téhle lekce omluvili. Místo nedrží, takže do
     * [participants] nepatří — admin je ale musí vidět, aby mohl omylem podanou
     * omluvenku vzít zpět.
     */
    val optedOut: List<AdminParticipantRow> = emptyList(),
)

@Serializable
data class AdminParticipantRow(
    val reservationId: Uuid,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String?,
    val seatCount: Int,
    val totalPrice: Double,
    val status: Reservation.Status,
    val paymentType: PaymentInfo.Type,
    val createdAt: Instant,
    val customValues: Map<String, CustomFieldValue>,
    /**
     * true = přihláška na celý kurz, která drží místo i na téhle lekci.
     * Rezervace patří sérii, ne lekci — akce nad ní se dělají na detailu kurzu.
     */
    val fromSeries: Boolean = false,
)
