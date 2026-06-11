package cz.svitaninymburk.projects.reservations.android.feature.events

import androidx.navigation3.runtime.NavKey
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem

data object EventList : NavKey
data class EventInstanceDetail(val id: String) : NavKey
data class EventSeriesDetail(val id: String) : NavKey
data class EventReservationForm(val id: String, val isSeries: Boolean) : NavKey
data class EventReservationCreated(val item: MyReservationListItem) : NavKey
