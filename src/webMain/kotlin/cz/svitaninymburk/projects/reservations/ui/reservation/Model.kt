package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo



data class ReservationFormData(
    val name: String,
    val surname: String,
    val email: String,
    val phone: String,
    val seats: Int,
    val paymentType: PaymentInfo.Type,
    val customValues: Map<String, CustomFieldValue>
)