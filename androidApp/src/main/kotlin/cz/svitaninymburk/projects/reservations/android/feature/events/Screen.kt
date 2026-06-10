package cz.svitaninymburk.projects.reservations.android.feature.events

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.svitaninymburk.projects.reservations.android.feature.events.detail.InstanceDetailScreen
import cz.svitaninymburk.projects.reservations.android.feature.events.detail.SeriesDetailScreen
import cz.svitaninymburk.projects.reservations.android.feature.events.list.EventListScreen

@Composable
fun EventsScreen() {
    val backStack = rememberNavBackStack(EventList)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<EventList> {
                EventListScreen(
                    onNavigateToInstance = { backStack.add(EventInstanceDetail(it.id.toString())) },
                    onNavigateToSeries = { backStack.add(EventSeriesDetail(it.id.toString())) },
                )
            }
            entry<EventInstanceDetail> { key ->
                InstanceDetailScreen(id = key.id, onNavigateBack = { backStack.removeLastOrNull() })
            }
            entry<EventSeriesDetail> { key ->
                SeriesDetailScreen(id = key.id, onNavigateBack = { backStack.removeLastOrNull() })
            }
        }
    )
}
