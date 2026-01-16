package cz.svitaninymburk.projects.reservations.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
data class Reservation(
    val id: String,
    val eventInstanceId: String,
    val registeredUserId: String? = null,

    val contactName: String,
    val contactEmail: String,
    val contactPhone: String? = null,
    val userId: String? = null,

    val seatCount: Int = 1,
    val totalPrice: Double,
    val paidAmount: Double = 0.0,

    val status: Status,
    val createdAt: Instant,

    val customValues: List<CustomFieldValue>,

    val paymentType: PaymentInfo.Type,
    val variableSymbol: String? = null, // VS pro párování platby
    val paymentPairingToken: String? = null // Interní ID pro bankovní API
) {
    val unpaidAmount: Double get() = totalPrice - paidAmount
    @Serializable
    enum class Status {
        PENDING_PAYMENT,
        CONFIRMED,
        CANCELLED,
        REJECTED,
    }
}

@Serializable
data class CreateReservationRequest(
    val eventInstanceId: String,
    val seatCount: Int,

    val contactName: String,
    val contactEmail: String,
    val contactPhone: String? = null,
    val userId: String? = null,

    val paymentType: PaymentInfo.Type,
    val customValues: List<CustomFieldValue>,
)

@Serializable
data class GetReservationsRequest(
    val userId: String,
)
