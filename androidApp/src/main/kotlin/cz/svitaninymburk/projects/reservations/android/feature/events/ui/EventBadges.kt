package cz.svitaninymburk.projects.reservations.android.feature.events.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.svitaninymburk.projects.reservations.android.R

@Composable
private fun EventBadge(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
internal fun CancelledBadge() = EventBadge(
    text = stringResource(R.string.event_badge_cancelled),
    container = MaterialTheme.colorScheme.errorContainer,
    content = MaterialTheme.colorScheme.onErrorContainer,
)

@Composable
internal fun FullBadge() = EventBadge(
    text = stringResource(R.string.event_badge_full),
    container = MaterialTheme.colorScheme.surfaceVariant,
    content = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun DropInBadge() = EventBadge(
    text = stringResource(R.string.event_badge_drop_in),
    container = MaterialTheme.colorScheme.secondaryContainer,
    content = MaterialTheme.colorScheme.onSecondaryContainer,
)
