package cz.svitaninymburk.projects.reservations.android.feature.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import cz.svitaninymburk.projects.reservations.android.ui.theme.SvitaniTheme
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private val previewInstance = EventInstance(
    id = Uuid.random(),
    definitionId = Uuid.random(),
    title = "Jóga pro začátečníky",
    description = "Lekce jógy pro úplné začátečníky. Podložky zajištěny.",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    endDateTime = LocalDateTime(2026, 7, 15, 10, 0),
    price = 200.0,
    capacity = 10,
    occupiedSpots = 3,
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

private val previewLessons = listOf(
    previewInstance.copy(id = Uuid.random(), seriesId = previewSeries.id),
    previewInstance.copy(id = Uuid.random(), seriesId = previewSeries.id, isDropIn = true, startDateTime = LocalDateTime(2026, 7, 22, 9, 0), endDateTime = LocalDateTime(2026, 7, 22, 10, 0)),
)

@PreviewLightDark
@Composable
private fun InstanceDetailContentPreview() {
    SvitaniTheme {
        InstanceDetailContent(
            state = InstanceDetailUiState(isLoading = false, instance = previewInstance),
            onBack = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SeriesDetailContentPreview() {
    SvitaniTheme {
        SeriesDetailContent(
            state = SeriesDetailUiState(isLoading = false, detail = SeriesDetailResponse(previewSeries, previewLessons)),
            onBack = {},
            onRetry = {},
        )
    }
}
