package cz.svitaninymburk.projects.reservations.android.feature.login

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.auth.UserDto
import cz.svitaninymburk.projects.reservations.user.User
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty and not loading`() {
        val vm = LoginViewModel(FakeAuthRepository())
        val state = vm.uiState.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.loginSuccess)
    }

    @Test
    fun `onEmailChange updates email in state`() {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onEmailChange("jana@svitani.cz")
        assertEquals("jana@svitani.cz", vm.uiState.value.email)
    }

    @Test
    fun `login success sets loginSuccess true`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository(loginResult = Either.Right(fakeAuthResponse())))
        vm.onEmailChange("jana@svitani.cz")
        vm.onPasswordChange("heslo123")
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loginSuccess)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `login failure sets error`() = runTest {
        val error = RepositoryError.Server("InvalidCredentials", "Neplatné přihlašovací údaje")
        val vm = LoginViewModel(FakeAuthRepository(loginResult = Either.Left(error)))
        vm.onEmailChange("x@x.cz")
        vm.onPasswordChange("spatne")
        vm.login()
        advanceUntilIdle()
        assertEquals(error, vm.uiState.value.error)
        assertFalse(vm.uiState.value.loginSuccess)
    }
}

private class FakeAuthRepository(
    private val loginResult: Either<RepositoryError, AuthResponse> = Either.Right(fakeAuthResponse()),
) : AuthRepository {
    override suspend fun login(email: String, password: String) = loginResult
    override fun hasToken() = false
    override fun clearTokens() {}
    override fun getUser() = null
}

private fun fakeAuthResponse() = AuthResponse(
    accessToken = "access",
    refreshToken = "refresh",
    user = UserDto(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        email = "jana@svitani.cz",
        fullName = "Jana Nováková",
        role = User.Role.USER,
        walletCode = null,
    ),
)
