package cz.svitaninymburk.projects.reservations.android.feature.reservations

import androidx.navigation3.runtime.NavKey
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import kotlinx.serialization.Serializable

@Serializable data object ReservationList : NavKey
@Serializable data class ReservationDetail(val item: MyReservationListItem) : NavKey
