package cz.svitaninymburk.projects.reservations.admin

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.reservation.PaymentEvent
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.user.User
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
    val contactPhone: String?,
    val eventTitle: String,
    val eventDate: String,
    val seatCount: Int,
    val totalPrice: Double,
    val variableSymbol: String?,
    val status: Reservation.Status,
    val paymentType: PaymentInfo.Type,
    val walletDeductedAmount: Double = 0.0,
    val createdAt: Instant,
    val customFields: List<CustomFieldDefinition>,
    val customValues: Map<String, CustomFieldValue>,
)

@Serializable
data class AdminEventListItem(
    val id: Uuid,
    val definitionId: Uuid? = null,
    val title: String,
    val isSeries: Boolean,
    val dateInfo: String,
    val capacity: Int,
    val occupiedSpots: Int,
    val priceString: String,
    val isDefinitionOnly: Boolean = false,
    val isPublished: Boolean = true,
    val isCancelled: Boolean = false,
)

@Serializable
data class AdminUserListItem(
    val id: Uuid,
    val name: String,
    val surname: String,
    val email: String,
    val role: User.Role,
    val authType: AuthType,
    val reservationCount: Int,
) {
    @Serializable
    enum class AuthType { EMAIL, GOOGLE }
}

@Serializable
data class PaymentEventsPage(
    val items: List<PaymentEvent>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)

@Serializable
data class ReservationsPage(
    val items: List<AdminReservationListItem>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)

@Serializable
data class EventsPage(
    val items: List<AdminEventListItem>,
    val page: Int,
    val pageSize: Int,
    val totalDefinitionCount: Long,
)

@Serializable
data class SeriesInstancesPage(
    val items: List<EventInstance>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
)
