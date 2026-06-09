package cz.svitaninymburk.projects.reservations.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserDto? = null,
    val wallet: WalletInfo? = null,
    val isLoading: Boolean = true,
    val error: RepositoryError? = null,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState>
        field = MutableStateFlow(ProfileUiState(user = authRepository.getUser()))

    init { loadWallet() }

    fun loadWallet() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            walletRepository.getMyWallet()
                .onRight { wallet -> uiState.update { it.copy(wallet = wallet, isLoading = false) } }
                .onLeft { error -> uiState.update { it.copy(error = error, isLoading = false) } }
        }
    }

    fun logout() {
        authRepository.clearTokens()
    }
}
