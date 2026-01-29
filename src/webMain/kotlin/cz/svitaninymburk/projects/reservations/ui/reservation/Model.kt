package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo



data class ReservationFormData(
    val name: String,
    val surname: String,
    val email: String,
    val phone: String,
    val seats: Int,
    val paymentType: PaymentInfo.Type,
    val customValues: Map<String, CustomFieldValue>
) {
    fun toCreateInstanceReservationRequest(id: String): CreateInstanceReservationRequest = CreateInstanceReservationRequest(
        eventInstanceId = id,
        seatCount = seats,
        contactName = "$name $surname",
        contactEmail = email,
        contactPhone = phone,
        paymentType = paymentType,
        customValues = customValues,
    )

    fun toCreateSeriesReservationRequest(id: String): CreateSeriesReservationRequest = CreateSeriesReservationRequest(
        eventSeriesId = id,
        seatCount = seats,
        contactName = "$name $surname",
        contactEmail = email,
        contactPhone = phone,
        paymentType = paymentType,
        customValues = customValues,
    )
}