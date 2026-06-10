package cz.svitaninymburk.projects.reservations.android.feature.events.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.event.EventSeries
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class EventsTab { ONE_OFF, COURSES }

data class EventsUiState(
    val isLoading: Boolean = false,
    val oneOff: List<EventInstance> = emptyList(),
    val courses: List<EventSeries> = emptyList(),
    val error: RepositoryError? = null,
    val selectedTab: EventsTab = EventsTab.ONE_OFF,
)

class EventsViewModel(
    private val repository: EventsRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    val uiState: StateFlow<EventsUiState>
        field = MutableStateFlow(EventsUiState())

    init {
        load()
    }

    fun load() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getEvents()
                .onLeft { error -> uiState.update { it.copy(isLoading = false, error = error) } }
                .onRight { response ->
                    val now = clock.now().toLocalDateTime(TimeZone.of("Europe/Prague"))
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            oneOff = response.instances
                                .filter { i -> i.seriesId == null || i.isDropIn }
                                .filter { i -> i.startDateTime >= now }
                                .sortedBy { i -> i.startDateTime },
                            courses = response.series
                                .filter { s -> s.endDate >= now.date }
                                .sortedBy { s -> s.startDate },
                        )
                    }
                }
        }
    }

    fun refresh() = load()

    fun selectTab(tab: EventsTab) {
        uiState.update { it.copy(selectedTab = tab) }
    }
}
