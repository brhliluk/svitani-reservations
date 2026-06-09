package cz.svitaninymburk.projects.reservations.android.feature.reservations.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReservationDetailUiState(
    val item: MyReservationListItem,
    val paymentInfo: MobilePaymentInfo? = null,
    val isLoadingPayment: Boolean = false,
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false,
    val error: RepositoryError? = null,
)

class ReservationDetailViewModel(
    private val item: MyReservationListItem,
    private val repository: ReservationsRepository,
) : ViewModel() {

    val uiState: StateFlow<ReservationDetailUiState>
        field = MutableStateFlow(ReservationDetailUiState(item = item))

    init {
        loadPaymentInfoIfNeeded()
    }

    private fun loadPaymentInfoIfNeeded() {
        if (item.status != Reservation.Status.PENDING_PAYMENT) return
        if (item.paymentType != PaymentInfo.Type.BANK_TRANSFER) return
        uiState.update { it.copy(isLoadingPayment = true, error = null) }
        viewModelScope.launch {
            repository.getPaymentInfo(item.id)
                .onLeft { error -> uiState.update { it.copy(isLoadingPayment = false, error = error) } }
                .onRight { info -> uiState.update { it.copy(isLoadingPayment = false, paymentInfo = info) } }
        }
    }

    fun cancelReservation() {
        uiState.update { it.copy(isCancelling = true, error = null) }
        viewModelScope.launch {
            repository.cancelReservation(item.id)
                .onLeft { error -> uiState.update { it.copy(isCancelling = false, error = error) } }
                .onRight { uiState.update { it.copy(isCancelling = false, cancelSuccess = true, item = it.item.copy(status = Reservation.Status.CANCELLED)) } }
        }
    }

    fun consumeCancelSuccess() {
        uiState.update { it.copy(cancelSuccess = false, error = null) }
    }
}
