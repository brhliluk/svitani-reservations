package cz.svitaninymburk.projects.reservations.android.feature.reservations

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.android.error.RepositoryError
import cz.svitaninymburk.projects.reservations.android.feature.reservations.list.ReservationsViewModel
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.api.MobilePaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationsViewModelTest {

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
    fun `initial state shows loading and triggers load`() = runTest {
        val repo = FakeReservationsRepository()
        val vm = ReservationsViewModel(repo)
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `load success populates items`() = runTest {
        val items = listOf(mockItem(), mockItem())
        val vm = ReservationsViewModel(
            FakeReservationsRepository(
                myReservationsResult = Either.Right(items)
            )
        )
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.items.size)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `load failure sets error`() = runTest {
        val vm = ReservationsViewModel(
            FakeReservationsRepository(
                myReservationsResult = Either.Left(RepositoryError.Network)
            )
        )
        advanceUntilIdle()
        assertEquals(RepositoryError.Network, vm.uiState.value.error)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun `refresh re-fetches data`() = runTest {
        var callCount = 0
        val repo = object : ReservationsRepository {
            override suspend fun getMyReservations(): Either<RepositoryError, List<MyReservationListItem>> {
                callCount++
                return Either.Right(emptyList())
            }
            override suspend fun getPaymentInfo(id: Uuid) = Either.Right(mockPaymentInfo())
            override suspend fun cancelReservation(id: Uuid) = Either.Right(Unit)
        }
        val vm = ReservationsViewModel(repo)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)
    }
}

private class FakeReservationsRepository(
    private val myReservationsResult: Either<RepositoryError, List<MyReservationListItem>> = Either.Right(emptyList()),
    private val paymentInfoResult: Either<RepositoryError, MobilePaymentInfo> = Either.Right(mockPaymentInfo()),
    private val cancelResult: Either<RepositoryError, Unit> = Either.Right(Unit),
) : ReservationsRepository {
    override suspend fun getMyReservations() = myReservationsResult
    override suspend fun getPaymentInfo(id: Uuid) = paymentInfoResult
    override suspend fun cancelReservation(id: Uuid) = cancelResult
}

private fun mockItem(
    status: Reservation.Status = Reservation.Status.CONFIRMED,
    paymentType: PaymentInfo.Type = PaymentInfo.Type.BANK_TRANSFER,
) = MyReservationListItem(
    id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
    eventTitle = "Jóga pro začátečníky",
    startDateTime = LocalDateTime(2026, 7, 15, 9, 0),
    seatCount = 2,
    totalPrice = 500.0,
    status = status,
    paymentType = paymentType,
    variableSymbol = "1234567890",
    isSeries = false,
)

private fun mockPaymentInfo() = MobilePaymentInfo(
    spayd = "SPD*1.0*ACC:123456789/0800*AM:500.0*CC:CZK*X-VS:1234567890*MSG:Rezervace",
    amount = 500.0,
    variableSymbol = "1234567890",
    iban = "CZ1208000000001234567890",
    accountNumber = "123456789/0800",
)
