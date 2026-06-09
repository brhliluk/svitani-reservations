package cz.svitaninymburk.projects.reservations.android.feature.reservations.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.svitaninymburk.projects.reservations.android.R
import cz.svitaninymburk.projects.reservations.android.ui.theme.statusColors
import cz.svitaninymburk.projects.reservations.reservation.Reservation

@Composable
internal fun StatusBadge(status: Reservation.Status) {
    val colors = MaterialTheme.statusColors
    val (label, container, content) = when (status) {
        Reservation.Status.PENDING_PAYMENT -> Triple(stringResource(R.string.reservation_status_pending_payment), colors.pendingContainer, colors.pendingContent)
        Reservation.Status.CONFIRMED -> Triple(stringResource(R.string.reservation_status_confirmed), colors.confirmedContainer, colors.confirmedContent)
        Reservation.Status.CANCELLED -> Triple(stringResource(R.string.reservation_status_cancelled), colors.neutralContainer, colors.neutralContent)
        Reservation.Status.REJECTED -> Triple(stringResource(R.string.reservation_status_rejected), colors.neutralContainer, colors.neutralContent)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}
