package cz.svitaninymburk.projects.reservations.admin

import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AdminEventDetailData(
    val eventId: Uuid,
    val title: String,
    val subtitle: String,
    val capacity: Int,
    val occupiedSpots: Int,
    val totalCollected: Double,
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
)
