package cz.svitaninymburk.projects.reservations.android.feature.events.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.error.toMessage
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.CancelledBadge
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.DropInBadge
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.ErrorState
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.FullBadge
import cz.svitaninymburk.projects.reservations.android.feature.events.ui.LoadingState
import cz.svitaninymburk.projects.reservations.android.feature.events.util.displayName
import cz.svitaninymburk.projects.reservations.android.feature.events.util.formatted
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EventListScreen(
    onNavigateToInstance: (EventInstance) -> Unit,
    onNavigateToSeries: (EventSeries) -> Unit,
) {
    val vm: EventsViewModel = koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    EventListContent(
        state = state,
        onTabSelected = vm::selectTab,
        onInstanceClick = onNavigateToInstance,
        onSeriesClick = onNavigateToSeries,
        onRetry = vm::load,
        onRefresh = vm::refresh,
    )
}

@Composable
fun EventListContent(
    state: EventsUiState,
    onTabSelected: (EventsTab) -> Unit,
    onInstanceClick: (EventInstance) -> Unit,
    onSeriesClick: (EventSeries) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == EventsTab.ONE_OFF,
                onClick = { onTabSelected(EventsTab.ONE_OFF) },
                text = { Text(stringResource(R.string.events_tab_one_off)) },
            )
            Tab(
                selected = state.selectedTab == EventsTab.COURSES,
                onClick = { onTabSelected(EventsTab.COURSES) },
                text = { Text(stringResource(R.string.events_tab_courses)) },
            )
        }
        val hasContent = state.oneOff.isNotEmpty() || state.courses.isNotEmpty()
        PullToRefreshBox(
            isRefreshing = state.isLoading && hasContent,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && !hasContent -> LoadingState()
                state.error != null && !hasContent -> ErrorState(message = state.error.toMessage(context), onRetry = onRetry)
                else -> when (state.selectedTab) {
                    EventsTab.ONE_OFF ->
                        if (state.oneOff.isEmpty()) EmptyState(stringResource(R.string.events_empty_one_off))
                        else InstanceList(state.oneOff, onInstanceClick)
                    EventsTab.COURSES ->
                        if (state.courses.isEmpty()) EmptyState(stringResource(R.string.events_empty_courses))
                        else SeriesList(state.courses, onSeriesClick)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun InstanceList(items: List<EventInstance>, onClick: (EventInstance) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id.toString() }) { item ->
            InstanceCard(item = item, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun InstanceCard(item: EventInstance, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isCancelled) 0.6f else 1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (item.isCancelled) CancelledBadge()
                else if (item.isFull) FullBadge()
                if (item.isDropIn) DropInBadge()
            }
            Text(item.startDateTime.formatted(), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(item.price.toCzkString(), style = MaterialTheme.typography.bodySmall)
                if (item.showAttendeeCount) {
                    Text(
                        stringResource(R.string.event_occupancy, item.occupiedSpots, item.capacity),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesList(items: List<EventSeries>, onClick: (EventSeries) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id.toString() }) { item ->
            SeriesCard(item = item, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun SeriesCard(item: EventSeries, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (item.isFull) FullBadge()
            }
            Text(
                stringResource(R.string.event_detail_date_range, item.startDate.formatted(), item.endDate.formatted()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (item.lessonDayOfWeek != null && item.lessonStartTime != null && item.lessonEndTime != null) {
                Text(
                    "${item.lessonDayOfWeek!!.displayName()} ${item.lessonStartTime!!.formatted()}–${item.lessonEndTime!!.formatted()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.event_lesson_count, item.lessonCount), style = MaterialTheme.typography.bodySmall)
                Text(item.price.toCzkString(), style = MaterialTheme.typography.bodySmall)
                if (item.showAttendeeCount) {
                    Text(
                        stringResource(R.string.event_occupancy, item.occupiedSpots, item.capacity),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
