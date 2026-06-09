package cz.svitaninymburk.projects.reservations.android.feature.reservations

import androidx.navigation3.runtime.NavKey
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem

data object ReservationList : NavKey
data class ReservationDetail(val item: MyReservationListItem) : NavKey
