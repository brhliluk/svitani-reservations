package cz.svitaninymburk.projects.reservations.android.feature.reservations.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReservationsUiState(
    val isLoading: Boolean = false,
    val items: List<MyReservationListItem> = emptyList(),
    val error: RepositoryError? = null,
)

class ReservationsViewModel(
    private val repository: ReservationsRepository,
) : ViewModel() {

    val uiState: StateFlow<ReservationsUiState>
        field = MutableStateFlow(ReservationsUiState())

    init {
        load()
    }

    fun load() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getMyReservations()
                .onLeft { error -> uiState.update { it.copy(isLoading = false, error = error) } }
                .onRight { items -> uiState.update { it.copy(isLoading = false, items = items) } }
        }
    }

    fun refresh() = load()
}
