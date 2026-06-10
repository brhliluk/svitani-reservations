package cz.svitaninymburk.projects.reservations.android.feature.reservations.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.error.toMessage
import cz.svitaninymburk.projects.reservations.android.feature.reservations.ui.StatusBadge
import cz.svitaninymburk.projects.reservations.android.feature.reservations.util.formatted
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReservationListScreen(onNavigateToDetail: (MyReservationListItem) -> Unit) {
    val vm: ReservationsViewModel = koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    ReservationListContent(
        state = state,
        onReservationClick = onNavigateToDetail,
        onRetry = vm::load,
        onRefresh = vm::refresh,
    )
}

@Composable
fun ReservationListContent(
    state: ReservationsUiState,
    onReservationClick: (MyReservationListItem) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    val context = LocalContext.current
    PullToRefreshBox(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingState()
            state.error != null && state.items.isEmpty() -> ErrorState(message = state.error.toMessage(context), onRetry = onRetry)
            state.items.isEmpty() -> EmptyState()
            else -> ReservationList(items = state.items, onReservationClick = onReservationClick)
        }
    }
}

@Composable
private fun LoadingState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.reservations_retry))
        }
    }
}

@Composable
private fun EmptyState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
        stringResource(R.string.reservations_empty),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ReservationList(
    items: List<MyReservationListItem>,
    onReservationClick: (MyReservationListItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id.toString() }) { item ->
            ReservationCard(item = item, onClick = { onReservationClick(item) })
        }
    }
}

@Composable
private fun ReservationCard(item: MyReservationListItem, onClick: () -> Unit) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.eventTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(item.status)
            }
            Text(item.startDateTime.formatted(), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.reservation_seat_count, item.seatCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(item.totalPrice.toCzkString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
