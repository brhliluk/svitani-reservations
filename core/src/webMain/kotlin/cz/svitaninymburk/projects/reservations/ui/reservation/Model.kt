package cz.svitaninymburk.projects.reservations.ui.reservation

import cz.svitaninymburk.projects.reservations.event.CustomFieldValue
import cz.svitaninymburk.projects.reservations.reservation.CreateInstanceReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.CreateSeriesReservationRequest
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import kotlin.uuid.Uuid


data class ReservationFormData(
    val name: String,
    val surname: String,
    val email: String,
    val phone: String,
    val seats: Int,
    val paymentType: PaymentType,
    val customValues: Map<String, CustomFieldValue>,
    val locale: String = "cs",
    val walletCode: String? = null,
    val asWaitlist: Boolean = false,
) {
    /** [acknowledgedDuplicate] je `true` až u druhého pokusu, kdy uživatel odklikl varování o duplicitě. */
    fun toCreateInstanceReservationRequest(id: Uuid, acknowledgedDuplicate: Boolean = false): CreateInstanceReservationRequest = CreateInstanceReservationRequest(
        eventInstanceId = id,
        seatCount = seats,
        contactName = "$name $surname",
        contactEmail = email,
        contactPhone = phone,
        paymentType = paymentType,
        customValues = customValues,
        locale = locale,
        walletCode = walletCode,
        acknowledgedDuplicate = acknowledgedDuplicate,
    )

    fun toCreateSeriesReservationRequest(id: Uuid, acknowledgedDuplicate: Boolean = false): CreateSeriesReservationRequest = CreateSeriesReservationRequest(
        eventSeriesId = id,
        seatCount = seats,
        contactName = "$name $surname",
        contactEmail = email,
        contactPhone = phone,
        paymentType = paymentType,
        customValues = customValues,
        locale = locale,
        walletCode = walletCode,
        acknowledgedDuplicate = acknowledgedDuplicate,
    )
}