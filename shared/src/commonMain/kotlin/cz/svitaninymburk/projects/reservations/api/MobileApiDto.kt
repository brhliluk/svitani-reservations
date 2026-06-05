package cz.svitaninymburk.projects.reservations.api

import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlinx.serialization.Serializable

@Serializable
data class RefreshResponse(val accessToken: String)

@Serializable
data class EventsResponse(
    val instances: List<EventInstance>,
    val series: List<EventSeries>,
)

@Serializable
data class MobilePaymentInfo(
    val spayd: String,
    val amount: Double,
    val variableSymbol: String?,
    val iban: String,
    val accountNumber: String,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)
