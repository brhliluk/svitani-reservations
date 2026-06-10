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
import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InstanceDetailScreen(
    id: String,
    onNavigateBack: () -> Unit,
) {
    val vm: InstanceDetailViewModel = koinViewModel(
        key = id,
        parameters = { parametersOf(Uuid.parse(id)) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    InstanceDetailContent(state = state, onBack = onNavigateBack, onRetry = vm::load)
}

@Composable
fun InstanceDetailContent(
    state: InstanceDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.instance?.title ?: "") },
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
            state.instance != null -> InstanceDetailBody(
                instance = state.instance,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun InstanceDetailBody(instance: EventInstance, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (instance.isCancelled) CancelledBadge()
            else if (instance.isFull) FullBadge()
            if (instance.isDropIn) DropInBadge()
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow(stringResource(R.string.event_detail_date), "${instance.startDateTime.formatted()} – ${instance.endDateTime.time.formatted()}")
                DetailRow(stringResource(R.string.event_detail_price), instance.price.toCzkString())
                if (instance.showAttendeeCount) {
                    DetailRow(
                        stringResource(R.string.event_detail_capacity),
                        stringResource(R.string.event_occupancy, instance.occupiedSpots, instance.capacity),
                    )
                }
            }
        }
        if (instance.description.isNotBlank()) {
            Text(instance.description, style = MaterialTheme.typography.bodyMedium)
        }
        if (instance.isDeadlinePassed) {
            Text(
                instance.reservationDeadlineMessage ?: stringResource(R.string.event_detail_deadline_passed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.event_detail_reserve_coming_soon))
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
