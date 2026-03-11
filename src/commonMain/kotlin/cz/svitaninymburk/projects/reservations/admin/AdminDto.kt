package cz.svitaninymburk.projects.reservations.admin

import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AdminDashboardData(
    val todayParticipantsCount: Int,
    val pendingPaymentsTotal: Double,
    val pendingPaymentsCount: Int,
    val freeSpotsThisWeek: Int,
    val upcomingEvents: List<AdminUpcomingEvent>,
    val pendingReservations: List<AdminPendingReservation>,
)

@Serializable
data class AdminUpcomingEvent(
    val id: Uuid,
    val title: String,
    val startDateTime: LocalDateTime,
    val occupiedSpots: Int,
    val capacity: Int,
)

@Serializable
data class AdminPendingReservation(
    val id: Uuid,
    val contactName: String,
    val eventName: String,
    val totalPrice: Double,
    val variableSymbol: String?,
)

@Serializable
data class AdminReservationListItem(
    val id: Uuid,
    val contactName: String,
    val contactEmail: String,
    val eventTitle: String,
    val eventDate: String,
    val seatCount: Int,
    val totalPrice: Double,
    val variableSymbol: String?,
    val status: Reservation.Status,
    val paymentType: PaymentInfo.Type,
    val createdAt: Instant,
)

@Serializable
data class AdminEventListItem(
    val id: Uuid,
    val title: String,
    val isSeries: Boolean,
    val dateInfo: String,
    val capacity: Int,
    val occupiedSpots: Int,
    val priceString: String
)
