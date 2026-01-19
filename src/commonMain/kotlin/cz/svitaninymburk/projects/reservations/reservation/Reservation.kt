package cz.svitaninymburk.projects.reservations.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant


@Serializable
data class Reservation(
    val id: String,
    val reference: Reference,
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
sealed interface Reference {
    val id: String
    @Serializable @SerialName("instance")
    data class Instance(override val id: String): Reference
    @Serializable @SerialName("series")
    data class Series(override val id: String): Reference
}

@Serializable
data class CreateInstanceReservationRequest(
    val eventInstanceId: String,
    val seatCount: Int = 1,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String,
    val paymentType: PaymentInfo.Type,
    val customValues: List<CustomFieldValue>,
)

@Serializable
data class CreateSeriesReservationRequest(
    val eventSeriesId: String,
    val seatCount: Int = 1,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String,
    val paymentType: PaymentInfo.Type,
    val customValues: List<CustomFieldValue>,
)