package cz.svitaninymburk.projects.reservations.android.feature.events.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.api.SeriesDetailResponse
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val detail: SeriesDetailResponse? = null,
    val error: RepositoryError? = null,
)

class SeriesDetailViewModel(
    private val id: Uuid,
    private val repository: EventsRepository,
) : ViewModel() {

    val uiState: StateFlow<SeriesDetailUiState>
        field = MutableStateFlow(SeriesDetailUiState())

    init {
        load()
    }

    fun load() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getSeriesDetail(id)
                .onLeft { error -> uiState.update { it.copy(isLoading = false, error = error) } }
                .onRight { detail -> uiState.update { it.copy(isLoading = false, detail = detail) } }
        }
    }

    fun refresh() = load()
}
