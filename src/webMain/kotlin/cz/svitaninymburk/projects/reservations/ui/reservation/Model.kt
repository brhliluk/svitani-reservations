package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

sealed interface ReservationTarget {
    val id: String
    val title: String
    val price: Double
    val maxCapacity: Int
    val customFields: List<CustomFieldDefinition>
    val startDateTime: LocalDateTime
    val endDateTime: LocalDateTime

    data class Instance(val event: EventInstance) : ReservationTarget {
        override val id = event.id
        override val title = event.title
        override val price = event.price
        override val maxCapacity = event.capacity - event.occupiedSpots
        override val startDateTime = event.startDateTime
        override val endDateTime = event.endDateTime
        override val customFields = event.customFields
    }

    data class Series(val series: EventSeries) : ReservationTarget {
        override val id = series.id
        override val title = series.title
        override val price = series.price
        override val maxCapacity = series.capacity - series.occupiedSpots
        override val startDateTime = LocalDateTime(date = series.startDate, time = LocalTime(0,0))
        override val endDateTime = LocalDateTime(date = series.endDate, time = LocalTime(24,0))
        override val customFields = series.customFields
    }
}

data class ReservationFormData(
    val name: String,
    val surname: String,
    val email: String,
    val phone: String,
    val seats: Int,
    val paymentType: PaymentInfo.Type,
    val customValues: Map<String, CustomFieldValue>
)