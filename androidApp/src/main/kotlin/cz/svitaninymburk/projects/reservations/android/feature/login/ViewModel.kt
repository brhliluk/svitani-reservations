package cz.svitaninymburk.projects.reservations.android.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.svitaninymburk.projects.reservations.android.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val uiState: StateFlow<LoginUiState>
        field = MutableStateFlow(LoginUiState())

    fun onEmailChange(email: String) {
        uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        uiState.update { it.copy(password = password, error = null) }
    }

    fun consumeLoginSuccess() {
        uiState.update { it.copy(loginSuccess = false, email = "", password = "", error = null) }
    }

    fun login() {
        uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.login(uiState.value.email, uiState.value.password)
                .onLeft { error ->
                    uiState.update { it.copy(isLoading = false, error = error.message) }
                }
                .onRight { _ ->
                    uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
        }
    }
}
