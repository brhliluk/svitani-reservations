package cz.svitaninymburk.projects.reservations.android.feature.reservations

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.svitaninymburk.projects.reservations.android.feature.reservations.detail.ReservationDetailScreen
import cz.svitaninymburk.projects.reservations.android.feature.reservations.list.ReservationListScreen

@Composable
fun MyReservationsScreen() {
    val backStack = rememberNavBackStack(ReservationList)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ReservationList> {
                ReservationListScreen(
                    onNavigateToDetail = { item -> backStack.add(ReservationDetail(item)) }
                )
            }
            entry<ReservationDetail> {
                ReservationDetailScreen(
                    item = it.item,
                    onNavigateBack = { backStack.removeLastOrNull() },
                )
            }
        }
    )
}
