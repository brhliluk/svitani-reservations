package cz.svitaninymburk.projects.reservations.android.feature.profile

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.repository.auth.AuthRepository
import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.auth.AuthResponse
import cz.svitaninymburk.projects.reservations.wallet.WalletInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
    fun `initial state is loading`() {
        val vm = ProfileViewModel(FakeAuthRepository(), FakeWalletRepository())
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.wallet)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `loads wallet successfully on init`() = runTest {
        val info = fakeWalletInfo()
        val vm = ProfileViewModel(FakeAuthRepository(), FakeWalletRepository(result = Either.Right(info)))
        advanceUntilIdle()
        assertEquals(info, vm.uiState.value.wallet)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `sets error state on wallet load failure`() = runTest {
        val error = RepositoryError.Network
        val vm = ProfileViewModel(FakeAuthRepository(), FakeWalletRepository(result = Either.Left(error)))
        advanceUntilIdle()
        assertEquals(error, vm.uiState.value.error)
        assertNull(vm.uiState.value.wallet)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `logout clears auth tokens`() {
        val authRepo = FakeAuthRepository()
        val vm = ProfileViewModel(authRepo, FakeWalletRepository())
        vm.logout()
        assertTrue(authRepo.tokensCleared)
    }

    @Test
    fun `loadWallet resets error and reloads`() = runTest {
        val repo = FakeWalletRepository(result = Either.Left(RepositoryError.Network))
        val vm = ProfileViewModel(FakeAuthRepository(), repo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        val info = fakeWalletInfo()
        repo.result = Either.Right(info)
        vm.loadWallet()
        advanceUntilIdle()
        assertEquals(info, vm.uiState.value.wallet)
        assertNull(vm.uiState.value.error)
    }
}

private class FakeAuthRepository : AuthRepository {
    var tokensCleared = false
    override suspend fun login(email: String, password: String): Either<RepositoryError, AuthResponse> =
        throw UnsupportedOperationException()
    override fun hasToken() = true
    override fun clearTokens() { tokensCleared = true }
}

private class FakeWalletRepository(
    var result: Either<RepositoryError, WalletInfo> = Either.Right(fakeWalletInfo()),
) : WalletRepository {
    override suspend fun getMyWallet() = result
}

private fun fakeWalletInfo() = WalletInfo(
    code = "SVIT-TEST-1234",
    balance = 250.0,
    emailMatches = true,
    seasonResetDay = 31,
    seasonResetMonth = 8,
)
