package cz.svitaninymburk.projects.reservations.android.feature.events

import androidx.navigation3.runtime.NavKey
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import kotlinx.serialization.Serializable

@Serializable data object EventList : NavKey
@Serializable data class EventInstanceDetail(val id: String) : NavKey
@Serializable data class EventSeriesDetail(val id: String) : NavKey
@Serializable data class EventReservationForm(val id: String, val isSeries: Boolean) : NavKey
@Serializable data class EventReservationCreated(val item: MyReservationListItem) : NavKey
