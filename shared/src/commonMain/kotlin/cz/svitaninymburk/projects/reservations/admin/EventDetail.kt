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
)
