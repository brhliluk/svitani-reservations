package cz.svitaninymburk.projects.reservations.android.feature.events.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import cz.svitaninymburk.projects.reservations.android.feature.events.util.formatted
import cz.svitaninymburk.projects.reservations.android.util.toCzkString
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SeriesDetailScreen(
    id: String,
    onNavigateBack: () -> Unit,
    onReserve: () -> Unit,
) {
    val vm: SeriesDetailViewModel = koinViewModel(
        key = id,
        parameters = { parametersOf(Uuid.parse(id)) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    SeriesDetailContent(state = state, onBack = onNavigateBack, onRetry = vm::load, onReserve = onReserve)
}

@Composable
fun SeriesDetailContent(
    state: SeriesDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReserve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.series?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            state.error != null -> ErrorState(
                message = state.error.toMessage(context),
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            state.detail != null -> SeriesDetailBody(
                detail = state.detail,
                onReserve = onReserve,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SeriesDetailBody(detail: SeriesDetailResponse, onReserve: () -> Unit, modifier: Modifier = Modifier) {
    val series = detail.series
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (series.isFull) Row { FullBadge() }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow(
                    stringResource(R.string.event_detail_date),
                    stringResource(R.string.event_detail_date_range, series.startDate.formatted(), series.endDate.formatted()),
                )
                DetailRow(stringResource(R.string.event_detail_price), series.price.toCzkString())
                if (series.showAttendeeCount) {
                    DetailRow(
                        stringResource(R.string.event_detail_capacity),
                        stringResource(R.string.event_occupancy, series.occupiedSpots, series.capacity),
                    )
                }
            }
        }
        if (series.description.isNotBlank()) {
            Text(series.description, style = MaterialTheme.typography.bodyMedium)
        }
        if (series.isDeadlinePassed) {
            Text(
                series.reservationDeadlineMessage ?: stringResource(R.string.event_detail_deadline_passed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (detail.lessons.isNotEmpty()) {
            Text(stringResource(R.string.event_detail_lessons), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.lessons.forEach { lesson ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lesson.startDateTime.formatted(), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (lesson.isCancelled) CancelledBadge()
                                if (lesson.isDropIn) DropInBadge()
                            }
                        }
                    }
                }
            }
        }
        val canReserve = !series.isFull && !series.isDeadlinePassed
        Button(
            onClick = onReserve,
            enabled = canReserve,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.event_detail_reserve))
        }
    }
}
