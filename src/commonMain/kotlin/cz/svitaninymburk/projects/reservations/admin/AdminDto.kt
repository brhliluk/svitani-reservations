package cz.svitaninymburk.projects.reservations.admin

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDateTime
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
