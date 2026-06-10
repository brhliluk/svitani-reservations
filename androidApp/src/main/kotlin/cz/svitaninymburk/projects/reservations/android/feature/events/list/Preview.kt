package cz.svitaninymburk.projects.reservations.android.feature.events.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private val previewInstance = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = "Jóga pro začátečníky",
    description = "Lekce jógy pro úplné začátečníky.",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    endDateTime = LocalDateTime(2026, 7, 15, 10, 0),
    price = 200.0,
    capacity = 10,
    occupiedSpots = 3,
)

private val previewInstanceFull = previewInstance.copy(
    id = Uuid.random(),
    title = "Pilates",
    occupiedSpots = 10,
)

private val previewInstanceCancelled = previewInstance.copy(
    id = Uuid.random(),
    title = "Dětská jóga",
    isCancelled = true,
)

private val previewSeries = EventSeries(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = "Kurz keramiky",
    description = "Desetitýdenní kurz keramiky.",
    price = 1800.0,
    capacity = 12,
    occupiedSpots = 5,
    startDate = LocalDate(2026, 9, 1),
    endDate = LocalDate(2026, 12, 15),
    lessonCount = 10,
)

private class EventsUiStateProvider : PreviewParameterProvider<EventsUiState> {
    override val values = sequenceOf(
        EventsUiState(isLoading = true),
        EventsUiState(error = RepositoryError.Network),
        EventsUiState(oneOff = listOf(previewInstance, previewInstanceFull, previewInstanceCancelled)),
        EventsUiState(courses = listOf(previewSeries), selectedTab = EventsTab.COURSES),
        EventsUiState(selectedTab = EventsTab.COURSES),
    )
}

@PreviewLightDark
@Composable
private fun EventListContentPreview(
    @PreviewParameter(EventsUiStateProvider::class) state: EventsUiState,
) {
    SvitaniTheme {
        EventListContent(
            state = state,
            onTabSelected = {},
            onInstanceClick = {},
            onSeriesClick = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}
