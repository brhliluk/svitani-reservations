package cz.svitaninymburk.projects.reservations.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid


@Serializable
data class Reservation(
    val id: Uuid,
    val reference: Reference,
    val registeredUserId: Uuid? = null,

    val contactName: String,
    val contactEmail: String,
    val contactPhone: String? = null,
    val userId: Uuid? = null,

    val seatCount: Int = 1,
    val totalPrice: Double,
    val paidAmount: Double = 0.0,

    val status: Status,
    val createdAt: Instant,

    val customValues: Map<String, CustomFieldValue>,

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
    val id: Uuid
    @Serializable @SerialName("instance")
    data class Instance(override val id: Uuid): Reference
    @Serializable @SerialName("series")
    data class Series(override val id: Uuid): Reference
}

@Serializable
sealed interface ReservationTarget {
    val id: Uuid
    val title: String
    val price: Double
    val allowedPaymentTypes: List<PaymentInfo.Type>
    val maxCapacity: Int
    val customFields: List<CustomFieldDefinition>
    val startDateTime: LocalDateTime
    val endDateTime: LocalDateTime

    @Serializable
    @SerialName("instance")
    data class Instance(val event: EventInstance) : ReservationTarget {
        override val id = event.id
        override val title = event.title
        override val price = event.price
        override val allowedPaymentTypes = event.allowedPaymentTypes
        override val maxCapacity = event.capacity - event.occupiedSpots
        override val startDateTime = event.startDateTime
        override val endDateTime = event.endDateTime
        override val customFields = event.customFields
    }

    @Serializable
    @SerialName("series")
    data class Series(val series: EventSeries) : ReservationTarget {
        override val id = series.id
        override val title = series.title
        override val price = series.price
        override val allowedPaymentTypes = series.allowedPaymentTypes
        override val maxCapacity = series.capacity - series.occupiedSpots
        override val startDateTime = LocalDateTime(date = series.startDate, time = LocalTime(0,0))
        override val endDateTime = LocalDateTime(date = series.endDate, time = LocalTime(24,0))
        override val customFields = series.customFields
    }
}

interface ReservationRequestData {
    val seatCount: Int
    val contactName: String
    val contactEmail: String
    val contactPhone: String
    val paymentType: PaymentInfo.Type
    val customValues: Map<String, CustomFieldValue>
}

@Serializable
data class CreateInstanceReservationRequest(
    val eventInstanceId: String,
    override val seatCount: Int = 1,
    override val contactName: String,
    override val contactEmail: String,
    override val contactPhone: String,
    override val paymentType: PaymentInfo.Type,
    override val customValues: Map<String, CustomFieldValue>,
) : ReservationRequestData

@Serializable
data class CreateSeriesReservationRequest(
    val eventSeriesId: String,
    override val seatCount: Int = 1,
    override val contactName: String,
    override val contactEmail: String,
    override val contactPhone: String,
    override val paymentType: PaymentInfo.Type,
    override val customValues: Map<String, CustomFieldValue>,
) : ReservationRequestData

@Serializable
data class ReservationDetail(
    val reservation: Reservation,
    val target: ReservationTarget,
)