package cz.svitaninymburk.projects.reservations.android.feature.events.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstanceDetailUiState(
    val isLoading: Boolean = true,
    val instance: EventInstance? = null,
    val error: RepositoryError? = null,
)

class InstanceDetailViewModel(
    private val id: Uuid,
    private val repository: EventsRepository,
) : ViewModel() {

    val uiState: StateFlow<InstanceDetailUiState>
        field = MutableStateFlow(InstanceDetailUiState())

    init {
        load()
    }

    fun load() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getInstance(id)
                .onLeft { error -> uiState.update { it.copy(isLoading = false, error = error) } }
                .onRight { instance -> uiState.update { it.copy(isLoading = false, instance = instance) } }
        }
    }

    fun refresh() = load()
}
